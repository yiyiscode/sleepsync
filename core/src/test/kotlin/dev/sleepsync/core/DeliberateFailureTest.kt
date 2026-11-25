package dev.sleepsync.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DeliberateFailureTest {
    @Test
    fun failsOnPurpose() {
        assertEquals(1, 2)
    }
}
