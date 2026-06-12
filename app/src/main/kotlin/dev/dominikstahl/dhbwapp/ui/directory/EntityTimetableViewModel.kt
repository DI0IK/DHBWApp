package dev.dominikstahl.dhbwapp.ui.directory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dominikstahl.dhbwapp.data.model.EnrichedLectureEvent
import dev.dominikstahl.dhbwapp.data.model.LecturesPage
import dev.dominikstahl.dhbwapp.data.repository.CalendarRepository
import dev.dominikstahl.dhbwapp.ui.lectures.LecturesViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EntityTimetableUiState(
    val page: LecturesPage? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class EntityTimetableViewModel(
    private val calendarRepository: CalendarRepository,
    private val site: String,
    private val type: String,
    private val name: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntityTimetableUiState())
    val uiState: StateFlow<EntityTimetableUiState> = _uiState.asStateFlow()

    /** The Monday of the currently displayed week. Starts at today's week. */
    private var currentMonday: LocalDate = mondayOf(LocalDate.now())

    init {
        loadPage(currentMonday)
    }

    fun loadLectures() {
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
            _uiState.value = EntityTimetableUiState(loading = true)
            try {
                val all = calendarRepository.getLectures(site)

                val filtered = all.filter { enriched ->
                    val lecture = enriched.lecture
                    when (type) {
                        "lecturer" -> {
                            val lecturers = DirectoryExtractor.cleanLecturers(lecture.lecturer)
                                .map { it.trim().lowercase() }
                            lecturers.contains(name.trim().lowercase())
                        }
                        "room" -> {
                            val rooms = lecture.rooms.map { it.trim().lowercase() }
                            rooms.contains(name.trim().lowercase())
                        }
                        "course" -> {
                            val courses = lecture.course.split(",").map { it.trim().lowercase() }
                            courses.contains(name.trim().lowercase())
                        }
                        else -> false
                    }
                }

                val page = calendarRepository.buildAndEnrichPage(
                    events = filtered,
                    cacheKey = "$site:true",
                    date = monday,
                )
                currentMonday = page.weekMonday
                _uiState.value = EntityTimetableUiState(page = page)
            } catch (e: Exception) {
                _uiState.value = EntityTimetableUiState(
                    error = e.message ?: "Fehler beim Laden des Stundenplans"
                )
            }
        }
    }

    /** Returns events for a specific day within the current page, sorted by start time. */
    fun lecturesForDay(date: LocalDate): List<EnrichedLectureEvent> =
        (_uiState.value.page?.events ?: emptyList())
            .filter { LecturesViewModel.apiDateToLocalDate(it.lecture.date) == date }
            .sortedBy { it.lecture.startTime }

    /** All distinct days in the current page that have at least one event, sorted ascending. */
    fun daysInCurrentPage(): List<LocalDate> =
        (_uiState.value.page?.events ?: emptyList())
            .mapNotNull { LecturesViewModel.apiDateToLocalDate(it.lecture.date) }
            .distinct()
            .sorted()

    private fun mondayOf(date: LocalDate): LocalDate =
        date.with(java.time.DayOfWeek.MONDAY)

    class Factory(
        private val calendarRepository: CalendarRepository,
        private val site: String,
        private val type: String,
        private val name: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EntityTimetableViewModel(calendarRepository, site, type, name) as T
    }
}
