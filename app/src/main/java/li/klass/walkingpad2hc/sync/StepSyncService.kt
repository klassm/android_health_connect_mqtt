package li.klass.walkingpad2hc.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import li.klass.walkingpad2hc.MainActivity
import li.klass.walkingpad2hc.R
import li.klass.walkingpad2hc.settings.AppSettings
import li.klass.walkingpad2hc.settings.LastWritten
import li.klass.walkingpad2hc.settings.PendingRecord
import li.klass.walkingpad2hc.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class StepSyncService : LifecycleService() {

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    private val writer by lazy { HealthConnectWriter(applicationContext) }
    private val calculator = StepDeltaCalculator()
    private val gate = NetworkGate(
        hasLocationPermission = {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        },
        isLocationEnabled = { locationServicesEnabled() },
    )

    @Volatile
    private lateinit var settings: AppSettings
    @Volatile
    private var mqttClient: Mqtt3AsyncClient? = null
    @Volatile
    private var lastKnownSsid: String? = null
    @Volatile
    private var destroyed = false
    private var pendingRecord: DeltaResult.Record? = null
    private var connectivityManager: ConnectivityManager? = null
    private val initOnce = AtomicBoolean(false)
    private val retryScheduled = AtomicBoolean(false)
    private var lastGateEvalMillis = 0L
    private val messages = Channel<String>(64, BufferOverflow.DROP_OLDEST)
    private val connectDispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "mqtt-connect") }.asCoroutineDispatcher()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (destroyed) return
            wifiNetwork = network
            lifecycleScope.launch(connectDispatcher) {
                evaluateGate()
                delay(3000)
                evaluateGate()
            }
        }

        override fun onLost(network: Network) {
            if (destroyed) return
            if (wifiNetwork == network) wifiNetwork = null
            lastKnownSsid = null
            lifecycleScope.launch(connectDispatcher) { evaluateGate() }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (destroyed) return
            wifiNetwork = network
            lifecycleScope.launch(connectDispatcher) { evaluateGate() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundInternal()
        Log.i(TAG, "onStartCommand action=${intent?.action} destroyed=$destroyed mqtt=${mqttClient != null}")
        lifecycleScope.launch(connectDispatcher) {
            if (initOnce.compareAndSet(false, true)) {
                settings = try {
                    settingsRepository.settings.first()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    SyncStatusHolder.setError("Settings load failed: ${e.message}")
                    AppSettings()
                }
                pendingRecord = settingsRepository.loadPending()?.let {
                    DeltaResult.Record(it.startTime, it.endTime, it.count, it.endSteps)
                }
                connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager?.registerNetworkCallback(
                    NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                    networkCallback,
                )

                startMessageConsumer()
            }
            if (!destroyed) {
                SyncStatusHolder.setPhase(SyncPhase.WAITING_FOR_NETWORK)
                evaluateGate()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        connectivityManager?.let { unbindProcessNetwork(it) }
        connectivityManager?.unregisterNetworkCallback(networkCallback)
        disconnectMqtt()
        (connectDispatcher.executor as java.util.concurrent.ExecutorService).shutdown()
        SyncStatusHolder.update { it.copy(phase = SyncPhase.OFF, phaseDetail = null) }
    }

    override fun onTimeout(startId: Int) {
        handleTimeout()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleTimeout()
    }

    private fun handleTimeout() {
        Log.w(TAG, "foreground service time limit reached")
        destroyed = true
        connectivityManager?.let { unbindProcessNetwork(it) }
        disconnectMqtt()
        connectDispatcher.executor.let { it as java.util.concurrent.ExecutorService }.shutdown()
        SyncStatusHolder.setPhase(SyncPhase.DISCONNECTED, "sync stopped by Android (foreground time limit)")
        SyncStatusHolder.setError("Android stopped the sync after its foreground time budget — press Start to resume")
        stopSelf()
        super.onDestroy()
    }

    private fun locationServicesEnabled(): Boolean = try {
        val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    } catch (_: Exception) {
        false
    }

    private fun localIpv4(): String? = try {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    } catch (_: Exception) {
        null
    }

    private fun evaluateGate() {
        if (destroyed) return
        val cm = connectivityManager ?: return
        val evalNow = System.currentTimeMillis()
        if (evalNow - lastGateEvalMillis < 1_500) return
        lastGateEvalMillis = evalNow
        val (isWifi, ssid) = currentWifiState(cm)
        val reason = gate.reason(isWifi, ssid, settings.ssid)
        Log.i(TAG, "gate: isWifi=$isWifi ssid=$ssid configured=${settings.ssid} reason=$reason mqtt=${mqttClient != null}")
        if (reason != null) {
            SyncStatusHolder.setPhase(SyncPhase.WAITING_FOR_NETWORK, reason)
            disconnectMqtt()
            unbindProcessNetwork(cm)
            return
        }
        if (mqttClient == null) {
            bindToWifiNetwork(cm)
            connectMqtt()
        }
    }

    @Volatile
    private var wifiNetwork: Network? = null

    private fun bindToWifiNetwork(cm: ConnectivityManager) {
        val network = wifiNetwork ?: return
        val bound = try {
            cm.bindProcessToNetwork(network)
        } catch (e: Exception) {
            Log.w(TAG, "bindProcessToNetwork failed", e)
            false
        }
        Log.i(TAG, "process bound to wifi network=$network bound=$bound localIp=${localIpv4()}")
    }

    private fun unbindProcessNetwork(cm: ConnectivityManager) {
        try {
            cm.bindProcessToNetwork(null)
        } catch (_: Exception) {
        }
    }

    private fun probeTcp() {
        Thread({
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(settings.host, settings.port), 5000)
                    Log.i(TAG, "probe: raw TCP OK to ${settings.host}:${settings.port} localAddr=${socket.localAddress}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "probe: raw TCP FAILED to ${settings.host}:${settings.port}: ${e.javaClass.simpleName} ${e.message}")
            }
        }, "tcp-probe").start()
    }

    private fun currentWifiState(cm: ConnectivityManager): Pair<Boolean, String?> {
        val (isWifi, ssid) = WifiSsidScanner.currentWifiState(applicationContext)
        if (ssid != null) lastKnownSsid = ssid
        return isWifi to (ssid ?: lastKnownSsid)
    }

    private fun connectMqtt() {
        if (destroyed || mqttClient != null) return
        val validationError = settings.validationError()
        if (validationError != null) {
            SyncStatusHolder.setPhase(SyncPhase.DISCONNECTED)
            SyncStatusHolder.setError("Cannot connect: $validationError")
            return
        }
        Log.i(TAG, "connecting to ${settings.host}:${settings.port} tls=${settings.tls} localIp=${localIpv4()}")
        probeTcp()
        SyncStatusHolder.setPhase(SyncPhase.CONNECTING)
        var built: Mqtt3AsyncClient? = null
        try {
            built = MqttClient.builder()
                .useMqttVersion3()
                .identifier("walkingpad2hc-${UUID.randomUUID()}")
                .serverHost(settings.host)
                .serverPort(settings.port)
                .apply { if (settings.tls) sslWithDefaultConfig() }
                .addConnectedListener { _ ->
                    val self = built
                    if (self == null || mqttClient !== self) return@addConnectedListener
                    Log.i(TAG, "CONNACK received — subscribing to ${settings.topic}")
                    SyncStatusHolder.setPhase(SyncPhase.CONNECTED)
                    SyncStatusHolder.setError(null)
                    self.subscribeWith()
                        .topicFilter(settings.topic)
                        .qos(MqttQos.AT_LEAST_ONCE)
                        .callback { publish -> onMessage(String(publish.payloadAsBytes, Charsets.UTF_8)) }
                        .send()
                        .whenComplete { _, ex ->
                            if (ex != null) {
                                Log.w(TAG, "subscribe failed", ex)
                                SyncStatusHolder.setPhase(SyncPhase.DISCONNECTED)
                                SyncStatusHolder.setError("Subscribe failed for topic '${settings.topic}' — ${ex.message}")
                            }
                        }
                }
                .addDisconnectedListener { context ->
                    val cause = context.cause
                    val self = built
                    Log.w(TAG, "disconnected: ${cause?.let { it.javaClass.simpleName } ?: "client"} ${cause?.message ?: ""}")
                    if (self == null || mqttClient !== self) return@addDisconnectedListener
                    mqttClient = null
                    SyncStatusHolder.setPhase(SyncPhase.DISCONNECTED)
                    SyncStatusHolder.setError("Broker disconnected — ${cause?.let { c -> SyncErrors.classify(c) } ?: "connection lost"}")
                    scheduleRetry()
                }
                .buildAsync()
            mqttClient = built

            val connect = built.connectWith()
            if (settings.username.isNotBlank()) {
                connect.simpleAuth()
                    .username(settings.username)
                    .password(settings.password.toByteArray())
                    .applySimpleAuth()
            }
            connect.send().whenComplete { _, ex ->
                connectDispatcher.executor.execute {
                    if (ex != null) {
                        Log.w(TAG, "connect failed: ${ex.javaClass.simpleName}: ${ex.message}", ex)
                        if (mqttClient === built) {
                            built.disconnect()
                            mqttClient = null
                            SyncStatusHolder.setPhase(SyncPhase.DISCONNECTED)
                            SyncStatusHolder.setError("Connect failed: ${SyncErrors.classify(ex)}")
                            scheduleRetry()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "connect threw: ${e.javaClass.simpleName}: ${e.message}", e)
            built?.disconnect()
            if (mqttClient === built) mqttClient = null
            SyncStatusHolder.setPhase(SyncPhase.DISCONNECTED)
            SyncStatusHolder.setError("Connect failed: ${SyncErrors.classify(e)}")
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (retryScheduled.compareAndSet(false, true)) {
            Log.i(TAG, "retry scheduled in 30s")
            lifecycleScope.launch(connectDispatcher) {
                delay(30_000)
                retryScheduled.set(false)
                Log.i(TAG, "retry firing — re-evaluating gate")
                evaluateGate()
            }
        }
    }

    private fun disconnectMqtt() {
        mqttClient?.disconnect()?.whenComplete { _, _ -> }
        mqttClient = null
    }

    private fun startMessageConsumer() {
        lifecycleScope.launch(Dispatchers.Default) {
            for (payload in messages) {
                try {
                    handlePayload(payload)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "payload handling failed", e)
                    SyncStatusHolder.setError("Processing failed: ${e.message}")
                }
            }
        }
    }

    private fun onMessage(payload: String) {
        messages.trySend(payload)
    }

    private suspend fun handlePayload(payload: String) {
        val steps = StepMessageParser.parse(payload, settings.stepsKey)
        if (steps == null) {
            SyncStatusHolder.setError("Unparseable message: ${payload.take(200)}")
            return
        }
        val now = System.currentTimeMillis()
        SyncStatusHolder.update {
            if (it.lastMessageAt == null || now - it.lastMessageAt >= 5_000 || it.mqttSteps != steps)
                it.copy(lastMessageAt = now, mqttSteps = steps)
            else it
        }
        when (val result = calculator.onMessage(steps, now)) {
            is DeltaResult.Record -> {
                if (tryWrite(result)) {
                    settingsRepository.saveLastWritten(result.endSteps, result.endTime)
                    setPending(null)
                    SyncStatusHolder.setError(null)
                } else {
                    calculator.revert(result)
                    setPending(result)
                    SyncStatusHolder.setError(RETRY_MESSAGE)
                }
            }
            DeltaResult.BaselineSet -> {
                var lastWritten = settingsRepository.loadLastWritten()
                var pendingWritten = true
                val pending = pendingRecord
                if (pending != null) {
                    if (tryWrite(pending)) {
                        lastWritten = LastWritten(pending.endSteps, pending.endTime)
                        setPending(null)
                    } else {
                        pendingWritten = false
                        SyncStatusHolder.setError(RETRY_MESSAGE)
                    }
                }
                val lastWrittenDay = lastWritten?.let {
                    LocalDate.ofInstant(Instant.ofEpochMilli(it.endTime), ZoneId.systemDefault())
                }
                val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
                val sameDay = lastWrittenDay == today
                var catchUpWritten = true
                when {
                    lastWritten != null && sameDay && steps > lastWritten.endSteps -> {
                        val start = maxOf(
                            lastWritten.endTime,
                            HealthConnectWriter.startOfTodayMillis(now, ZoneId.systemDefault()),
                        )
                        catchUpWritten = tryWrite(DeltaResult.Record(start, now, steps - lastWritten.endSteps, steps))
                        if (catchUpWritten) settingsRepository.saveLastWritten(steps, now)
                    }
                    lastWritten == null || !sameDay -> {
                        if (steps > 0) {
                            val start = HealthConnectWriter.startOfTodayMillis(now, ZoneId.systemDefault())
                            catchUpWritten = tryWrite(DeltaResult.Record(start, now, steps, steps))
                            if (catchUpWritten) settingsRepository.saveLastWritten(steps, now)
                        }
                    }
                }
                if (pendingWritten && catchUpWritten) SyncStatusHolder.setError(null)
            }
            DeltaResult.Skipped -> {
                val retry = pendingRecord ?: return
                if (tryWrite(retry)) {
                    if (calculator.currentLastSteps() == retry.endSteps - retry.count) {
                        calculator.commit(retry)
                    }
                    settingsRepository.saveLastWritten(retry.endSteps, retry.endTime)
                    setPending(null)
                    SyncStatusHolder.setError(null)
                } else {
                    SyncStatusHolder.setError(RETRY_MESSAGE)
                }
            }
        }
    }

    private suspend fun tryWrite(record: DeltaResult.Record): Boolean =
        withTimeoutOrNull(30_000) { writer.writeSteps(record.startTime, record.endTime, record.count) } == true

    private suspend fun setPending(record: DeltaResult.Record?) {
        pendingRecord = record
        settingsRepository.savePending(
            record?.let { PendingRecord(it.startTime, it.endTime, it.count, it.endSteps) }
        )
    }

    private fun startForegroundInternal() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.sync_channel_name), NotificationManager.IMPORTANCE_LOW)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(getString(R.string.sync_notification_running)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle("WalkingPad Sync")
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setOngoing(true)
            .setSilent(true)
            .build()

    companion object {
        const val ACTION_STOP = "li.klass.walkingpad2hc.action.STOP"
        private const val TAG = "MqttSync"
        private const val RETRY_MESSAGE = "Health Connect write failed — will retry with the next message"
        private const val CHANNEL_ID = "sync"
        private const val NOTIFICATION_ID = 1
    }
}
