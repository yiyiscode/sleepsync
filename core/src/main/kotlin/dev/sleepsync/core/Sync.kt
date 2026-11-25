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

/** Where month files are read and written. `read` returns null when the month has no file yet. */
interface SleepStore {
    fun read(month: YearMonth): String?
    fun write(month: YearMonth, csv: String)
}

sealed interface SyncResult {
    data class Synced(val nightsAdded: Int, val months: List<YearMonth>) : SyncResult
    data object UpToDate : SyncResult
}

/** The single entry point the UI calls. */
fun sync(source: SleepSource, store: SleepStore): SyncResult {
    val byMonth = source.sessionsStartingAfter(null)
        .distinctBy { it.start.toInstant() } // Health Connect returns every segment twice
        .groupBy { YearMonth.from(it.start) } // month of the session's own local start
        .toSortedMap()
    val months = mutableListOf<YearMonth>()
    var added = 0
    for ((month, sessions) in byMonth) {
        val existing = store.read(month)?.let(SleepCsv::rows).orEmpty()
        val stored = existing.map(SleepCsv::startOf).toSet()
        val fresh = sessions.filter { it.start.toInstant() !in stored }
        if (fresh.isEmpty()) continue
        store.write(month, SleepCsv.merge(existing, fresh))
        months += month
        added += fresh.size
    }
    return if (added == 0) SyncResult.UpToDate else SyncResult.Synced(added, months)
}

/** The CSV contract from docs/specs/sleepsync-v1.md. */
object SleepCsv {
    const val HEADER = "start,end,in_bed_min,asleep_min,light_min,deep_min,rem_min,awake_min,source_app"

    fun encode(sessions: List<SleepSession>): String = merge(emptyList(), sessions)

    /** Data lines of a month file, kept as the original strings so they are rewritten byte-for-byte. */
    fun rows(csv: String): List<String> = csv.lines().drop(1).filter { it.isNotEmpty() }

    fun startOf(row: String): Instant = OffsetDateTime.parse(row.substringBefore(',')).toInstant()

    /** Existing rows untouched plus new sessions, sorted by start. */
    fun merge(existing: List<String>, sessions: List<SleepSession>): String =
        (existing.map { startOf(it) to it } + sessions.map { it.start.toInstant() to row(it) })
            .sortedBy { it.first }
            .let { listOf(HEADER) + it.map { (_, line) -> line } }
            .joinToString("\n", postfix = "\n")

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
