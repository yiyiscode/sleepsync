package dev.sleepsync.core

import java.time.OffsetDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SyncTest {
    private val header = "start,end,in_bed_min,asleep_min,light_min,deep_min,rem_min,awake_min,source_app"

    private fun session(
        start: String,
        end: String,
        stages: Stages? = null,
    ) = SleepSession(
        start = OffsetDateTime.parse(start),
        end = OffsetDateTime.parse(end),
        inBedMin = 450,
        asleepMin = 420,
        stages = stages,
        sourceApp = "com.example.tracker",
    )

    @Test
    fun firstSyncIntoEmptyStoreWritesMonthFile() {
        val source = FakeSleepSource(
            listOf(
                session(
                    "2026-11-24T23:40:00+08:00", "2026-11-25T07:10:00+08:00",
                    Stages(light = 240, deep = 90, rem = 90, awake = 30),
                ),
            ),
        )
        val store = FakeSleepStore()

        val result = sync(source, store)

        assertEquals(
            "$header\n" +
                "2026-11-24T23:40:00+08:00,2026-11-25T07:10:00+08:00,450,420,240,90,90,30,com.example.tracker\n",
            store.files[YearMonth.of(2026, 11)],
        )
        assertEquals(SyncResult.Synced(nightsAdded = 1, months = listOf(YearMonth.of(2026, 11))), result)
    }

    @Test
    fun sessionWithoutStagesLeavesStageCellsEmptyNotZero() {
        val source = FakeSleepSource(listOf(session("2026-11-01T00:30:00+08:00", "2026-11-01T07:00:00+08:00")))
        val store = FakeSleepStore()

        sync(source, store)

        assertEquals(
            "$header\n2026-11-01T00:30:00+08:00,2026-11-01T07:00:00+08:00,450,420,,,,,com.example.tracker\n",
            store.files[YearMonth.of(2026, 11)],
        )
    }

    @Test
    fun sessionsSpanningTwoMonthsWriteBothFilesByStartMonthSortedByStart() {
        val source = FakeSleepSource(
            listOf(
                session("2026-11-02T00:00:00+08:00", "2026-11-02T07:00:00+08:00"),
                // starts 31 Oct, ends in November: belongs to October
                session("2026-10-31T23:40:00+08:00", "2026-11-01T07:00:00+08:00"),
                session("2026-10-30T23:00:00+08:00", "2026-10-31T06:00:00+08:00"),
            ),
        )
        val store = FakeSleepStore()

        val result = sync(source, store)

        val october = store.files.getValue(YearMonth.of(2026, 10)).lines().filter { it.isNotEmpty() }
        assertEquals(
            listOf("2026-10-30T23:00:00+08:00", "2026-10-31T23:40:00+08:00"),
            october.drop(1).map { it.substringBefore(',') },
        )
        assertEquals(1, store.files.getValue(YearMonth.of(2026, 11)).lines().count { it.isNotEmpty() } - 1)
        assertEquals(
            SyncResult.Synced(3, listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11))),
            result,
        )
    }

    // Setup failures: each missing piece maps to its own result so the UI can offer its own recovery.

    private val oneNight = FakeSleepSource(listOf(session("2026-11-01T00:30:00+08:00", "2026-11-01T07:00:00+08:00")))

    @Test
    fun healthConnectMissingOrOutdatedMapsToItsOwnResult() {
        val result = sync(FailingSleepSource(HealthConnectUnavailableException()), FakeSleepStore())

        assertEquals(SyncResult.NeedsHealthConnect, result)
    }

    @Test
    fun deniedOrRevokedSleepPermissionMapsToNeedsPermissionOnEverySync() {
        val source = FailingSleepSource(SleepPermissionMissingException())

        assertEquals(SyncResult.NeedsHealthConnectPermission, sync(source, FakeSleepStore()))
        assertEquals(SyncResult.NeedsHealthConnectPermission, sync(source, FakeSleepStore()))
    }

    @Test
    fun cancelledOrExpiredSignInMapsToNeedsSignInAndWritesNothing() {
        val store = FailingSleepStore(SignInRequiredException())

        assertEquals(SyncResult.NeedsSignIn, sync(oneNight, store))
    }

    @Test
    fun unrelatedFailuresStillPropagate() {
        assertFailsWith<IllegalStateException> { sync(FailingSleepSource(IllegalStateException("boom")), FakeSleepStore()) }
    }
}
