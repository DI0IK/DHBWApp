package dev.dominikstahl.dhbwapp.ui.directory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import dev.dominikstahl.dhbwapp.ui.lectures.LecturesViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EntityTimetableUiState(
    val lectures: List<RaplaLectureEvent> = emptyList(),
    val selectedDayIndex: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

class EntityTimetableViewModel(
    private val apiClient: ApiClient,
    private val site: String,
    private val type: String,
    private val name: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntityTimetableUiState())
    val uiState: StateFlow<EntityTimetableUiState> = _uiState.asStateFlow()

    private var currentFilteredLectures: List<RaplaLectureEvent> = emptyList()
    var lectureDays: List<LocalDate> = emptyList()

    init {
        loadLectures()
    }

    fun loadLectures() {
        viewModelScope.launch {
            _uiState.value = EntityTimetableUiState(loading = true)
            try {
                val all = apiClient.getLectures(site)
                
                // Filter based on entity type
                currentFilteredLectures = all.filter { lecture ->
                    when (type) {
                        "lecturer" -> lecture.lecturer == name
                        "room" -> lecture.rooms.contains(name)
                        "course" -> lecture.course == name
                        else -> false
                    }
                }

                lectureDays = currentFilteredLectures
                    .mapNotNull { LecturesViewModel.apiDateToLocalDate(it.date) }
                    .distinct()
                    .sorted()

                val todayIndex = findTodayIndex(lectureDays)
                _uiState.value = EntityTimetableUiState(
                    lectures = currentFilteredLectures,
                    selectedDayIndex = todayIndex
                )
            } catch (e: Exception) {
                _uiState.value = EntityTimetableUiState(
                    error = e.message ?: "Fehler beim Laden des Stundenplans"
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
        if (i < lectureDays.lastIndex) {
            _uiState.value = _uiState.value.copy(selectedDayIndex = i + 1)
        }
    }

    fun lecturesForDay(localDate: LocalDate): List<RaplaLectureEvent> {
        return currentFilteredLectures.filter { LecturesViewModel.apiDateToLocalDate(it.date) == localDate }
            .sortedBy { it.startTime }
    }

    private fun findTodayIndex(days: List<LocalDate>): Int {
        if (days.isEmpty()) return 0
        val today = LocalDate.now()
        val index = days.indexOfFirst { it == today }
        return if (index >= 0) index else 0
    }

    class Factory(
        private val apiClient: ApiClient,
        private val site: String,
        private val type: String,
        private val name: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EntityTimetableViewModel(apiClient, site, type, name) as T
        }
    }
}
