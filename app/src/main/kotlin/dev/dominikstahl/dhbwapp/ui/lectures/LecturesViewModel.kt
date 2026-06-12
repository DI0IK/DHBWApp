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

data class LecturesUiState(
    val lectures: List<RaplaLectureEvent> = emptyList(),
    val selectedDayIndex: Int = 0,
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

    var lectureDays: List<LocalDate> = emptyList()

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
                lectureDays = all.mapNotNull { apiDateToLocalDate(it.date) }.distinct().sorted()
                val todayIndex = findTodayIndex(lectureDays)
                _uiState.value = LecturesUiState(lectures = all, selectedDayIndex = todayIndex)
            } catch (e: Exception) {
                _uiState.value = LecturesUiState(
                    error = e.message ?: "Fehler beim Laden der Vorlesungen",
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
        return currentLectures.filter { apiDateToLocalDate(it.date) == localDate }
            .sortedBy { it.startTime }
    }

    private fun findTodayIndex(days: List<LocalDate>): Int {
        if (days.isEmpty()) return 0
        val today = LocalDate.now()
        return days.indexOfFirst { it == today }.coerceAtLeast(0)
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
        private val course: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LecturesViewModel(apiClient, course) as T
        }
    }
}
