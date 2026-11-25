package dev.sleepsync.app

import android.content.Context
import dev.sleepsync.core.StatusStore
import dev.sleepsync.core.SyncStatus
import java.time.Instant
import java.time.OffsetDateTime

/** Keeps only last-sync time and newest night start; no sleep data. */
class PrefsStatusStore(context: Context) : StatusStore {
    private val prefs = context.getSharedPreferences("sync_status", Context.MODE_PRIVATE)

    override fun load(): SyncStatus? {
        val at = prefs.getString("lastSyncAt", null) ?: return null
        return SyncStatus(Instant.parse(at), prefs.getString("newestNight", null)?.let(OffsetDateTime::parse))
    }

    override fun save(status: SyncStatus) {
        prefs.edit()
            .putString("lastSyncAt", status.lastSyncAt.toString())
            .putString("newestNight", status.newestNight?.toString())
            .apply()
    }
}
