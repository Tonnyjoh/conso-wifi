package mg.flybox.usage.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Origine du rythme quotidien (BR-4). */
enum class RateSource(val label: String) {
    ORANGE("forfait Orange"),
    ROUTER("compteur routeur"),
}

data class Rate(val goPerDay: Double, val source: RateSource)

data class Prediction(
    val rateGoPerDay: Double,
    val source: RateSource,
    val remainingGo: Double,
    /** Solde prévu dans 30 jours (peut être négatif, à borner à l'affichage). */
    val predRemaining30dGo: Double,
    val daysUntilEmpty: Double,
    val daysUntilExpiry: Double,
    /** Vrai si le solde sera épuisé avant l'expiration (BR-5). */
    val atRisk: Boolean,
    /** Date d'épuisement, null si au-delà de 10 ans. */
    val emptyDate: LocalDate?,
    /** Solde prévu à l'expiration, borné à 0. */
    val remainingAtExpiryGo: Double,
)

/**
 * Taux et prédiction (BR-4, BR-5), identiques à stats() de conso.py.
 */
object PredictionEngine {
    const val MIN_HISTORY_MILLIS: Long = 6L * 3600 * 1000
    private const val MIN_SPAN_DAYS = 0.25
    private const val DAY_MILLIS = 86_400_000.0
    private const val TOLERANCE_GO = 0.001

    /**
     * Rythme depuis l'historique Orange : dernière décroissance sans recharge.
     * Une hausse du solde marque un nouveau forfait et redémarre la fenêtre.
     * Retourne null si moins de 6 h d'historique ou rien consommé.
     */
    fun orangeRate(history: List<OrangeSnapshot>): Double? {
        val h = history.sortedBy { it.ts }
        if (h.size < 2 || h.last().ts - h.first().ts < MIN_HISTORY_MILLIS) return null
        val seg = ArrayDeque<OrangeSnapshot>()
        seg.addLast(h.last())
        for (i in h.size - 2 downTo 0) {
            if (h[i].remainingGo < seg.first().remainingGo - TOLERANCE_GO) break
            seg.addFirst(h[i])
        }
        val spanDays = (seg.last().ts - seg.first().ts) / DAY_MILLIS
        val used = seg.first().remainingGo - seg.last().remainingGo
        return if (spanDays >= MIN_SPAN_DAYS && used > 0) used / spanDays else null
    }

    /**
     * Rythme de repli depuis les compteurs du routeur sur la durée observée.
     * [daily] est l'usage par jour sur la période, [samples] sert à mesurer la durée.
     */
    fun routerRate(samples: List<Sample>, zone: ZoneId): Double? {
        if (samples.size < 2) return null
        val first = samples.minOf { it.ts }
        val last = samples.maxOf { it.ts }
        if (last - first < MIN_HISTORY_MILLIS) return null
        val spanDays = (last - first) / DAY_MILLIS
        val totalBytes = UsageCalculator.dailyUsage(samples, zone).values.sum()
        return totalBytes / Units.GO / spanDays
    }

    /** Rythme retenu : Orange en priorité, sinon routeur (BR-4). Null sous 6 h d'historique. */
    fun rate(orangeHistory: List<OrangeSnapshot>, samples: List<Sample>, zone: ZoneId): Rate? {
        orangeRate(orangeHistory)?.let { return Rate(it, RateSource.ORANGE) }
        routerRate(samples, zone)?.let { return Rate(it, RateSource.ROUTER) }
        return null
    }

    /**
     * Prédiction (BR-5). Comme conso.py, un rythme nul ou absent ne donne aucune prédiction.
     * L'expiration est à 23:59:59 le jour indiqué, soit le début du jour suivant.
     */
    fun predict(rate: Rate?, remainingGo: Double, expires: LocalDate, nowMillis: Long, zone: ZoneId): Prediction? {
        if (rate == null || rate.goPerDay <= 0.0) return null
        val r = rate.goPerDay
        val daysLeft = remainingGo / r
        val expiryMillis = expires.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val daysToExpiry = (expiryMillis - nowMillis) / DAY_MILLIS
        val emptyDate = if (daysLeft in 0.0..3650.0) {
            Instant.ofEpochMilli(nowMillis + (daysLeft * DAY_MILLIS).toLong()).atZone(zone).toLocalDate()
        } else null
        return Prediction(
            rateGoPerDay = r,
            source = rate.source,
            remainingGo = remainingGo,
            predRemaining30dGo = remainingGo - 30 * r,
            daysUntilEmpty = daysLeft,
            daysUntilExpiry = daysToExpiry,
            atRisk = daysLeft < daysToExpiry,
            emptyDate = emptyDate,
            remainingAtExpiryGo = (remainingGo - r * daysToExpiry).coerceAtLeast(0.0),
        )
    }
}
