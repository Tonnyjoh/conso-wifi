package mg.flybox.usage.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Tout ce que l'accueil et le rappel affichent, calculé depuis l'historique stocké. */
data class DashboardData(
    val lastSample: Sample?,
    val plan: OrangeSnapshot?,
    val todayBytes: Long,
    /** Moyenne des jours précédents ayant des données (7 derniers jours), null s'il n'y en a pas. */
    val averageDailyBytes: Long?,
    val rate: Rate?,
    val prediction: Prediction?,
    val daily: Map<LocalDate, Long>,
    /** Durée d'historique de relevés routeur en millisecondes. */
    val historySpanMillis: Long,
) {
    val isEmpty: Boolean get() = lastSample == null && plan == null
    val collecting: Boolean get() = prediction == null
}

object DashboardCalculator {
    /** Fenêtre de relevés routeur lue pour les calculs, en jours. */
    const val SAMPLE_WINDOW_DAYS = 31L
    const val ORANGE_WINDOW_DAYS = 14L

    fun sampleWindowStart(nowMillis: Long, zone: ZoneId): Long =
        UsageCalculator.startOfDayMillis(nowMillis - SAMPLE_WINDOW_DAYS * 86_400_000L, zone)

    fun orangeWindowStart(nowMillis: Long): Long = nowMillis - ORANGE_WINDOW_DAYS * 86_400_000L

    fun build(samples: List<Sample>, orangeHistory: List<OrangeSnapshot>, nowMillis: Long, zone: ZoneId): DashboardData {
        val sorted = samples.sortedBy { it.ts }
        val daily = UsageCalculator.dailyUsage(sorted, zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val plan = orangeHistory.maxByOrNull { it.ts }
        val rate = PredictionEngine.rate(orangeHistory, sorted, zone)
        val prediction = if (plan != null) PredictionEngine.predict(rate, plan.remainingGo, plan.expires, nowMillis, zone) else null
        val previous = (1..7).mapNotNull { daily[today.minusDays(it.toLong())] }.filter { it > 0 }
        return DashboardData(
            lastSample = sorted.lastOrNull(),
            plan = plan,
            todayBytes = daily[today] ?: 0L,
            averageDailyBytes = if (previous.isEmpty()) null else previous.average().toLong(),
            rate = rate,
            prediction = prediction,
            daily = daily,
            historySpanMillis = if (sorted.size < 2) 0L else sorted.last().ts - sorted.first().ts,
        )
    }
}
