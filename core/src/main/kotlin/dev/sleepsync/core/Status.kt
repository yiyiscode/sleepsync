package dev.sleepsync.core

import java.time.Instant
import java.time.OffsetDateTime

/** Metadata only; no sleep data is kept beyond one sync. */
data class SyncStatus(val lastSyncAt: Instant, val newestNight: OffsetDateTime?)

/** Survives app restarts; the production adapter wraps SharedPreferences. */
interface StatusStore {
    fun load(): SyncStatus?
    fun save(status: SyncStatus)
}

/** [sync] plus recording the status after a successful run. Exceptions leave the old status. */
fun syncAndRecord(source: SleepSource, store: SleepStore, statuses: StatusStore, now: Instant): SyncResult {
    var newest: OffsetDateTime? = statuses.load()?.newestNight
    val watching = object : SleepSource {
        override fun sessionsStartingAfter(after: Instant?): List<SleepSession> =
            source.sessionsStartingAfter(after).also { sessions ->
                for (s in sessions) {
                    val n = newest
                    if (n == null || s.start.toInstant().isAfter(n.toInstant())) newest = s.start
                }
            }
    }
    val result = sync(watching, store)
    if (result is SyncResult.Synced) statuses.save(SyncStatus(now, newest))
    return result
}

/** The on-screen message for every sync result. */
fun SyncResult.message(): String = when (this) {
    is SyncResult.Synced -> "Synced $nightsAdded nights into ${months.joinToString()}"
}
