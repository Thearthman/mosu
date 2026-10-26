package com.mosu.app.domain.rating

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingRefreshBackoffTest {
    @Test
    fun delaysDoubleFromOneSecondUpToTheOneHourCap() {
        var now = 0L
        val backoff = RatingRefreshBackoff(clockMs = { now })
        var delay = 1_000L
        val cap = 3_600_000L

        while (delay < cap) {
            backoff.recordIncomplete(42)
            assertFalse(backoff.isReady(42))
            now += delay - 1
            assertFalse(backoff.isReady(42))
            now += 1
            assertTrue(backoff.isReady(42))
            delay = (delay * 2L).coerceAtMost(cap)
        }

        backoff.recordIncomplete(42)
        now += cap - 1
        assertFalse(backoff.isReady(42))
        now += 1
        assertTrue(backoff.isReady(42))
    }

    @Test
    fun completeResolutionResetsTheDelay() {
        var now = 0L
        val backoff = RatingRefreshBackoff(clockMs = { now })

        backoff.recordIncomplete(42)
        now += 1_000L
        backoff.recordIncomplete(42)
        backoff.recordResolved(42)
        backoff.recordIncomplete(42)

        now += 999L
        assertFalse(backoff.isReady(42))
        now += 1L
        assertTrue(backoff.isReady(42))
    }
}
