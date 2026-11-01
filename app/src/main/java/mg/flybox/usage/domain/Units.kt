package mg.flybox.usage.domain

import java.util.Locale

/** Unités décimales (BR-6) : 1 Go = 10^9 octets, 1 Mo = 10^6 octets. */
object Units {
    const val GO: Double = 1_000_000_000.0
    const val MO: Double = 1_000_000.0

    private val fr: Locale = Locale.FRANCE

    private fun num(v: Double, decimals: Int): String = String.format(fr, "%.${decimals}f", v)

    /** Octets vers texte. Sous 1 Go, en Mo (sauf si [alwaysGo]). */
    fun formatBytes(bytes: Long, alwaysGo: Boolean = false, goDecimals: Int = 2): String {
        val b = if (bytes < 0) 0L else bytes
        return if (b >= GO || alwaysGo) {
            num(b / GO, goDecimals) + " Go"
        } else {
            num(b / MO, 0) + " Mo"
        }
    }

    /** Valeur en Go (flottant) vers texte. Sous 1 Go, en Mo (sauf si [alwaysGo]). */
    fun formatGo(go: Double, alwaysGo: Boolean = false, decimals: Int = 1): String {
        val g = if (go < 0) 0.0 else go
        return if (g >= 1.0 || alwaysGo) {
            num(g, decimals) + " Go"
        } else {
            num(g * 1000.0, 0) + " Mo"
        }
    }

    /** Valeur seule et unité séparées, pour l'affichage héros de l'anneau. */
    fun splitGo(go: Double, alwaysGo: Boolean = false, decimals: Int = 1): Pair<String, String> {
        val g = if (go < 0) 0.0 else go
        return if (g >= 1.0 || alwaysGo) num(g, decimals) to "Go" else num(g * 1000.0, 0) to "Mo"
    }

    /** Débit en octets par seconde. */
    fun formatRate(bytesPerSecond: Double): String {
        val v = if (bytesPerSecond < 0) 0.0 else bytesPerSecond
        return if (v >= MO) num(v / MO, 1) + " Mo/s" else num(v / 1000.0, 0) + " Ko/s"
    }
}
