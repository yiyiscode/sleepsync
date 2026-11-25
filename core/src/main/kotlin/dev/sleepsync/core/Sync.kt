package dev.sleepsync.core

import java.time.Instant
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Minutes per sleep stage. A session with no stage data has `stages == null`, never zeros. */
data class Stages(val light: Int, val deep: Int, val rem: Int, val awake: Int)

/** One Health Connect sleep record; one CSV row. */
data class SleepSession(
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val inBedMin: Int,
    val asleepMin: Int,
    val stages: Stages?,
    val sourceApp: String,
)

/** Where sessions are read from. `after == null` means all available history. */
interface SleepSource {
    fun sessionsStartingAfter(after: Instant?): List<SleepSession>
}

/** Where month files are written. */
interface SleepStore {
    fun write(month: YearMonth, csv: String)
}

/** Ports throw these when setup is missing; [sync] turns them into results. */
class HealthConnectUnavailableException : Exception("Health Connect is missing or needs an update")
class SleepPermissionMissingException : Exception("Sleep read permission not granted")
class SignInRequiredException : Exception("Google sign-in missing or expired")

/** Ports throw these when setup is missing; [sync] turns them into results. */
class HealthConnectUnavailableException : Exception("Health Connect is missing or needs an update")
class SleepPermissionMissingException : Exception("Sleep read permission not granted")
class SignInRequiredException : Exception("Google sign-in missing or expired")

sealed interface SyncResult {
    data object NeedsHealthConnect : SyncResult
    data object NeedsHealthConnectPermission : SyncResult
    data object NeedsSignIn : SyncResult
    data object NeedsHealthConnect : SyncResult
    data object NeedsHealthConnectPermission : SyncResult
    data object NeedsSignIn : SyncResult

    data class Synced(val nightsAdded: Int, val months: List<YearMonth>) : SyncResult
}

/** The single entry point the UI calls. */
fun sync(source: SleepSource, store: SleepStore): SyncResult = try {
    syncOrThrow(source, store)
} catch (_: HealthConnectUnavailableException) {
    SyncResult.NeedsHealthConnect
} catch (_: SleepPermissionMissingException) {
    SyncResult.NeedsHealthConnectPermission
} catch (_: SignInRequiredException) {
    SyncResult.NeedsSignIn
}

private fun syncOrThrow(source: SleepSource, store: SleepStore): SyncResult {
    val byMonth = source.sessionsStartingAfter(null)
        .groupBy { YearMonth.from(it.start) } // month of the session's own local start
        .toSortedMap()
    for ((month, sessions) in byMonth) {
        store.write(month, SleepCsv.encode(sessions))
    }
    return SyncResult.Synced(byMonth.values.sumOf { it.size }, byMonth.keys.toList())
}

/** The CSV contract from docs/specs/sleepsync-v1.md. */
object SleepCsv {
    const val HEADER = "start,end,in_bed_min,asleep_min,light_min,deep_min,rem_min,awake_min,source_app"

    fun encode(sessions: List<SleepSession>): String =
        (listOf(HEADER) + sessions.sortedBy { it.start.toInstant() }.map(::row)).joinToString("\n", postfix = "\n")

    private fun row(s: SleepSession): String = listOf(
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(s.start),
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(s.end),
        s.inBedMin.toString(),
        s.asleepMin.toString(),
        s.stages?.light?.toString() ?: "",
        s.stages?.deep?.toString() ?: "",
        s.stages?.rem?.toString() ?: "",
        s.stages?.awake?.toString() ?: "",
        quote(s.sourceApp),
    ).joinToString(",")

    private fun quote(v: String) =
        if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
