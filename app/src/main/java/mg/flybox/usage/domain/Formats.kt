package mg.flybox.usage.domain

import java.time.LocalDate

/** Dates et signal en français, sans dépendre des données de langue de l'appareil. */
object Formats {
    private val weekdays = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")
    private val months = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

    fun weekdayShort(d: LocalDate): String = weekdays[d.dayOfWeek.value - 1]

    /** "28 oct." */
    fun dayMonth(d: LocalDate): String = "${d.dayOfMonth} ${months[d.monthValue - 1]}"

    /** "mar. 28 oct." (utilisé dans la bulle des barres). */
    fun bubbleDate(d: LocalDate): String = "${weekdayShort(d)} ${dayMonth(d)}"

    /** "14/12/2026" */
    fun numericDate(d: LocalDate): String = "%02d/%02d/%04d".format(d.dayOfMonth, d.monthValue, d.year)

    /** "14/12" */
    fun shortNumericDate(d: LocalDate): String = "%02d/%02d".format(d.dayOfMonth, d.monthValue)

    /** "1 h 02" ou "12 min". */
    fun duration(seconds: Long): String {
        val m = seconds / 60
        return if (m < 60) "$m min" else "${m / 60} h ${"%02d".format(m % 60)}"
    }
}

/** Niveau de signal d'après le RSRP (dBm) : 0 à 4. */
object Signal {
    fun level(rsrp: Int?): Int = when {
        rsrp == null -> 0
        rsrp >= -85 -> 4
        rsrp >= -95 -> 3
        rsrp >= -105 -> 2
        else -> 1
    }

    fun word(level: Int): String = when (level) {
        4 -> "excellent"
        3 -> "bon"
        2 -> "moyen"
        1 -> "faible"
        else -> "inconnu"
    }

    fun networkLabel(type: String?): String = when (type?.uppercase()) {
        null, "" -> "Réseau"
        "LTE", "4G", "LTE_A" -> "4G"
        "NR", "5G", "NSA", "SA" -> "5G"
        "WCDMA", "HSPA", "HSPA+", "UMTS", "3G" -> "3G"
        "GSM", "EDGE", "GPRS", "2G" -> "2G"
        else -> type
    }

    fun chipText(type: String?, rsrp: Int?): String {
        val net = networkLabel(type)
        return if (rsrp == null) net else "$net, signal ${word(level(rsrp))}"
    }
}
