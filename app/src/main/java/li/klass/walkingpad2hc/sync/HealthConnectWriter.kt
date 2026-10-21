package li.klass.walkingpad2hc.sync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata as HcMetadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId

class HealthConnectWriter(private val context: Context) {

    suspend fun writeSteps(startTime: Long, endTime: Long, count: Long): Boolean = try {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            false
        } else {
            val client = HealthConnectClient.getOrCreate(context)
            client.insertRecords(
                listOf(
                    StepsRecord(
                        Instant.ofEpochMilli(startTime),
                        null,
                        Instant.ofEpochMilli(endTime),
                        null,
                        count,
                        HcMetadata.unknownRecordingMethod(clientRecordId = "wp2hc-$startTime-$endTime-$count"),
                    )
                )
            )
            Log.i(
                "MqttSync",
                "HC write ok: count=$count ${Instant.ofEpochMilli(startTime)} → ${Instant.ofEpochMilli(endTime)}",
            )
            true
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("MqttSync", "HC write failed: count=$count ${Instant.ofEpochMilli(startTime)} → ${Instant.ofEpochMilli(endTime)}: ${e.message}")
        false
    }

    suspend fun stepsToday(nowMillis: Long = System.currentTimeMillis()): Long? = try {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            null
        } else {
            val client = HealthConnectClient.getOrCreate(context)
            val result = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(startOfTodayMillis(nowMillis, ZoneId.systemDefault())),
                        Instant.ofEpochMilli(nowMillis),
                    ),
                )
            )
            result[StepsRecord.COUNT_TOTAL] ?: 0L
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    companion object {
        fun startOfTodayMillis(nowMillis: Long, zone: ZoneId): Long =
            Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
