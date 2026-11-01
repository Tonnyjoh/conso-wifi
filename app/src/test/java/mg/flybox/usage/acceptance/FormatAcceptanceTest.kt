package mg.flybox.usage.acceptance

import mg.flybox.usage.domain.AgeFormatter
import mg.flybox.usage.domain.PinLockState
import mg.flybox.usage.domain.PinPolicy
import mg.flybox.usage.domain.OrangeSnapshot
import mg.flybox.usage.domain.Prediction
import mg.flybox.usage.domain.RateSource
import mg.flybox.usage.domain.ReminderText
import mg.flybox.usage.domain.Units
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BR-6, AC-13 (age), AC-2 (PIN), AC-12/BR-8 (reminder text), AC-1 (PIN format). */
class FormatAcceptanceTest {
    @Test fun `BR-6 one Go is 10 to the 9 bytes`() {
        assertEquals("1,00 Go", Units.formatBytes(1_000_000_000))
        assertEquals("1,07 Go", Units.formatBytes(1_073_741_824)) // not binary GiB rounding to 1,00
    }

    @Test fun `BR-6 amounts under 1 Go are shown in Mo`() {
        assertEquals("500 Mo", Units.formatBytes(500_000_000))
        assertEquals("0 Mo", Units.formatBytes(0))
        assertEquals("500 Mo", Units.formatGo(0.5))
        assertEquals("250 Mo", Units.formatGo(0.25))
    }

    @Test fun `BR-6 amounts of 1 Go or more are shown in Go`() {
        assertTrue(Units.formatGo(12.5).endsWith("Go"))
        assertEquals("12,5 Go", Units.formatGo(12.5))
    }

    @Test fun `AC-13 age text in French`() {
        val now = 10_000_000_000L
        assertEquals("mis à jour il y a 4 min", AgeFormatter.updated(now - 4 * 60_000, now))
        assertEquals("il y a 2 h", AgeFormatter.ago(now - 2 * 3_600_000L, now))
    }

    @Test fun `AC-1 PIN must be 4 to 6 digits`() {
        assertFalse(PinPolicy.isValidFormat("123"))
        assertTrue(PinPolicy.isValidFormat("1234"))
        assertTrue(PinPolicy.isValidFormat("12345"))
        assertTrue(PinPolicy.isValidFormat("123456"))
        assertFalse(PinPolicy.isValidFormat("1234567"))
        assertFalse(PinPolicy.isValidFormat("12a4"))
        assertFalse(PinPolicy.isValidFormat(""))
    }

    @Test fun `AC-2 PIN is verified, wrong pin rejected, stored value is not the pin`() {
        val salt = PinPolicy.newSalt()
        val h = PinPolicy.hash("4821", salt)
        assertFalse(h.contains("4821"))
        assertTrue(PinPolicy.verify("4821", salt, h))
        assertFalse(PinPolicy.verify("4822", salt, h))
    }

    @Test fun `AC-2 PIN asked again only after more than 1 minute in background`() {
        val t = 1_000_000L
        assertFalse(PinPolicy.needsUnlock(t, t + 59_000))
        assertFalse(PinPolicy.needsUnlock(t, t + 60_000))
        assertTrue(PinPolicy.needsUnlock(t, t + 60_001))
        assertTrue("cold start must ask the PIN", PinPolicy.needsUnlock(null, t))
    }

    @Test fun `AC-2 5 wrong attempts lock input for 30 seconds`() {
        var st = PinLockState()
        val t = 5_000_000L
        repeat(4) { st = st.afterFailure(t); assertFalse(st.isLocked(t)) }
        st = st.afterFailure(t)
        assertTrue(st.isLocked(t))
        assertTrue(st.isLocked(t + 29_000))
        assertEquals(30, st.remainingLockSeconds(t))
        assertFalse(st.isLocked(t + 30_000))
    }

    @Test fun `AC-2 success resets failure counter`() {
        var st = PinLockState()
        repeat(4) { st = st.afterFailure(1L) }
        st = st.afterSuccess()
        repeat(4) { st = st.afterFailure(2L) }
        assertFalse(st.isLocked(2L))
    }

    private val plan = OrangeSnapshot(0, 12.5, 60.0, LocalDate.of(2026, 12, 14), "P")
    private fun pred(risk: Boolean) = Prediction(5.0, RateSource.ORANGE, 12.5, -137.5, 2.5, 30.0, risk, LocalDate.of(2026, 11, 13), 0.0)

    @Test fun `BR-8 reminder has today usage and remaining Go`() {
        val t = ReminderText.build(1_500_000_000, plan, pred(false), false)
        assertTrue(t, t.contains("1,50 Go"))
        assertTrue(t, t.contains("12,5 Go"))
        assertFalse(t, t.contains("dépassement"))
    }

    @Test fun `BR-8 reminder has at-risk warning when BR-5 applies`() {
        val t = ReminderText.build(1_500_000_000, plan, pred(true), false)
        assertTrue(t, t.contains("13/11"))
    }

    @Test fun `BR-8 reminder today under 1 Go in Mo`() {
        assertTrue(ReminderText.build(300_000_000, plan, null, false).contains("300 Mo"))
    }
}
