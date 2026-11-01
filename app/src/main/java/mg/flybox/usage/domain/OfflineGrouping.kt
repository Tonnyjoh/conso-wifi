package mg.flybox.usage.domain

/** Un appareil hors ligne, éventuellement regroupant plusieurs adresses MAC portant le même nom. */
data class OfflineGroup(val name: String, val addressCount: Int, val representative: KnownDevice)

object OfflineGrouping {
    /** Nom affiché par le parseur quand le routeur n'en fournit pas. */
    const val UNKNOWN_NAME = "Appareil inconnu"

    private fun key(name: String, mac: String): String {
        val n = name.trim().lowercase()
        return if (n.isEmpty() || name.trim() == UNKNOWN_NAME) "mac:" + mac.trim().uppercase() else "name:$n"
    }

    /**
     * Regroupe les appareils hors ligne par nom normalisé (espaces et casse ignorés). Un nom vide
     * ou inconnu reste seul (clé : adresse MAC). Un nom actuellement en ligne masque ses doublons
     * hors ligne. L'ordre du routeur est conservé : un groupe prend la place de sa première entrée,
     * dont le détail sert de représentant.
     */
    fun group(offline: List<KnownDevice>, online: List<OnlineDevice>): List<OfflineGroup> {
        val onlineNames = online.map { it.name.trim().lowercase() }
            .filter { it.isNotEmpty() && it != UNKNOWN_NAME.lowercase() }.toSet()
        val order = LinkedHashMap<String, MutableList<KnownDevice>>()
        for (d in offline) {
            val n = d.name.trim().lowercase()
            if (n in onlineNames) continue
            order.getOrPut(key(d.name, d.mac)) { ArrayList() }.add(d)
        }
        return order.values.map { g -> OfflineGroup(g.first().name.trim(), g.size, g.first()) }
    }
}
