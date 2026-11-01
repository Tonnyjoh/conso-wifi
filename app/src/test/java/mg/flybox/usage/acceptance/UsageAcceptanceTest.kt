package mg.flybox.usage.acceptance

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import mg.flybox.usage.domain.DashboardCalculator
import mg.flybox.usage.domain.Sample
import mg.flybox.usage.domain.UsageCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/** AC-5, BR-2, EC-4. Spec only: sums of positive deltas of downlink+uplink since local midnight. */
class UsageAcceptanceTest {
    private val zone = ZoneId.of("Indian/Antananarivo") // UTC+3
    private fun at(day: Int, h: Int, m: Int = 0, z: ZoneId = zone) =
        ZonedDateTime.of(2026, 11, day, h, m, 0, 0, z).toInstant().toEpochMilli()

    private fun s(ts: Long, down: Long, up: Long = 0) = Sample(ts = ts, down = down, up = up)

    @Test fun `AC-5 counts only deltas after midnight`() {
        val samples = listOf(
            s(at(1, 10), 1_000, 100),   // yesterday, must not count
            s(at(1, 18), 5_000, 500),   // yesterday delta 4400
            s(at(2, 1), 5_000, 500),    // first sample after midnight (delta 0 or straddle)
            s(at(2, 9), 7_000, 700),    // +2200
            s(at(2, 15), 8_000, 800),   // +1100
        )
        val now = at(2, 16)
        val today = UsageCalculator.todayUsage(samples, now, zone)
        // Deltas strictly between samples both after midnight: 2200 + 1100 = 3300. The pair
        // straddling midnight (18:00 -> 01:00) is a spec ambiguity and contributes 0 here.
        assertEquals(3300L, today)
    }

    @Test fun `AC-5 yesterday deltas are not counted in today`() {
        val samples = listOf(s(at(1, 8), 0), s(at(1, 20), 9_000_000), s(at(2, 8), 9_000_000), s(at(2, 12), 9_001_000))
        assertEquals(1_000L, UsageCalculator.todayUsage(samples, at(2, 13), zone))
    }

    @Test fun `AC-5 sums downlink and uplink`() {
        val samples = listOf(s(at(2, 8), 1000, 10), s(at(2, 12), 1500, 60))
        assertEquals(550L, UsageCalculator.todayUsage(samples, at(2, 13), zone))
    }

    @Test fun `AC-5 counter reset counts new value as delta`() {
        val samples = listOf(s(at(2, 8), 10_000, 0), s(at(2, 10), 12_000, 0), s(at(2, 12), 300, 200), s(at(2, 14), 1_000, 200))
        // +2000, reset -> 500, then +700
        assertEquals(2000L + 500L + 700L, UsageCalculator.todayUsage(samples, at(2, 15), zone))
    }

    @Test fun `AC-5 counter reset to zero adds nothing and never negative`() {
        val samples = listOf(s(at(2, 8), 10_000), s(at(2, 10), 0))
        assertEquals(0L, UsageCalculator.todayUsage(samples, at(2, 11), zone))
    }

    @Test fun `AC-5 single sample gives zero`() {
        assertEquals(0L, UsageCalculator.todayUsage(listOf(s(at(2, 8), 5)), at(2, 9), zone))
    }

    @Test fun `AC-5 unsorted input gives same result`() {
        val a = listOf(s(at(2, 8), 100), s(at(2, 9), 200), s(at(2, 10), 400))
        assertEquals(300L, UsageCalculator.todayUsage(a.reversed(), at(2, 11), zone))
    }

    @Test fun `EC-4 day boundary uses device local zone, UTC timestamps kept`() {
        // 22:00 UTC on Nov 1 is 01:00 local on Nov 2 in UTC+3.
        val utcLate = ZonedDateTime.of(2026, 11, 1, 22, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val samples = listOf(s(utcLate, 0), s(utcLate + 3_600_000, 4_000))
        val now = utcLate + 7_200_000
        assertEquals(4_000L, UsageCalculator.todayUsage(samples, now, zone))
        // In UTC the same two samples fall on Nov 1 and "today" (Nov 2 00:00+) has none.
        assertEquals(0L, UsageCalculator.todayUsage(samples, utcLate + 3 * 3_600_000L, ZoneId.of("UTC")))
        assertEquals(LocalDate.of(2026, 11, 2), Instant.ofEpochMilli(utcLate).atZone(zone).toLocalDate())
    }

    @Test fun `AC-4 dashboard today usage matches deltas`() {
        val samples = listOf(s(at(2, 8), 0), s(at(2, 12), 2_000_000))
        val d = DashboardCalculator.build(samples, emptyList(), at(2, 13), zone)
        assertEquals(2_000_000L, d.todayBytes)
    }

    @Test fun `BR-1 no samples gives empty dashboard and no invented values`() {
        val d = DashboardCalculator.build(emptyList(), emptyList(), at(2, 13), zone)
        assertEquals(0L, d.todayBytes)
        assertEquals(true, d.isEmpty)
        assertEquals(null, d.prediction)
    }
}
