package mg.flybox.usage.acceptance

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import mg.flybox.usage.domain.DashboardCalculator
import mg.flybox.usage.domain.OrangeSnapshot
import mg.flybox.usage.domain.PredictionEngine
import mg.flybox.usage.domain.RateSource
import mg.flybox.usage.domain.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** BR-4, BR-5, AC-6, AC-7. */
class PredictionAcceptanceTest {
    private val zone = ZoneId.of("Indian/Antananarivo")
    private val now = ZonedDateTime.of(2026, 11, 10, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val h = 3_600_000L
    private val d = 24 * h
    private val farExpiry = LocalDate.of(2026, 12, 10)

    private fun o(ts: Long, rem: Double, total: Double = 100.0, exp: LocalDate = farExpiry) =
        OrangeSnapshot(ts, rem, total, exp, "Pass")

    private fun s(ts: Long, bytes: Long) = Sample(ts = ts, down = bytes, up = 0)

    @Test fun `AC-6 rate from Orange decrease over span`() {
        val hist = listOf(o(now - 2 * d, 20.0), o(now - d, 15.0), o(now, 10.0))
        val r = PredictionEngine.rate(hist, emptyList(), zone)!!
        assertEquals(5.0, r.goPerDay, 1e-6)
        assertEquals(RateSource.ORANGE, r.source)
    }

    @Test fun `AC-6 at risk when empty before expiry, with empty date and 30d prediction`() {
        val hist = listOf(o(now - 2 * d, 20.0), o(now, 10.0))
        val rate = PredictionEngine.rate(hist, emptyList(), zone)
        val p = PredictionEngine.predict(rate, 10.0, farExpiry, now, zone)!!
        assertEquals(2.0, p.daysUntilEmpty, 1e-6)
        assertEquals(10.0 - 30 * 5.0, p.predRemaining30dGo, 1e-6)
        assertTrue(p.atRisk)
        assertEquals(LocalDate.of(2026, 11, 12), p.emptyDate)
    }

    @Test fun `AC-6 green when plan lasts until expiry`() {
        val hist = listOf(o(now - 2 * d, 20.0), o(now, 19.8))
        val rate = PredictionEngine.rate(hist, emptyList(), zone)
        val p = PredictionEngine.predict(rate, 19.8, LocalDate.of(2026, 11, 20), now, zone)!!
        assertEquals(0.1, p.rateGoPerDay, 1e-6)
        assertFalse(p.atRisk)
        assertEquals(19.8 - 3.0, p.predRemaining30dGo, 1e-6)
    }

    @Test fun `BR-5 days until expiry counts to 23h59m59 of the expiry date`() {
        // Expiry today: still ~12 h left. With 0.5 day left of data, rate such that empty in 0.3 day -> at risk.
        val p = PredictionEngine.predict(mg.flybox.usage.domain.Rate(10.0, RateSource.ORANGE), 3.0, LocalDate.of(2026, 11, 10), now, zone)!!
        assertEquals(0.5, p.daysUntilExpiry, 0.01)
        assertTrue(p.atRisk)
        val q = PredictionEngine.predict(mg.flybox.usage.domain.Rate(1.0, RateSource.ORANGE), 3.0, LocalDate.of(2026, 11, 10), now, zone)!!
        assertFalse(q.atRisk)
    }

    @Test fun `AC-7 under 6 hours no prediction and collecting`() {
        val hist = listOf(o(now - 5 * h, 20.0), o(now, 10.0))
        val samples = listOf(s(now - 5 * h, 0), s(now, 5_000_000_000))
        assertNull(PredictionEngine.rate(hist, samples, zone))
        val dash = DashboardCalculator.build(samples, hist, now, zone)
        assertNull(dash.prediction)
        assertTrue(dash.collecting)
    }

    @Test fun `AC-7 single snapshot no prediction`() {
        assertNull(PredictionEngine.rate(listOf(o(now, 10.0)), emptyList(), zone))
    }

    @Test fun `BR-4 exactly 6 hours of Orange history is enough (at least 6 hours)`() {
        val hist = listOf(o(now - 6 * h, 20.0), o(now, 19.0))
        val r = PredictionEngine.rate(hist, emptyList(), zone)
        assertNotNull("spec says 'at least 6 hours', exactly 6h must give a rate", r)
        assertEquals(4.0, r!!.goPerDay, 1e-6)
    }

    @Test fun `BR-4 exactly 6 hours of router history is enough`() {
        val samples = listOf(s(now - 6 * h, 0), s(now, 1_000_000_000))
        val r = PredictionEngine.rate(emptyList(), samples, zone)
        assertNotNull(r)
        assertEquals(4.0, r!!.goPerDay, 1e-6)
        assertEquals(RateSource.ROUTER, r.source)
    }

    @Test fun `BR-4 top-up restarts the Orange window`() {
        // 10 -> 4 over first day, top-up to 30, then 30 -> 24 over the last day: rate must be 6, not mixed.
        val hist = listOf(o(now - 3 * d, 10.0), o(now - 2 * d, 4.0), o(now - d, 30.0), o(now, 24.0))
        val r = PredictionEngine.rate(hist, emptyList(), zone)!!
        assertEquals(6.0, r.goPerDay, 1e-6)
        assertEquals(RateSource.ORANGE, r.source)
    }

    @Test fun `BR-4 top-up less than 6h ago falls back to router counters`() {
        val hist = listOf(o(now - 2 * d, 10.0), o(now - 1 * h, 50.0), o(now, 49.5))
        val samples = listOf(s(now - d, 0), s(now, 12_000_000_000))
        val r = PredictionEngine.rate(hist, samples, zone)!!
        assertEquals(RateSource.ROUTER, r.source)
        assertEquals(12.0, r.goPerDay, 1e-6)
    }

    @Test fun `BR-4 router fallback handles counter reset`() {
        val samples = listOf(s(now - d, 0), s(now - d / 2, 10_000_000_000), s(now, 2_000_000_000))
        val r = PredictionEngine.rate(emptyList(), samples, zone)!!
        assertEquals(12.0, r.goPerDay, 1e-6)
    }

    @Test fun `BR-4 Orange has priority over router`() {
        val hist = listOf(o(now - d, 20.0), o(now, 18.0))
        val samples = listOf(s(now - d, 0), s(now, 9_000_000_000))
        val r = PredictionEngine.rate(hist, samples, zone)!!
        assertEquals(RateSource.ORANGE, r.source)
        assertEquals(2.0, r.goPerDay, 1e-6)
    }

    @Test fun `BR-5 zero consumption gives no prediction (no division by zero)`() {
        val hist = listOf(o(now - d, 20.0), o(now, 20.0))
        assertNull(PredictionEngine.predict(PredictionEngine.rate(hist, emptyList(), zone), 20.0, farExpiry, now, zone))
    }

    @Test fun `AC-6 dashboard exposes prediction with more than 6h of history`() {
        val hist = listOf(o(now - 2 * d, 20.0), o(now, 10.0))
        val dash = DashboardCalculator.build(emptyList(), hist, now, zone)
        assertNotNull(dash.prediction)
        assertFalse(dash.collecting)
        assertEquals(5.0, dash.prediction!!.rateGoPerDay, 1e-6)
    }
}
