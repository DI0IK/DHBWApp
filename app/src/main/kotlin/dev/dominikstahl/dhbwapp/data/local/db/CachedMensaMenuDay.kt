package dev.dominikstahl.dhbwapp.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.dominikstahl.dhbwapp.remote.models.MenuDay
import dev.dominikstahl.dhbwapp.remote.models.MenuItem
import dev.dominikstahl.dhbwapp.remote.models.MensaInfo
import dev.dominikstahl.dhbwapp.remote.models.MensaResponse

@Entity(tableName = "mensa_menu_days")
data class CachedMensaMenuDay(
    @PrimaryKey
    val id: Int,
    val site: String,
    val date: String,
    val closed: Boolean,
    val starters: List<MenuItem>,
    val mainCourses: List<MenuItem>,
    val desserts: List<MenuItem>,
    
    // Embedded MensaInfo fields to reconstruct MensaResponse
    val mensaId: Int,
    val mensaName: String,
    val mensaActive: Boolean,
    val mensaAddress: String?,
    val mensaOpeningHours: String?,
    val mensaInfoUrl: String?,
    val mensaMenuUrl: String?
) {
    fun toMenuDay(): MenuDay {
        return MenuDay(
            id = id,
            site = site,
            date = date,
            closed = closed,
            starters = starters,
            mainCourses = mainCourses,
            desserts = desserts
        )
    }

    fun toMensaInfo(): MensaInfo {
        return MensaInfo(
            id = mensaId,
            site = site,
            name = mensaName,
            active = mensaActive,
            address = mensaAddress,
            openingHours = mensaOpeningHours,
            infoUrl = mensaInfoUrl,
            menuUrl = mensaMenuUrl
        )
    }

    companion object {
        fun fromMensaResponse(response: MensaResponse): List<CachedMensaMenuDay> {
            val info = response.mensaInfo
            return response.menus.map { menu ->
                CachedMensaMenuDay(
                    id = menu.id,
                    site = menu.site,
                    date = menu.date,
                    closed = menu.closed,
                    starters = menu.starters ?: emptyList(),
                    mainCourses = menu.mainCourses ?: emptyList(),
                    desserts = menu.desserts ?: emptyList(),
                    mensaId = info.id,
                    mensaName = info.name,
                    mensaActive = info.active,
                    mensaAddress = info.address,
                    mensaOpeningHours = info.openingHours,
                    mensaInfoUrl = info.infoUrl,
                    mensaMenuUrl = info.menuUrl
                )
            }
        }

        fun toMensaResponseList(cached: List<CachedMensaMenuDay>): List<MensaResponse> {
            if (cached.isEmpty()) return emptyList()
            // Group by site / mensaId
            val grouped = cached.groupBy { it.mensaId }
            return grouped.map { (_, days) ->
                val first = days.first()
                MensaResponse(
                    lastUpdate = "", // Not strictly used in UI
                    mensaInfo = first.toMensaInfo(),
                    menus = days.map { it.toMenuDay() }
                )
            }
        }
    }
}
