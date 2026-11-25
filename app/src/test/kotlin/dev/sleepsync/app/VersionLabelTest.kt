package dev.sleepsync.app

import kotlin.test.Test
import kotlin.test.assertEquals

class VersionLabelTest {
    @Test
    fun formatsVersionName() {
        assertEquals("Version 0.1.0", VersionLabel.format("0.1.0"))
    }
}
