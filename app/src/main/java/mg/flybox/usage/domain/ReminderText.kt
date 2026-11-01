package mg.flybox.usage.domain

import java.time.format.DateTimeFormatter

/** Contenu de la notification quotidienne (BR-8). */
object ReminderText {
    const val TITLE = "Consommation du jour"
    private val dayMonth = DateTimeFormatter.ofPattern("dd/MM")

    fun build(todayBytes: Long?, plan: OrangeSnapshot?, prediction: Prediction?, alwaysGo: Boolean): String {
        val lines = ArrayList<String>()
        lines.add(
            if (todayBytes != null) "Aujourd'hui : ${Units.formatBytes(todayBytes, alwaysGo)}"
            else "Usage du jour indisponible",
        )
        if (plan != null) {
            lines.add("Reste : ${Units.formatGo(plan.remainingGo, alwaysGo)} sur ${Units.formatGo(plan.totalGo, alwaysGo, 0)}")
        } else {
            lines.add("Forfait indisponible")
        }
        if (prediction != null && prediction.atRisk) {
            val d = prediction.emptyDate
            lines.add(if (d != null) "Risque de dépassement : plus de data vers le ${d.format(dayMonth)}." else "Risque de dépassement avant l'expiration.")
        }
        return lines.joinToString("\n")
    }
}
