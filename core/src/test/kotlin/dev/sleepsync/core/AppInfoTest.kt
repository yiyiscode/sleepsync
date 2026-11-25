package dev.sleepsync.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AppInfoTest {
    @Test
    fun appNameIsSleepSync() {
        assertEquals("SleepSync", AppInfo.NAME)
    }
}
