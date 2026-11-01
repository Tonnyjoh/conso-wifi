package mg.flybox.usage.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {
    @Test fun bubbleDate() = assertEquals("mar. 28 oct.", Formats.bubbleDate(LocalDate.of(2025, 10, 28)))

    @Test fun numericDates() {
        assertEquals("04/03/2027", Formats.numericDate(LocalDate.of(2027, 3, 4)))
        assertEquals("04/03", Formats.shortNumericDate(LocalDate.of(2027, 3, 4)))
    }

    @Test fun duration() {
        assertEquals("12 min", Formats.duration(750))
        assertEquals("1 h 02", Formats.duration(3725))
    }

    @Test fun signal() {
        assertEquals(1, Signal.level(-100 - 10))
        assertEquals(2, Signal.level(-100))
        assertEquals("4G, signal moyen", Signal.chipText("LTE", -100))
        assertEquals("4G", Signal.chipText("LTE", null))
    }
}
