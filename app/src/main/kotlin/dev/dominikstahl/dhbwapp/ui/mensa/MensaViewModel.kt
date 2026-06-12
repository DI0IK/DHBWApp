package dev.dominikstahl.dhbwapp.ui.mensa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.MenuDay
import dev.dominikstahl.dhbwapp.remote.models.MensaResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class MensaUiState(
    val menus: List<MensaResponse> = emptyList(),
    val selectedDayIndex: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

class MensaViewModel(
    private val apiClient: ApiClient,
    private var site: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MensaUiState())
    val uiState: StateFlow<MensaUiState> = _uiState

    private var currentMenuDays: List<MenuDay> = emptyList()

    fun setSite(newSite: String) {
        site = newSite
    }

    fun loadMenus() {
        viewModelScope.launch {
            _uiState.value = MensaUiState(loading = true)
            try {
                val response = apiClient.getMensaMenu(site)
                currentMenuDays = response.firstOrNull()?.menus ?: emptyList()
                val todayIndex = findTodayIndex(currentMenuDays)
                _uiState.value = MensaUiState(menus = response, selectedDayIndex = todayIndex)
            } catch (e: Exception) {
                _uiState.value = MensaUiState(
                    error = e.message ?: "Fehler beim Laden der Menüs",
                )
            }
        }
    }

    fun previousDay() {
        val i = _uiState.value.selectedDayIndex
        if (i > 0) {
            _uiState.value = _uiState.value.copy(selectedDayIndex = i - 1)
        }
    }

    fun nextDay() {
        val i = _uiState.value.selectedDayIndex
        if (i < currentMenuDays.lastIndex) {
            _uiState.value = _uiState.value.copy(selectedDayIndex = i + 1)
        }
    }

    private fun findTodayIndex(days: List<MenuDay>): Int {
        if (days.isEmpty()) return 0
        val today = LocalDate.now()
        return days.indexOfFirst {
            apiDateToLocalDate(it.date) == today
        }.coerceAtLeast(0)
    }

    companion object {
        private val berlinZone = ZoneId.of("Europe/Berlin")

        fun apiDateToLocalDate(dateString: String): LocalDate? {
            return try {
                OffsetDateTime.parse(dateString).atZoneSameInstant(berlinZone).toLocalDate()
            } catch (_: Exception) {
                try {
                    LocalDate.parse(dateString.substringBefore("T"))
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    class Factory(
        private val apiClient: ApiClient,
        private val site: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MensaViewModel(apiClient, site) as T
        }
    }
}
