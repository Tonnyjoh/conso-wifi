package mg.flybox.usage.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Analyse de la page de consommation Orange (BR-3), équivalent de collect_orange de conso.py.
 * Plusieurs forfaits : restant et total sont additionnés, la plus proche expiration est retenue.
 */
object OrangeParser {
    private val scripts = Regex("<(script|style)[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    private val tags = Regex("<[^>]+>")
    private val spaces = Regex("\\s+")

    private const val APOS = "['’‘`]"
    private val plan = Regex(
        "([\\d.,]+)\\s*Go sur\\s*([\\d.,]+)\\s*Go valable jusqu${APOS}au (\\d+/\\d+/\\d+)",
    )
    private val balance = Regex(
        "solde de votre compte de rechargement est de\\s*([\\d][\\d\\s.,\\u00a0\\u202f]*?)\\s*Ar",
        RegexOption.IGNORE_CASE,
    )
    private val dateFormat = DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT)

    /** Retourne null si le texte du forfait est introuvable (EC-2). */
    fun parse(html: String): OrangePlan? {
        val text = flatten(html)
        val rows = plan.findAll(text).toList()
        if (rows.isEmpty()) return null
        var remaining = 0.0
        var total = 0.0
        var earliest: LocalDate? = null
        val names = ArrayList<String>()
        var prevEnd = 0
        for (m in rows) {
            val rem = toDouble(m.groupValues[1]) ?: return null
            val tot = toDouble(m.groupValues[2]) ?: return null
            val date = runCatching { LocalDate.parse(m.groupValues[3], dateFormat) }.getOrNull() ?: return null
            remaining += rem
            total += tot
            if (earliest == null || date.isBefore(earliest)) earliest = date
            names.add(planName(text.substring(prevEnd, m.range.first)))
            prevEnd = m.range.last + 1
        }
        return OrangePlan(
            remainingGo = remaining,
            totalGo = total,
            expires = earliest!!,
            plan = names.joinToString(" + "),
            balanceAr = parseBalance(text),
        )
    }

    /** Nom du forfait : texte qui précède les chiffres, sans le titre "Forfaits en cours de validité". */
    private fun planName(before: String): String {
        var seg = before.replace(Regex("^\\s*(?:\u00e0|a)?\\s*\\d{1,2}\\s*(?:[:h]\\s*\\d{1,2}(?:\\s*(?:[:m]|min)\\s*\\d{1,2}\\s*s?)?)?(?=\\s)"), "")
        val v = seg.lastIndexOf("validit\u00e9", ignoreCase = true)
        seg = if (v >= 0) seg.substring(v + "validit\u00e9".length) else seg.substring(seg.indexOfLast { it in ".!?:;|" } + 1)
        seg = seg.replace(Regex("[^\\p{L}\\p{N}_+ ]"), " ").trim().replace(Regex("\\s+Internet$", RegexOption.IGNORE_CASE), "").trim()
        val words = seg.split(" ").filter { it.isNotEmpty() }
        return if (words.isEmpty()) "Forfait" else words.takeLast(4).joinToString(" ")
    }

    /** Solde du compte de rechargement en Ariary, null si absent. */
    fun parseBalance(text: String): Long? {
        val m = balance.find(text) ?: return null
        val raw = m.groupValues[1].replace(Regex("[\\s\\u00a0\\u202f]"), "")
        // La partie décimale éventuelle est ignorée : "12.500,50" ou "12 500".
        val integer = raw.substringBefore(',').replace(".", "")
        return integer.toLongOrNull()
    }

    fun flatten(html: String): String {
        var t = scripts.replace(html, "")
        t = tags.replace(t, " ")
        t = spaces.replace(t, " ")
        return decodeEntities(t).replace(' ', ' ').let { spaces.replace(it, " ") }
    }

    private fun toDouble(s: String): Double? = s.replace(",", ".").toDoubleOrNull()

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "euml" to "ë",
        "agrave" to "à", "acirc" to "â", "ocirc" to "ô", "ucirc" to "û",
        "ugrave" to "ù", "icirc" to "î", "iuml" to "ï", "ccedil" to "ç",
        "Eacute" to "É", "rsquo" to "’", "lsquo" to "‘", "laquo" to "«", "raquo" to "»",
    )
    private val entity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z]+);")

    fun decodeEntities(s: String): String = entity.replace(s) { m ->
        val g = m.groupValues[1]
        when {
            g.startsWith("#x") || g.startsWith("#X") -> g.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            g.startsWith("#") -> g.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> named[g]
        } ?: m.value
    }
}
