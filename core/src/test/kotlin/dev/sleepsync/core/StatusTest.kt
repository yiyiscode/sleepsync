package dev.sleepsync.core

import java.time.Instant
import java.time.OffsetDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatusTest {
    private fun session(start: String, end: String) = SleepSession(
        OffsetDateTime.parse(start), OffsetDateTime.parse(end), 450, 420, null, "com.example.tracker",
    )

    private val source = FakeSleepSource(
        listOf(
            session("2026-11-23T23:00:00+08:00", "2026-11-24T07:00:00+08:00"),
            session("2026-11-24T23:40:00+08:00", "2026-11-25T07:10:00+08:00"),
        ),
    )

    @Test
    fun successfulSyncRecordsSyncTimeAndNewestNight() {
        val statuses = FakeStatusStore()
        val now = Instant.parse("2026-11-25T01:00:00Z")

        syncAndRecord(source, FakeSleepStore(), statuses, now)

        assertEquals(
            SyncStatus(lastSyncAt = now, newestNight = OffsetDateTime.parse("2026-11-24T23:40:00+08:00")),
            statuses.saved,
        )
    }

    @Test
    fun syncResultIsReturnedUnchanged() {
        val result = syncAndRecord(source, FakeSleepStore(), FakeStatusStore(), Instant.EPOCH)

        assertEquals(SyncResult.Synced(2, listOf(YearMonth.of(2026, 11))), result)
    }

    @Test
    fun failedSyncKeepsPreviousStatus() {
        val statuses = FakeStatusStore()
        val failing = object : SleepStore {
            override fun write(month: YearMonth, csv: String) = error("boom")
        }

        runCatching { syncAndRecord(source, failing, statuses, Instant.EPOCH) }

        assertNull(statuses.saved)
    }

    @Test
    fun everyResultHasAMessage() {
        assertEquals(
            "Synced 2 nights into 2026-11",
            SyncResult.Synced(2, listOf(YearMonth.of(2026, 11))).message(),
        )
    }
}
