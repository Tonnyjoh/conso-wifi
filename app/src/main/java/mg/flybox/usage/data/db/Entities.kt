package mg.flybox.usage.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import mg.flybox.usage.domain.OrangeSnapshot
import mg.flybox.usage.domain.Sample

@Entity(tableName = "samples")
data class SampleEntity(
    @PrimaryKey val ts: Long,
    val down: Long,
    val up: Long,
    @ColumnInfo(name = "down_rate") val downRate: Long,
    @ColumnInfo(name = "up_rate") val upRate: Long,
    @ColumnInfo(name = "online_time") val onlineTime: Long,
    val net: String?,
    val rsrp: Int?,
    val rssi: Int?,
    val sinr: Double?,
) {
    fun toDomain() = Sample(ts, down, up, downRate, upRate, onlineTime, net, rsrp, rssi, sinr)

    companion object {
        fun from(s: Sample) = SampleEntity(s.ts, s.down, s.up, s.downRate, s.upRate, s.onlineTime, s.network, s.rsrp, s.rssi, s.sinr)
    }
}

@Entity(tableName = "orange")
data class OrangeEntity(
    @PrimaryKey val ts: Long,
    @ColumnInfo(name = "remaining_go") val remainingGo: Double,
    @ColumnInfo(name = "total_go") val totalGo: Double,
    /** Date d'expiration au format ISO yyyy-MM-dd. */
    val expires: String,
    val plan: String,
    @ColumnInfo(name = "balance_ar") val balanceAr: Long?,
) {
    fun toDomain() = OrangeSnapshot(ts, remainingGo, totalGo, LocalDate.parse(expires), plan, balanceAr)

    companion object {
        fun from(o: OrangeSnapshot) = OrangeEntity(o.ts, o.remainingGo, o.totalGo, o.expires.toString(), o.plan, o.balanceAr)
    }
}
