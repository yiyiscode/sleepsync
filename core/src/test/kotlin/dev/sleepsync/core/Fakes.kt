package dev.sleepsync.core

import java.time.Instant
import java.time.YearMonth

class FakeSleepSource(private val sessions: List<SleepSession>) : SleepSource {
    override fun sessionsStartingAfter(after: Instant?): List<SleepSession> =
        sessions.filter { after == null || it.start.toInstant().isAfter(after) }
}

/** Holds month files as strings so tests read like real CSVs. */
class FakeSleepStore : SleepStore {
    val files = mutableMapOf<YearMonth, String>()

    override fun write(month: YearMonth, csv: String) {
        files[month] = csv
    }
}

/** A source or store that fails the way a missing setup does. */
class FailingSleepSource(private val failure: Exception) : SleepSource {
    override fun sessionsStartingAfter(after: Instant?): List<SleepSession> = throw failure
}

class FailingSleepStore(private val failure: Exception) : SleepStore {
    override fun write(month: YearMonth, csv: String) = throw failure
}
