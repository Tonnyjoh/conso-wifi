package mg.flybox.usage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineGroupingTest {
    private fun k(name: String, mac: String) = KnownDevice(name, mac, "", false)
    private fun on(name: String) = OnlineDevice(name, "1.2.3.4", "AA", 10)

    @Test fun sameNameDifferentMacIsGrouped() {
        val g = OfflineGrouping.group(listOf(k("PC", "A1"), k("PC", "A2"), k("TV", "B1")), emptyList())
        assertEquals(2, g.size)
        assertEquals(2, g[0].addressCount)
        assertEquals("A1", g[0].representative.mac)
        assertEquals(1, g[1].addressCount)
    }

    @Test fun caseAndSpacesAreNormalised() {
        val g = OfflineGrouping.group(listOf(k("Tonny-PC", "A1"), k("  tonny-pc ", "A2")), emptyList())
        assertEquals(1, g.size)
        assertEquals(2, g[0].addressCount)
    }

    @Test fun onlineNameHidesOfflineDuplicates() {
        val g = OfflineGrouping.group(listOf(k("Phone", "A1"), k("phone ", "A2"), k("TV", "B1")), listOf(on("PHONE")))
        assertEquals(listOf("TV"), g.map { it.name })
    }

    @Test fun emptyNamesStaySeparateByMac() {
        val g = OfflineGrouping.group(listOf(k("", "A1"), k("  ", "A2"), k(OfflineGrouping.UNKNOWN_NAME, "A3"), k("", "A1")), emptyList())
        assertEquals(3, g.size)
        assertEquals(2, g.first { it.representative.mac == "A1" }.addressCount)
    }

    @Test fun orderIsStable() {
        val g = OfflineGrouping.group(listOf(k("B", "1"), k("A", "2"), k("B", "3"), k("C", "4")), emptyList())
        assertEquals(listOf("B", "A", "C"), g.map { it.name })
    }

    @Test fun emptyInput() = assertTrue(OfflineGrouping.group(emptyList(), emptyList()).isEmpty())
}
