package mg.flybox.usage.ui

import mg.flybox.usage.ui.components.monotoneTangents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonotoneTest {
    @Test fun flatSegmentsHaveZeroSlopeAndNoOvershoot() {
        val x = doubleArrayOf(0.0, 1.0, 2.0, 3.0)
        val y = doubleArrayOf(0.0, 0.0, 5.0, 5.0)
        val m = monotoneTangents(x, y)
        assertEquals(0.0, m[0], 1e-9)
        assertEquals(0.0, m[1], 1e-9)
        assertEquals(0.0, m[3], 1e-9)
        assertTrue(m[2] >= 0.0)
    }

    @Test fun extremumGetsZeroSlope() {
        val m = monotoneTangents(doubleArrayOf(0.0, 1.0, 2.0), doubleArrayOf(0.0, 4.0, 0.0))
        assertEquals(0.0, m[1], 1e-9)
    }
}
