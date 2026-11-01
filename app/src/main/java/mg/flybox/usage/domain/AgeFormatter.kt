package mg.flybox.usage.domain

/** "mis à jour il y a 4 min" : texte d'âge d'un relevé. */
object AgeFormatter {
    fun ago(thenMillis: Long, nowMillis: Long): String {
        val minutes = ((nowMillis - thenMillis).coerceAtLeast(0)) / 60_000
        return when {
            minutes < 1 -> "à l'instant"
            minutes < 60 -> "il y a $minutes min"
            minutes < 24 * 60 -> "il y a ${minutes / 60} h"
            else -> "il y a ${minutes / (24 * 60)} j"
        }
    }

    fun updated(thenMillis: Long, nowMillis: Long): String = "mis à jour ${ago(thenMillis, nowMillis)}"

    fun isStale(thenMillis: Long, nowMillis: Long, hours: Long = 24): Boolean = nowMillis - thenMillis > hours * 3_600_000
}
