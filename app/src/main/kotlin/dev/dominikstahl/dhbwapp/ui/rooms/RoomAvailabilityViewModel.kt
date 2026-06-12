package dev.dominikstahl.dhbwapp.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.RoomAvailabilityResponse
import dev.dominikstahl.dhbwapp.remote.models.RoomInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Immutable
data class StableRoom(
    val name: String,
    val isFree: Boolean,
    val occupancyLine1: String? = null,
    val occupancyLine2: String? = null
)

data class RoomsUiState(
    val response: RoomAvailabilityResponse? = null,
    val roomList: List<StableRoom> = emptyList(),
    val selectedDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val loading: Boolean = false,
    val error: String? = null,
)

class RoomAvailabilityViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomsUiState())
    val uiState: StateFlow<RoomsUiState> = _uiState

    fun loadRooms(site: String) {
        val date = _uiState.value.selectedDate
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val response = apiClient.getRoomAvailability(site, date)
                val deduplicatedRooms = deduplicateRooms(response.rooms)
                val stableRooms = deduplicatedRooms.map { room ->
                    val isFree = room.status == "free"
                    val occupancyLines = if (!isFree && room.occupancies != null) {
                        room.occupancies.take(2).map { occ ->
                            "${occ.lectureName?.trim() ?: ""} ${occ.lecturer?.let { "- $it" } ?: ""}".trim()
                        }
                    } else {
                        emptyList()
                    }
                    StableRoom(
                        name = room.name,
                        isFree = isFree,
                        occupancyLine1 = occupancyLines.getOrNull(0),
                        occupancyLine2 = occupancyLines.getOrNull(1)
                    )
                }
                val total = stableRooms.size
                val free = stableRooms.count { it.isFree }
                val occupied = total - free
                val updatedStats = dev.dominikstahl.dhbwapp.remote.models.RoomAvailabilityResponseStats(
                    total = total,
                    free = free,
                    occupied = occupied
                )
                val updatedResponse = response.copy(stats = updatedStats)

                _uiState.value = _uiState.value.copy(
                    response = updatedResponse,
                    roomList = stableRooms,
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Fehler beim Laden der Raumdaten",
                )
            }
        }
    }

    internal fun deduplicateRooms(rooms: List<RoomInfo>): List<RoomInfo> {
        val sortedRooms = rooms.sortedBy { it.name.length }
        val resolvedNames = mutableMapOf<String, String>()
        val separators = listOf(' ', '-', '/', '_', '(')

        for (room in sortedRooms) {
            val name = room.name
            val baseName = resolvedNames.values.firstOrNull { base ->
                name.length > base.length &&
                name.startsWith(base) &&
                name[base.length] in separators
            }
            resolvedNames[name] = baseName ?: name
        }

        return rooms
            .groupBy { resolvedNames[it.name] ?: it.name }
            .map { (_, group) ->
                val primaryRoom = group.minByOrNull { it.name.length } ?: group.first()
                val mergedStatus = if (group.any { it.status != "free" }) "occupied" else "free"
                val mergedOccupancies = group.flatMap { it.occupancies ?: emptyList() }
                    .distinct()
                    .sortedBy { it.startTime }

                RoomInfo(
                    name = primaryRoom.name,
                    status = mergedStatus,
                    occupancies = mergedOccupancies.ifEmpty { null }
                )
            }
    }

    class Factory(
        private val apiClient: ApiClient,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RoomAvailabilityViewModel(apiClient) as T
        }
    }
}
