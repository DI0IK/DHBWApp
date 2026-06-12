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
    val selectedWeekIndex: Int = 0,
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
    var lectureWeeks: List<LocalDate> = emptyList()

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
                        "lecturer" -> DirectoryExtractor.parseLecturerNames(lecture.lecturer).contains(name)
                        "room" -> lecture.rooms.contains(name)
                        "course" -> lecture.course == name
                        else -> false
                    }
                }

                val days = currentFilteredLectures
                    .mapNotNull { LecturesViewModel.apiDateToLocalDate(it.date) }
                    .distinct()
                
                lectureWeeks = days.map { LecturesViewModel.getMonday(it) }.distinct().sorted()

                val todayWeekIndex = findTodayWeekIndex(lectureWeeks)
                _uiState.value = EntityTimetableUiState(
                    lectures = currentFilteredLectures,
                    selectedWeekIndex = todayWeekIndex
                )
            } catch (e: Exception) {
                _uiState.value = EntityTimetableUiState(
                    error = e.message ?: "Fehler beim Laden des Stundenplans"
                )
            }
        }
    }

    fun previousWeek() {
        val i = _uiState.value.selectedWeekIndex
        if (i > 0) {
            _uiState.value = _uiState.value.copy(selectedWeekIndex = i - 1)
        }
    }

    fun nextWeek() {
        val i = _uiState.value.selectedWeekIndex
        if (i < lectureWeeks.lastIndex) {
            _uiState.value = _uiState.value.copy(selectedWeekIndex = i + 1)
        }
    }

    fun lecturesForWeek(monday: LocalDate): Map<LocalDate, List<RaplaLectureEvent>> {
        val sunday = monday.plusDays(6)
        return currentFilteredLectures
            .filter { 
                val date = LecturesViewModel.apiDateToLocalDate(it.date)
                date != null && !date.isBefore(monday) && !date.isAfter(sunday)
            }
            .groupBy { LecturesViewModel.apiDateToLocalDate(it.date)!! }
            .mapValues { (_, list) -> list.sortedBy { it.startTime } }
            .toSortedMap()
    }

    private fun findTodayWeekIndex(weeks: List<LocalDate>): Int {
        if (weeks.isEmpty()) return 0
        val todayMonday = LecturesViewModel.getMonday(LocalDate.now())
        val index = weeks.indexOf(todayMonday)
        if (index >= 0) return index
        
        // Find closest week
        val closestIndex = weeks.indexOfFirst { !it.isBefore(todayMonday) }
        return if (closestIndex >= 0) closestIndex else weeks.lastIndex
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
