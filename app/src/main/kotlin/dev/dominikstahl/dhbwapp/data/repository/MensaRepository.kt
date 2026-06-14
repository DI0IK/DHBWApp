package dev.dominikstahl.dhbwapp.data.repository

import dev.dominikstahl.dhbwapp.data.local.db.CachedMensaMenuDay
import dev.dominikstahl.dhbwapp.data.local.db.MensaDao
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.MensaResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MensaRepository(
    private val apiClient: ApiClient,
    private val mensaDao: MensaDao
) {
    fun getMensaMenu(site: String): Flow<List<MensaResponse>> {
        return mensaDao.getMensaMenuForSite(site).map { cachedList ->
            CachedMensaMenuDay.toMensaResponseList(cachedList)
        }
    }

    suspend fun syncMensaMenu(site: String) {
        if (site.isBlank()) return
        val remoteMenu = apiClient.getMensaMenu(site)
        val cached = remoteMenu.flatMap { CachedMensaMenuDay.fromMensaResponse(it) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            mensaDao.refreshMensaMenuForSite(site, cached)
        }
    }
}
