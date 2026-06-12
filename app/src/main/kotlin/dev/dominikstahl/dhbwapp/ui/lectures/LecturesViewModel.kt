package dev.dominikstahl.dhbwapp.ui.lectures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

data class LecturesUiState(
    val lectures: List<RaplaLectureEvent> = emptyList(),
    val selectedWeekIndex: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

class LecturesViewModel(
    private val apiClient: ApiClient,
    private var course: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LecturesUiState())
    val uiState: StateFlow<LecturesUiState> = _uiState

    private var currentLectures: List<RaplaLectureEvent> = emptyList()

    var lectureWeeks: List<LocalDate> = emptyList()

    fun setCourse(newCourse: String) {
        course = newCourse
    }

    fun hasCourse(): Boolean = course.isNotBlank()

    fun loadLectures() {
        viewModelScope.launch {
            _uiState.value = LecturesUiState(loading = true)
            try {
                val all = apiClient.getLecturesForCourse(course, archived = false)
                currentLectures = all
                
                val days = all.mapNotNull { apiDateToLocalDate(it.date) }.distinct()
                lectureWeeks = days.map { getMonday(it) }.distinct().sorted()
                
                val todayWeekIndex = findTodayWeekIndex(lectureWeeks)
                _uiState.value = LecturesUiState(lectures = all, selectedWeekIndex = todayWeekIndex)
            } catch (e: Exception) {
                _uiState.value = LecturesUiState(
                    error = e.message ?: "Fehler beim Laden der Vorlesungen",
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
        return currentLectures
            .filter { 
                val date = apiDateToLocalDate(it.date)
                date != null && !date.isBefore(monday) && !date.isAfter(sunday)
            }
            .groupBy { apiDateToLocalDate(it.date)!! }
            .mapValues { (_, list) -> list.sortedBy { it.startTime } }
            .toSortedMap()
    }

    private fun findTodayWeekIndex(weeks: List<LocalDate>): Int {
        if (weeks.isEmpty()) return 0
        val todayMonday = getMonday(LocalDate.now())
        val index = weeks.indexOf(todayMonday)
        if (index >= 0) return index
        
        // Find closest week
        val closestIndex = weeks.indexOfFirst { !it.isBefore(todayMonday) }
        return if (closestIndex >= 0) closestIndex else weeks.lastIndex
    }

    companion object {
        private val berlinZone = ZoneId.of("Europe/Berlin")

        fun getMonday(date: LocalDate): LocalDate {
            return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }

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
        private val course: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LecturesViewModel(apiClient, course) as T
        }
    }
}
