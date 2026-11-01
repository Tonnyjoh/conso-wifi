package mg.flybox.usage.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Calcul de l'usage à partir des compteurs cumulés du routeur (BR-2).
 * Reproduit usage_between et daily_usage de conso.py : le delta d'une paire de
 * relevés consécutifs est attribué au jour local du relevé le plus récent. Un compteur
 * qui diminue est une remise à zéro : la nouvelle valeur compte comme delta.
 */
object UsageCalculator {

    /** Delta entre deux relevés successifs, remise à zéro comprise. Jamais négatif. */
    fun delta(previousTotal: Long, currentTotal: Long): Long =
        if (currentTotal >= previousTotal) currentTotal - previousTotal else currentTotal.coerceAtLeast(0)

    /** Somme des deltas sur une suite de relevés triés par temps. */
    fun usageBetween(samples: List<Sample>): Long {
        val sorted = samples.sortedBy { it.ts }
        var total = 0L
        for (i in 1 until sorted.size) total += delta(sorted[i - 1].total, sorted[i].total)
        return total
    }

    /** Usage par jour local (clé : date locale du relevé le plus récent de la paire). */
    fun dailyUsage(samples: List<Sample>, zone: ZoneId): Map<LocalDate, Long> {
        val sorted = samples.sortedBy { it.ts }
        val out = LinkedHashMap<LocalDate, Long>()
        for (i in 1 until sorted.size) {
            val d = delta(sorted[i - 1].total, sorted[i].total)
            val day = Instant.ofEpochMilli(sorted[i].ts).atZone(zone).toLocalDate()
            out[day] = (out[day] ?: 0L) + d
        }
        return out
    }

    /** Usage du jour local de [nowMillis]. */
    fun todayUsage(samples: List<Sample>, nowMillis: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return dailyUsage(samples, zone)[today] ?: 0L
    }

    /** Premier instant (millisecondes UTC) du jour local de [nowMillis]. */
    fun startOfDayMillis(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Série de [days] jours se terminant à aujourd'hui, du plus ancien au plus récent,
     * avec 0 pour les jours sans relevé.
     */
    fun series(daily: Map<LocalDate, Long>, today: LocalDate, days: Int): List<Pair<LocalDate, Long>> =
        (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            d to (daily[d] ?: 0L)
        }
}
