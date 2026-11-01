package mg.flybox.usage.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionEngineTest {
    private val zone = ZoneId.of("Indian/Antananarivo")
    private val hour = 3_600_000L
    private val t0 = ZonedDateTime.of(2026, 10, 20, 8, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun o(ts: Long, rem: Double) = OrangeSnapshot(ts, rem, 60.0, LocalDate.of(2026, 12, 14), "Forfait")
    private fun s(ts: Long, down: Long) = Sample(ts = ts, down = down, up = 0)

    @Test fun orangeRateNeedsSixHours() {
        assertNull(PredictionEngine.orangeRate(listOf(o(t0, 50.0), o(t0 + 5 * hour, 49.0))))
        assertNull(PredictionEngine.orangeRate(listOf(o(t0, 50.0))))
    }

    @Test fun orangeRateOverOneDay() {
        val r = PredictionEngine.orangeRate(listOf(o(t0, 50.0), o(t0 + 24 * hour, 47.0)))!!
        assertEquals(3.0, r, 1e-9)
    }

    @Test fun topUpRestartsWindow() {
        // 40 -> 30 (consommation), puis recharge à 60, puis 60 -> 57 sur un jour.
        val h = listOf(
            o(t0, 40.0), o(t0 + 24 * hour, 30.0),
            o(t0 + 30 * hour, 60.0), o(t0 + 54 * hour, 57.0),
        )
        assertEquals(3.0, PredictionEngine.orangeRate(h)!!, 1e-9)
    }

    @Test fun orangeRateNullWhenNothingConsumed() {
        assertNull(PredictionEngine.orangeRate(listOf(o(t0, 50.0), o(t0 + 24 * hour, 50.0))))
    }

    @Test fun routerFallbackUsesCountersOverObservedSpan() {
        val gb = 1_000_000_000L
        val rows = listOf(s(t0, 0), s(t0 + 12 * hour, 1 * gb), s(t0 + 24 * hour, 2 * gb))
        val rate = PredictionEngine.routerRate(rows, zone)!!
        assertEquals(2.0, rate, 1e-9)
    }

    @Test fun routerFallbackNullUnderSixHours() {
        val rows = listOf(s(t0, 0), s(t0 + 5 * hour, 5_000_000_000L))
        assertNull(PredictionEngine.routerRate(rows, zone))
    }

    @Test fun rateChoosesOrangeFirstThenRouterThenNull() {
        val gb = 1_000_000_000L
        val orange = listOf(o(t0, 50.0), o(t0 + 24 * hour, 47.0))
        val rows = listOf(s(t0, 0), s(t0 + 24 * hour, 2 * gb))
        assertEquals(RateSource.ORANGE, PredictionEngine.rate(orange, rows, zone)!!.source)
        assertEquals(RateSource.ROUTER, PredictionEngine.rate(emptyList(), rows, zone)!!.source)
        assertNull(PredictionEngine.rate(emptyList(), listOf(s(t0, 0)), zone))
    }

    private fun now() = ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun atRiskWhenEmptyBeforeExpiry() {
        // 30 Go restants, 5 Go/jour : vide dans 6 jours, expiration dans environ 43 jours.
        val p = PredictionEngine.predict(Rate(5.0, RateSource.ORANGE), 30.0, LocalDate.of(2026, 12, 14), now(), zone)!!
        assertTrue(p.atRisk)
        assertEquals(6.0, p.daysUntilEmpty, 1e-9)
        assertEquals(-120.0, p.predRemaining30dGo, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 7), p.emptyDate)
        assertEquals(0.0, p.remainingAtExpiryGo, 1e-9)
    }

    @Test fun onTrackWhenPlanLastsUntilExpiry() {
        val p = PredictionEngine.predict(Rate(1.0, RateSource.ROUTER), 30.0, LocalDate.of(2026, 11, 20), now(), zone)!!
        assertFalse(p.atRisk)
        assertEquals(0.0, p.predRemaining30dGo, 1e-9)
        assertNotNull(p.emptyDate)
        // expiration le 20 à 23:59:59 : 19.5 jours après le 1er à midi.
        assertEquals(19.5, p.daysUntilExpiry, 1e-9)
        assertEquals(10.5, p.remainingAtExpiryGo, 1e-9)
    }

    @Test fun zeroOrMissingRateGivesNoPrediction() {
        assertNull(PredictionEngine.predict(null, 30.0, LocalDate.of(2026, 12, 14), now(), zone))
        assertNull(PredictionEngine.predict(Rate(0.0, RateSource.ROUTER), 30.0, LocalDate.of(2026, 12, 14), now(), zone))
    }

    @Test fun emptyDateHiddenBeyondTenYears() {
        val p = PredictionEngine.predict(Rate(0.0001, RateSource.ROUTER), 30.0, LocalDate.of(2026, 12, 14), now(), zone)!!
        assertNull(p.emptyDate)
        assertFalse(p.atRisk)
    }

    @Test fun exactlySixHoursOfOrangeHistoryGivesRate() {
        val r = PredictionEngine.orangeRate(listOf(o(t0, 50.0), o(t0 + 6 * hour, 49.0)))
        assertEquals(4.0, r!!, 1e-9)
    }
}
