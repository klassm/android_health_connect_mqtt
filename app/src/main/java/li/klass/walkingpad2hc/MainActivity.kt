package li.klass.walkingpad2hc

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.klass.walkingpad2hc.settings.AppSettings
import li.klass.walkingpad2hc.settings.PasswordCipher
import li.klass.walkingpad2hc.settings.SettingsRepository
import li.klass.walkingpad2hc.sync.HealthConnectWriter
import li.klass.walkingpad2hc.sync.StepSyncService
import li.klass.walkingpad2hc.sync.SyncPhase
import li.klass.walkingpad2hc.sync.SyncStatusHolder
import li.klass.walkingpad2hc.sync.WifiSsidScanner
import li.klass.walkingpad2hc.sync.displayText
import li.klass.walkingpad2hc.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = SettingsRepository(applicationContext)
        setContent {
            AppTheme { AppScreen(repository) }
        }
    }
}

class SettingsViewModel : ViewModel() {
    var draft by mutableStateOf(AppSettings())
        private set
    var portText by mutableStateOf("")
        private set
    var initialized = false
    var locationPrompted = false
    var pendingHcRequest = false

    fun load(loaded: AppSettings, corruptPassword: Boolean) {
        if (initialized) return
        initialized = true
        draft = if (corruptPassword) loaded.copy(password = "") else loaded
        portText = loaded.port.toString()
    }

    fun updateDraft(newDraft: AppSettings) {
        draft = newDraft
    }

    fun updatePort(text: String) {
        portText = text
    }

    fun currentSettings(): AppSettings = draft.copy(port = portText.toIntOrNull() ?: draft.port)
}

@Composable
fun AppScreen(repository: SettingsRepository) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stepsToday by remember { mutableStateOf<Long?>(null) }
    var availableSsids by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasScanned by remember { mutableStateOf(false) }
    var inForeground by remember { mutableStateOf(true) }
    var locationGranted by remember { mutableStateOf(false) }
    var nearbyGranted by remember { mutableStateOf(false) }
    var localNetworkGranted by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inForeground = true
                locationGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                nearbyGranted = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
                localNetworkGranted = Build.VERSION.SDK_INT >= 36 && ContextCompat.checkSelfPermission(
                    context, LOCAL_NETWORK_PERMISSION
                ) == PackageManager.PERMISSION_GRANTED
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                inForeground = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hcWrite = HealthPermission.getWritePermission(StepsRecord::class)
    val hcRead = HealthPermission.getReadPermission(StepsRecord::class)
    val errorHcPermission = stringResource(R.string.error_hc_permission)
    val settingsSavedText = stringResource(R.string.settings_saved)
    val settingsSaveFailedText = stringResource(R.string.settings_save_failed)
    val passwordReenterHint = stringResource(R.string.password_reenter_hint)
    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (!granted.contains(hcWrite)) {
            SyncStatusHolder.setError(errorHcPermission)
        }
        ContextCompat.startForegroundService(context, Intent(context, StepSyncService::class.java))
    }
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (result.containsKey(LOCAL_NETWORK_PERMISSION)) {
            localNetworkGranted = result[LOCAL_NETWORK_PERMISSION] == true
        }
        if (viewModel.locationPrompted) {
            viewModel.locationPrompted = false
        }
        if (viewModel.pendingHcRequest) {
            viewModel.pendingHcRequest = false
            hcLauncher.launch(setOf(hcWrite, hcRead))
        }
    }

    fun promptForLocation() {
        if (viewModel.locationPrompted) return
        viewModel.locationPrompted = true
        val permissions = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (Build.VERSION.SDK_INT >= 36 && !localNetworkGranted) add(LOCAL_NETWORK_PERMISSION)
        }
        runtimeLauncher.launch(permissions.toTypedArray())
    }

    fun startSyncFlow() {
        val runtime = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (viewModel.draft.ssid.isNotBlank() && !locationGranted) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (Build.VERSION.SDK_INT >= 36 && !localNetworkGranted) {
                add(LOCAL_NETWORK_PERMISSION)
            }
            // Qualifying permission for the `health` foreground service type
            // (API 34+): without it the service falls back to the time-limited
            // dataSync type. ACTIVITY_RECOGNITION is API 29+.
            if (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        if (runtime.isEmpty()) {
            hcLauncher.launch(setOf(hcWrite, hcRead))
        } else {
            viewModel.pendingHcRequest = true
            runtimeLauncher.launch(runtime.toTypedArray())
        }
    }

    fun saveSettings() {
        val toSave = viewModel.currentSettings()
        scope.launch {
            val ok = withContext(Dispatchers.IO) { repository.save(toSave) }
            snackbarHostState.showSnackbar(
                if (ok) settingsSavedText else settingsSaveFailedText
            )
            if (ok && toSave.ssid.isNotBlank() && !locationGranted) promptForLocation()
        }
    }

    LaunchedEffect(tab, inForeground) {
        if (tab == 0 && inForeground) {
            val writer = HealthConnectWriter(context)
            while (true) {
                stepsToday = withContext(Dispatchers.IO) { writer.stepsToday() }
                delay(60_000)
            }
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                    availableSsids = WifiSsidScanner.latestResults(c)
                    hasScanned = true
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(tab, inForeground) {
        if (tab == 1 && inForeground) {
            while (true) {
                availableSsids = WifiSsidScanner.latestResults(context)
                hasScanned = true
                WifiSsidScanner.startScan(context)
                delay(30_000)
            }
        }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { repository.settings.first() }
        val corrupt = withContext(Dispatchers.IO) { PasswordCipher.isCorrupt(loaded.password) }
        viewModel.load(loaded, corrupt)
        if (corrupt) {
            SyncStatusHolder.setError(passwordReenterHint)
        }
        if (loaded.ssid.isNotBlank() && !locationGranted) promptForLocation()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_status)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (tab == 0) {
                StatusScreen(
                    stepsToday = stepsToday,
                    invalidReason = viewModel.currentSettings().validationError(),
                    onStart = { startSyncFlow() },
                    onStop = {
                        context.startService(
                            Intent(context, StepSyncService::class.java).setAction(StepSyncService.ACTION_STOP)
                        )
                    },
                    onGoToSettings = { tab = 1 },
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                SettingsScreen(
                    draft = viewModel.draft,
                    portText = viewModel.portText,
                    locationGranted = locationGranted,
                    nearbyGranted = nearbyGranted,
                    hasScanned = hasScanned,
                    availableSsids = availableSsids,
                    onDraftChange = { viewModel.updateDraft(it) },
                    onPortChange = { viewModel.updatePort(it) },
                    onSave = { saveSettings() },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
fun StatusScreen(
    stepsToday: Long?,
    invalidReason: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status by SyncStatusHolder.status.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(stringResource(R.string.metric_steps_mqtt), status.mqttSteps, Modifier.weight(1f).fillMaxHeight())
            MetricCard(stringResource(R.string.metric_steps_hc), stepsToday, Modifier.weight(1f).fillMaxHeight())
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusRow(
                    stringResource(R.string.sync_status_label),
                    ok = status.phase == SyncPhase.CONNECTED,
                    value = status.phase.displayText(status.phaseDetail),
                )
                Text(
                    stringResource(R.string.last_message_label, status.lastMessageAt
                        ?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "—"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val error = status.lastError
        if (error != null) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.error_title), style = MaterialTheme.typography.labelMedium)
                    Text(error)
                    if (Build.VERSION.SDK_INT >= 36 && ContextCompat.checkSelfPermission(
                            LocalContext.current, LOCAL_NETWORK_PERMISSION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Text(stringResource(R.string.error_local_network_hint))
                    }
                    status.errorAt?.let {
                        Text(
                            DateFormat.getDateTimeInstance().format(Date(it)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (status.phase == SyncPhase.OFF) {
            Button(onClick = onStart, enabled = invalidReason == null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.start_sync))
            }
            if (invalidReason != null) {
                Text(stringResource(R.string.cannot_start, invalidReason), color = MaterialTheme.colorScheme.error)
                Button(onClick = onGoToSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.go_to_settings))
                }
            }
        } else {
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stop_sync)) }
        }
    }
}

@Composable
fun MetricCard(label: String, value: Long?, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                value?.toString() ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun StatusRow(label: String, ok: Boolean, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                .clearAndSetSemantics {}
        )
        Text("$label: $value")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    draft: AppSettings,
    portText: String,
    locationGranted: Boolean,
    nearbyGranted: Boolean,
    hasScanned: Boolean,
    availableSsids: List<String>,
    onDraftChange: (AppSettings) -> Unit,
    onPortChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }
    var ssidMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft.host,
            onValueChange = { onDraftChange(draft.copy(host = it)) },
            label = { Text(stringResource(R.string.field_mqtt_host)) },
            singleLine = true,
            isError = draft.hostError() != null,
            supportingText = { draft.hostError()?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = portText,
            onValueChange = onPortChange,
            label = { Text(stringResource(R.string.field_mqtt_port)) },
            singleLine = true,
            isError = draft.portError() != null,
            supportingText = { draft.portError()?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.username,
            onValueChange = { onDraftChange(draft.copy(username = it)) },
            label = { Text(stringResource(R.string.field_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Username },
        )
        OutlinedTextField(
            value = draft.password,
            onValueChange = { onDraftChange(draft.copy(password = it)) },
            label = { Text(stringResource(R.string.field_password)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(stringResource(if (showPassword) R.string.hide_password else R.string.show_password))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
        )
        OutlinedTextField(
            value = draft.topic,
            onValueChange = { onDraftChange(draft.copy(topic = it)) },
            label = { Text(stringResource(R.string.field_topic)) },
            singleLine = true,
            isError = draft.topicError() != null,
            supportingText = { draft.topicError()?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.stepsKey,
            onValueChange = { onDraftChange(draft.copy(stepsKey = it)) },
            label = { Text(stringResource(R.string.field_steps_key)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenuBox(
            expanded = ssidMenuExpanded,
            onExpandedChange = { ssidMenuExpanded = it },
        ) {
            OutlinedTextField(
                value = draft.ssid,
                onValueChange = { onDraftChange(draft.copy(ssid = it)) },
                label = { Text(stringResource(R.string.field_ssid)) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.ssid_helper)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                trailingIcon = {
                    IconButton(onClick = { ssidMenuExpanded = !ssidMenuExpanded }) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = ssidMenuExpanded)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = ssidMenuExpanded,
                onDismissRequest = { ssidMenuExpanded = false },
            ) {
                val connectedSsid = remember(availableSsids) {
                    WifiSsidScanner.currentWifiState(context).second
                }
                val options = (availableSsids + listOfNotNull(connectedSsid)).distinct().sorted()
                if (options.isEmpty()) {
                    val locationOk = locationGranted || nearbyGranted
                    val message = when {
                        !locationOk -> stringResource(R.string.scan_needs_location_permission)
                        !WifiSsidScanner.isLocationEnabled(context) -> stringResource(R.string.scan_needs_location_services)
                        hasScanned -> stringResource(R.string.no_networks_found)
                        else -> stringResource(R.string.scanning_networks)
                    }
                    DropdownMenuItem(text = { Text(message) }, onClick = {})
                }
                options.forEach { ssid ->
                    DropdownMenuItem(
                        text = { Text(ssid) },
                        onClick = {
                            onDraftChange(draft.copy(ssid = ssid))
                            ssidMenuExpanded = false
                        },
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = draft.tls,
                    role = Role.Checkbox,
                    onValueChange = { onDraftChange(draft.copy(tls = it)) },
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = draft.tls, onCheckedChange = null)
            Text(stringResource(R.string.use_tls))
        }

        if (draft.ssid.isNotBlank() && !(locationGranted || nearbyGranted)) {
            Text(
                stringResource(R.string.location_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(onClick = onSave, enabled = draft.validationError() == null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.save_settings))
        }
        OutlinedButton(onClick = { openHealthConnectSettings(context) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_health_connect))
        }
        Text(stringResource(R.string.settings_apply_hint), style = MaterialTheme.typography.bodySmall)
    }
}

private fun openHealthConnectSettings(context: Context) {
    try {
        context.startActivity(
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    } catch (_: Exception) {
    }
    try {
        context.startActivity(
            context.packageManager
                .getLaunchIntentForPackage("com.google.android.apps.healthdata")
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}
