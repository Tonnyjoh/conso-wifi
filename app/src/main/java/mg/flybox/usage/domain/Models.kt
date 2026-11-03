package mg.flybox.usage.domain

import java.time.LocalDate

/** Un relevé des compteurs du routeur (BR-2). Les horodatages sont en UTC (millisecondes epoch). */
data class Sample(
    val ts: Long,
    val down: Long,
    val up: Long,
    val downRate: Long = 0,
    val upRate: Long = 0,
    val onlineTime: Long = 0,
    val network: String? = null,
    val rsrp: Int? = null,
    val rssi: Int? = null,
    val sinr: Double? = null,
) {
    val total: Long get() = down + up
}

/** Un relevé de la page Orange (BR-3). */
data class OrangeSnapshot(
    val ts: Long,
    val remainingGo: Double,
    val totalGo: Double,
    val expires: LocalDate,
    val plan: String,
    val balanceAr: Long? = null,
)

/** Résultat de l'analyse de la page Orange. */
data class OrangePlan(
    val remainingGo: Double,
    val totalGo: Double,
    val expires: LocalDate,
    val plan: String,
    val balanceAr: Long?,
)

/** Réponse de system_status, déjà typée. Les champs vides deviennent null (EC-1). */
data class RouterStatus(
    val downBytes: Long?,
    val upBytes: Long?,
    val downRate: Long?,
    val upRate: Long?,
    val onlineTime: Long?,
    val networkType: String?,
    val rsrp: Int?,
    val rssi: Int?,
    val sinr: Double?,
    val plmn: String?,
) {
    /** Vrai si les deux compteurs cumulés sont présents. */
    val hasCounters: Boolean get() = downBytes != null && upBytes != null
}

data class OnlineDevice(
    val name: String,
    val ip: String,
    val mac: String,
    val connectedSeconds: Long?,
    /** Faux si l'interrupteur Internet de l'appareil est coupé sur le routeur (appareil bloqué). */
    val internetEnabled: Boolean = true,
)

data class KnownDevice(
    val name: String,
    val mac: String,
    val ip: String,
    val online: Boolean,
    val internetEnabled: Boolean = true,
)

/** Trafic par appareil (AC-17). Le total est en octets. */
data class DeviceTraffic(
    val mac: String?,
    val name: String?,
    val ip: String?,
    val totalBytes: Long,
)

data class DeviceLists(
    val online: List<OnlineDevice>,
    val offline: List<KnownDevice>,
)
