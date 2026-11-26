package dev.sleepsync.app

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.sleepsync.core.SleepSession
import dev.sleepsync.core.SleepSource
import dev.sleepsync.core.Stages
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

/** Production sleep source. Call from a background thread. */
class HealthConnectSleepSource(private val client: HealthConnectClient) : SleepSource {
    override fun sessionsStartingAfter(after: Instant?): List<SleepSession> = runBlocking {
        val out = mutableListOf<SleepSession>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    TimeRangeFilter.between(after ?: Instant.EPOCH, Instant.now()),
                    pageToken = pageToken,
                ),
            )
            out += response.records.map(::toSession)
            pages++
            Log.d("SleepSyncHC", "page=$pages records=${response.records.size} nextToken=${response.pageToken?.take(12)}")
            // Experiment: stop on an empty page, a repeated token, or too many pages.
            val next = response.pageToken
            pageToken = if (response.records.isEmpty() || next == pageToken || pages >= 20) null else next
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
