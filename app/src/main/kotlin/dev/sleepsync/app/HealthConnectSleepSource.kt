package dev.sleepsync.app

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.sleepsync.core.HealthConnectUnavailableException
import dev.sleepsync.core.SleepPermissionMissingException
import dev.sleepsync.core.SleepSession
import dev.sleepsync.core.SleepSource
import dev.sleepsync.core.Stages
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

/** Production sleep source. Call from a background thread. */
class HealthConnectSleepSource(private val context: Context) : SleepSource {
    override fun sessionsStartingAfter(after: Instant?): List<SleepSession> = runBlocking {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            throw HealthConnectUnavailableException()
        }
        val client = HealthConnectClient.getOrCreate(context)
        // Checked on every sync, so a permission revoked in system settings is caught here.
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) !in client.permissionController.getGrantedPermissions()) {
            throw SleepPermissionMissingException()
        }
        val out = mutableListOf<SleepSession>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    TimeRangeFilter.between(after ?: Instant.EPOCH, Instant.now()),
                    pageToken = pageToken,
                ),
            )
            out += response.records.map(::toSession)
            pageToken = response.pageToken
        } while (pageToken != null)
        out
    }

    private fun toSession(r: SleepSessionRecord): SleepSession {
        fun minutes(vararg types: Int) = r.stages.filter { it.stage in types }
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()

        val total = Duration.between(r.startTime, r.endTime).toMinutes().toInt()
        val stages = if (r.stages.isEmpty()) null else Stages(
            light = minutes(SleepSessionRecord.STAGE_TYPE_LIGHT),
            deep = minutes(SleepSessionRecord.STAGE_TYPE_DEEP),
            rem = minutes(SleepSessionRecord.STAGE_TYPE_REM),
            awake = minutes(SleepSessionRecord.STAGE_TYPE_AWAKE),
        )
        val zone = ZoneId.systemDefault()
        return SleepSession(
            start = r.startTime.atOffset(r.startZoneOffset ?: zone.rules.getOffset(r.startTime)),
            end = r.endTime.atOffset(r.endZoneOffset ?: zone.rules.getOffset(r.endTime)),
            inBedMin = total,
            asleepMin = if (stages == null) total else
                minutes(
                    SleepSessionRecord.STAGE_TYPE_SLEEPING,
                    SleepSessionRecord.STAGE_TYPE_LIGHT,
                    SleepSessionRecord.STAGE_TYPE_DEEP,
                    SleepSessionRecord.STAGE_TYPE_REM,
                ),
            stages = stages,
            sourceApp = r.metadata.dataOrigin.packageName,
        )
    }
}
