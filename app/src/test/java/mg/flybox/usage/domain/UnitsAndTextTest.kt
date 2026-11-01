package mg.flybox.usage.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsAndTextTest {
    private fun nb(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    @Test fun decimalGigabytes() = assertEquals("2,34 Go", nb(Units.formatBytes(2_340_000_000L)))

    @Test fun underOneGoShowsMegabytes() = assertEquals("850 Mo", nb(Units.formatBytes(850_000_000L)))

    @Test fun alwaysGoOption() = assertEquals("0,85 Go", nb(Units.formatBytes(850_000_000L, alwaysGo = true)))

    @Test fun remainingGoUnderOneShownInMo() = assertEquals("500 Mo", nb(Units.formatGo(0.5)))

    @Test fun rate() = assertEquals("3,2 Mo/s", nb(Units.formatRate(3_200_000.0)))

    @Test fun agoText() {
        val now = 10_000_000L
        assertEquals("il y a 4 min", AgeFormatter.ago(now - 4 * 60_000, now))
        assertEquals("à l'instant", AgeFormatter.ago(now - 10_000, now))
        assertEquals("il y a 3 h", AgeFormatter.ago(now - 3 * 3_600_000L, now))
        assertTrue(AgeFormatter.isStale(now - 25 * 3_600_000L, now))
        assertFalse(AgeFormatter.isStale(now - 23 * 3_600_000L, now))
    }

    @Test fun reminderContainsUsageRemainingAndRisk() {
        val plan = OrangeSnapshot(0, 12.0, 60.0, LocalDate.of(2026, 12, 14), "P")
        val risk = Prediction(5.0, RateSource.ORANGE, 12.0, -138.0, 2.4, 40.0, true, LocalDate.of(2026, 11, 3), 0.0)
        val text = nb(ReminderText.build(1_500_000_000L, plan, risk, false))
        assertTrue(text.contains("Aujourd'hui : 1,50 Go"))
        assertTrue(text.contains("Reste : 12,0 Go sur 60 Go"))
        assertTrue(text.contains("Risque de dépassement"))
        assertTrue(text.contains("03/11"))
        val calm = nb(ReminderText.build(1_500_000_000L, plan, risk.copy(atRisk = false), false))
        assertFalse(calm.contains("Risque"))
    }
}
