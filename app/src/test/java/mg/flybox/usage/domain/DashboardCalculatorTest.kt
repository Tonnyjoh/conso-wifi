package mg.flybox.usage.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardCalculatorTest {
    private val zone = ZoneId.of("Indian/Antananarivo")
    private fun at(d: Int, h: Int) = ZonedDateTime.of(2026, 10, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val gb = 1_000_000_000L

    @Test fun emptyHistory() {
        val d = DashboardCalculator.build(emptyList(), emptyList(), at(28, 10), zone)
        assertTrue(d.isEmpty)
        assertNull(d.prediction)
        assertEquals(0L, d.todayBytes)
    }

    @Test fun lessThanSixHoursGivesNoPrediction() {
        val samples = listOf(Sample(at(28, 8), 0, 0), Sample(at(28, 10), gb, 0))
        val plan = listOf(OrangeSnapshot(at(28, 10), 40.0, 60.0, LocalDate.of(2026, 12, 14), "P"))
        val d = DashboardCalculator.build(samples, plan, at(28, 10), zone)
        assertNull(d.prediction)
        assertTrue(d.collecting)
        assertEquals(gb, d.todayBytes)
    }

    @Test fun moreThanSixHoursGivesPredictionAndComparison() {
        val samples = listOf(
            Sample(at(26, 8), 0, 0), Sample(at(26, 20), 2 * gb, 0),
            Sample(at(27, 20), 4 * gb, 0), Sample(at(28, 10), 5 * gb, 0),
        )
        val plan = listOf(
            OrangeSnapshot(at(26, 8), 40.0, 60.0, LocalDate.of(2026, 12, 14), "P"),
            OrangeSnapshot(at(28, 10), 35.0, 60.0, LocalDate.of(2026, 12, 14), "P"),
        )
        val d = DashboardCalculator.build(samples, plan, at(28, 10), zone)
        assertNotNull(d.prediction)
        assertEquals(RateSource.ORANGE, d.rate!!.source)
        assertEquals(gb, d.todayBytes)
        assertEquals(2 * gb, d.averageDailyBytes)
    }
}
