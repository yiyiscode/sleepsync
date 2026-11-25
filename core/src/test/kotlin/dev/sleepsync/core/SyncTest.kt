package dev.sleepsync.core

import java.time.OffsetDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun secondSyncWithSameSourceAddsNothingAndIsUpToDate() {
        val source = FakeSleepSource(listOf(session("2026-11-24T23:40:00+08:00", "2026-11-25T07:10:00+08:00")))
        val store = FakeSleepStore()
        sync(source, store)
        val afterFirst = store.files.toMap()

        val result = sync(source, store)

        assertEquals(afterFirst, store.files)
        assertEquals(SyncResult.UpToDate, result)
    }

    @Test
    fun sameSessionTwiceInOneBatchIsWrittenOnce() {
        val night = session("2026-11-24T23:40:00+08:00", "2026-11-25T07:10:00+08:00")
        val store = FakeSleepStore()

        val result = sync(FakeSleepSource(listOf(night, night)), store)

        assertEquals(
            "$header\n2026-11-24T23:40:00+08:00,2026-11-25T07:10:00+08:00,450,420,,,,,com.example.tracker\n",
            store.files[YearMonth.of(2026, 11)],
        )
        assertEquals(SyncResult.Synced(1, listOf(YearMonth.of(2026, 11))), result)
    }

    @Test
    fun existingRowsArePreservedByteForByteAndNewRowsMergeByStart() {
        // Written in a form the encoder would not produce (no seconds, quoted app), so re-encoding would change it.
        val existingRow = "2026-11-10T23:00+08:00,2026-11-11T06:00+08:00,420,400,,,,,\"com.example.tracker\""
        val november = YearMonth.of(2026, 11)
        val store = FakeSleepStore().apply { files[november] = "$header\n$existingRow\n" }
        val source = FakeSleepSource(
            listOf(
                session("2026-11-12T00:00:00+08:00", "2026-11-12T07:00:00+08:00"),
                session("2026-11-05T23:30:00+08:00", "2026-11-06T06:30:00+08:00"),
                // same start instant as the existing row, written in another offset: already stored
                session("2026-11-10T15:00:00Z", "2026-11-10T22:00:00Z"),
            ),
        )

        val result = sync(source, store)

        assertEquals(
            "$header\n" +
                "2026-11-05T23:30:00+08:00,2026-11-06T06:30:00+08:00,450,420,,,,,com.example.tracker\n" +
                "$existingRow\n" +
                "2026-11-12T00:00:00+08:00,2026-11-12T07:00:00+08:00,450,420,,,,,com.example.tracker\n",
            store.files[november],
        )
        assertEquals(SyncResult.Synced(2, listOf(november)), result)
    }
}
