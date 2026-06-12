package dev.dominikstahl.dhbwapp.ui.parking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.ParkingLot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ParkingUiState(
    val parkingLots: List<ParkingLot> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class ParkingViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParkingUiState())
    val uiState: StateFlow<ParkingUiState> = _uiState

    fun loadParking(site: String) {
        viewModelScope.launch {
            _uiState.value = ParkingUiState(loading = true)
            try {
                val all = apiClient.getParking()
                val filtered = all.filter { it.site == site }
                _uiState.value = ParkingUiState(parkingLots = filtered)
            } catch (e: Exception) {
                _uiState.value = ParkingUiState(
                    error = e.message ?: "Fehler beim Laden der Parkdaten",
                )
            }
        }
    }

    class Factory(
        private val apiClient: ApiClient,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ParkingViewModel(apiClient) as T
        }
    }
}
