package dev.dominikstahl.dhbwapp.ui.lectures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dominikstahl.dhbwapp.data.model.EnrichedLectureEvent
import dev.dominikstahl.dhbwapp.data.model.LecturesPage
import dev.dominikstahl.dhbwapp.data.repository.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LecturesUiState(
    val page: LecturesPage? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class LecturesViewModel(
    private val calendarRepository: CalendarRepository,
    private var course: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LecturesUiState())
    val uiState: StateFlow<LecturesUiState> = _uiState

    /** The Monday of the currently displayed week. Starts at today's week. */
    private var currentMonday: LocalDate = mondayOf(LocalDate.now())

    fun setCourse(newCourse: String) {
        course = newCourse
    }

    fun hasCourse(): Boolean = course.isNotBlank()

    fun loadLectures() {
        if (!hasCourse()) return
        loadPage(currentMonday)
    }

    fun previousWeek() {
        val page = _uiState.value.page ?: return
        if (page.hasPrevious) loadPage(currentMonday.minusWeeks(1))
    }

    fun nextWeek() {
        val page = _uiState.value.page ?: return
        if (page.hasNext) loadPage(currentMonday.plusWeeks(1))
    }

    private fun loadPage(monday: LocalDate) {
        currentMonday = monday
        viewModelScope.launch {
            _uiState.value = LecturesUiState(loading = true)
            try {
                val page = calendarRepository.getLecturesPageForCourse(
                    course = course,
                    date = monday,
                    archived = false,
                )
                // The repository may have snapped to a different week if the
                // requested one was empty — keep currentMonday in sync.
                currentMonday = page.weekMonday
                _uiState.value = LecturesUiState(page = page)
            } catch (e: Exception) {
                _uiState.value = LecturesUiState(
                    error = e.message ?: "Fehler beim Laden der Vorlesungen"
                )
            }
        }
    }

    /** Returns events for a specific day within the current page, sorted by start time. */
    fun lecturesForDay(date: LocalDate): List<EnrichedLectureEvent> =
        (_uiState.value.page?.events ?: emptyList())
            .filter { apiDateToLocalDate(it.lecture.date) == date }
            .sortedBy { it.lecture.startTime }

    /**
     * All distinct days in the current page that have at least one event,
     * sorted ascending. Used by the UI to render day-section headers.
     */
    fun daysInCurrentPage(): List<LocalDate> =
        (_uiState.value.page?.events ?: emptyList())
            .mapNotNull { apiDateToLocalDate(it.lecture.date) }
            .distinct()
            .sorted()

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

        private fun mondayOf(date: LocalDate): LocalDate =
            date.with(java.time.DayOfWeek.MONDAY)

        /** e.g. "KW 24 · 09.06 – 13.06" */
        fun weekLabel(monday: LocalDate): String {
            val kw = monday.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
            val friday = monday.plusDays(4)
            val fmt = DateTimeFormatter.ofPattern("dd.MM", Locale.GERMANY)
            return "KW $kw · ${monday.format(fmt)} – ${friday.format(fmt)}"
        }
    }

    class Factory(
        private val calendarRepository: CalendarRepository,
        private val course: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LecturesViewModel(calendarRepository, course) as T
    }
}
