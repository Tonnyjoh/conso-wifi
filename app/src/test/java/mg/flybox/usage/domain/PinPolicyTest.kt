package mg.flybox.usage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinPolicyTest {
    @Test fun formatIsFourToSixDigits() {
        assertTrue(PinPolicy.isValidFormat("1234"))
        assertTrue(PinPolicy.isValidFormat("123456"))
        assertFalse(PinPolicy.isValidFormat("123"))
        assertFalse(PinPolicy.isValidFormat("1234567"))
        assertFalse(PinPolicy.isValidFormat("12a4"))
    }

    @Test fun hashVerifies() {
        val salt = PinPolicy.newSalt()
        val h = PinPolicy.hash("4821", salt, 1000)
        assertTrue(PinPolicy.verify("4821", salt, h, 1000))
        assertFalse(PinPolicy.verify("4822", salt, h, 1000))
    }

    @Test fun lockAfterFiveFailuresForThirtySeconds() {
        var st = PinLockState()
        repeat(4) { st = st.afterFailure(1_000) }
        assertFalse(st.isLocked(1_000))
        st = st.afterFailure(1_000)
        assertTrue(st.isLocked(1_001))
        assertEquals(30, st.remainingLockSeconds(1_000))
        assertEquals(29, st.remainingLockSeconds(2_500))
        assertEquals(28, st.remainingLockSeconds(3_000))
        assertFalse(st.isLocked(31_000))
        assertEquals(0, st.failures)
    }

    @Test fun unlockRequiredAfterMoreThanOneMinute() {
        assertFalse(PinPolicy.needsUnlock(100_000, 100_000 + 60_000))
        assertTrue(PinPolicy.needsUnlock(100_000, 100_000 + 60_001))
        assertTrue(PinPolicy.needsUnlock(null, 5))
    }
}
