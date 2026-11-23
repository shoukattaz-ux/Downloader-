package com.stashly.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {
    @Test fun bytes() {
        assertEquals("—", Formatters.bytes(null)); assertEquals("0 B", Formatters.bytes(0)); assertEquals("1023 B", Formatters.bytes(1023))
        assertEquals("1.0 KB", Formatters.bytes(1024)); assertEquals("1.5 MB", Formatters.bytes(1_572_864)); assertEquals("2.0 GB", Formatters.bytes(2L * 1024 * 1024 * 1024))
    }
    @Test fun speed() { assertEquals("—", Formatters.speed(0)); assertEquals("2.0 MB/s", Formatters.speed(2L * 1024 * 1024)) }
    @Test fun duration() {
        assertEquals("—", Formatters.duration(null)); assertEquals("0:09", Formatters.duration(9)); assertEquals("1:05", Formatters.duration(65)); assertEquals("1:01:01", Formatters.duration(3661))
    }
    @Test fun eta() { assertNull(Formatters.eta(null)); assertEquals("45s left", Formatters.eta(45)); assertEquals("2m 5s left", Formatters.eta(125)); assertEquals("1h 1m left", Formatters.eta(3700)) }
    @Test fun percent() { assertEquals(0, Formatters.percent(5, 0)); assertEquals(50, Formatters.percent(5, 10)); assertEquals(100, Formatters.percent(20, 10)) }
}
