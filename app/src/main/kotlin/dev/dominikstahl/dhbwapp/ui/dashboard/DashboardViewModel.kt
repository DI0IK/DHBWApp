package dev.dominikstahl.dhbwapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.data.remote.DualisClient
import dev.dominikstahl.dhbwapp.data.local.DualisCredentialsManager
import dev.dominikstahl.dhbwapp.data.remote.DualisGPA
import dev.dominikstahl.dhbwapp.remote.models.MenuDay
import dev.dominikstahl.dhbwapp.remote.models.MensaResponse
import dev.dominikstahl.dhbwapp.remote.models.ParkingLot
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import dev.dominikstahl.dhbwapp.remote.models.RoomAvailabilityResponseStats
import dev.dominikstahl.dhbwapp.ui.lectures.LecturesViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import dev.dominikstahl.dhbwapp.ui.mensa.MergedMenuItem
import dev.dominikstahl.dhbwapp.ui.mensa.groupMenuItems

import dev.dominikstahl.dhbwapp.data.local.UserPreferences

import dev.dominikstahl.dhbwapp.data.repository.TimetableRepository
import dev.dominikstahl.dhbwapp.data.repository.MensaRepository
import dev.dominikstahl.dhbwapp.data.repository.MoodleRepository
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleAssignment
import kotlinx.coroutines.Job

data class DashboardUiState(
    val upcomingLectures: List<RaplaLectureEvent> = emptyList(),
    val todayMensaMeals: List<MergedMenuItem> = emptyList(),
    val mensaClosed: Boolean = false,
    val parkingLots: List<ParkingLot> = emptyList(),
    val roomStats: RoomAvailabilityResponseStats? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val dualisCredentialsExist: Boolean = false,
    val dualisUnlocked: Boolean = false,
    val dualisGpa: DualisGPA? = null,
    val dualisLoading: Boolean = false,
    val dualisError: String? = null,
    val dualisNewestGrade: String? = null,
    val upcomingMoodleTasks: List<CachedMoodleAssignment> = emptyList()
)

class DashboardViewModel(
    private val timetableRepository: TimetableRepository,
    private val mensaRepository: MensaRepository,
    private val moodleRepository: MoodleRepository,
    private val apiClient: ApiClient,
    private val dualisClient: DualisClient,
    private val credentialsManager: DualisCredentialsManager,
    private val userPreferences: UserPreferences,
    private var site: String,
    private var course: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    private var collectionJob: Job? = null

    init {
        checkSavedDualisCredentials()
        viewModelScope.launch {
            userPreferences.newestGradeInfo.collect { newestGrade ->
                _uiState.value = _uiState.value.copy(dualisNewestGrade = newestGrade)
            }
        }
        startFlowCollection()
    }

    fun checkSavedDualisCredentials() {
        val creds = credentialsManager.getCredentials()
        _uiState.value = _uiState.value.copy(dualisCredentialsExist = creds != null)
    }

    fun unlockDualisAndLoadData() {
        val creds = credentialsManager.getCredentials() ?: return
        _uiState.value = _uiState.value.copy(dualisLoading = true, dualisError = null)
        viewModelScope.launch {
            try {
                val session = dualisClient.login(creds.first, creds.second)
                val gpaData = dualisClient.getGPA(session)
                _uiState.value = _uiState.value.copy(
                    dualisUnlocked = true,
                    dualisGpa = gpaData,
                    dualisLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    dualisLoading = false,
                    dualisError = "Fehler beim Laden von Dualis: ${e.message}"
                )
            }
        }
    }

    private fun startFlowCollection() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            launch {
                timetableRepository.getLectures(course).collect { lectures ->
                    val today = LocalDate.now()
                    val upcoming = lectures.filter { event ->
                        val eventDate = LecturesViewModel.apiDateToLocalDate(event.date)
                        eventDate == today && !event.startTime.isLecturePassed()
                    }.sortedBy { it.startTime }

                    _uiState.value = _uiState.value.copy(
                        upcomingLectures = upcoming.take(2)
                    )
                }
            }
            launch {
                mensaRepository.getMensaMenu(site).collect { mensaMenus ->
                    val today = LocalDate.now()
                    val todayMenuDay = mensaMenus.firstOrNull()?.menus?.firstOrNull { day ->
                        dev.dominikstahl.dhbwapp.ui.mensa.MensaViewModel.apiDateToLocalDate(day.date) == today
                    }
                    val todayMeals = mutableListOf<MergedMenuItem>()
                    var closed = false
                    if (todayMenuDay != null) {
                        closed = todayMenuDay.closed
                        if (!closed) {
                            todayMeals.addAll(groupMenuItems(todayMenuDay.mainCourses.orEmpty()).take(2))
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        todayMensaMeals = todayMeals,
                        mensaClosed = closed
                    )
                }
            }
            launch {
                moodleRepository.assignmentsFlow.collect { assignments ->
                    val now = System.currentTimeMillis() / 1000
                    val nextTasks = assignments
                        .filter { !it.isSubmitted && it.dueDate > now }
                        .sortedBy { it.dueDate }
                        .take(2)
                    _uiState.value = _uiState.value.copy(
                        upcomingMoodleTasks = nextTasks
                    )
                }
            }
        }
    }

    fun updateParams(newSite: String, newCourse: String) {
        site = newSite
        course = newCourse
        checkSavedDualisCredentials()
        startFlowCollection()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val today = LocalDate.now()
                val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

                // Trigger network syncs in parallel
                val lecturesDeferred = async {
                    try {
                        timetableRepository.syncLectures(course)
                    } catch (_: Exception) {}
                }
                
                val mensaDeferred = async {
                    try {
                        mensaRepository.syncMensaMenu(site)
                    } catch (_: Exception) {}
                }

                val parkingDeferred = async {
                    if (site.isNotBlank()) {
                        apiClient.getParking().filter { it.site == site }
                    } else {
                        emptyList()
                    }
                }

                val roomsDeferred = async {
                    if (site.isNotBlank()) {
                        apiClient.getRoomAvailability(site, todayStr)
                    } else {
                        null
                    }
                }

                lecturesDeferred.await()
                mensaDeferred.await()
                val parking = try { parkingDeferred.await() } catch (_: Exception) { emptyList() }
                val roomResponse = try { roomsDeferred.await() } catch (_: Exception) { null }

                _uiState.value = _uiState.value.copy(
                    parkingLots = parking,
                    roomStats = roomResponse?.stats,
                    loading = false
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Fehler beim Laden des Dashboards"
                )
            }
        }
    }

    private fun String.isLecturePassed(): Boolean {
        return try {
            val nowTime = java.time.LocalTime.now()
            val parsedTime = if (this.contains("T")) {
                OffsetDateTime.parse(this).atZoneSameInstant(ZoneId.systemDefault()).toLocalTime()
            } else {
                java.time.LocalTime.parse(this.take(5))
            }
            parsedTime.isBefore(nowTime)
        } catch (_: Exception) {
            false
        }
    }

    class Factory(
        private val timetableRepository: TimetableRepository,
        private val mensaRepository: MensaRepository,
        private val moodleRepository: MoodleRepository,
        private val apiClient: ApiClient,
        private val dualisClient: DualisClient,
        private val credentialsManager: DualisCredentialsManager,
        private val userPreferences: UserPreferences,
        private val site: String,
        private val course: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(
                timetableRepository,
                mensaRepository,
                moodleRepository,
                apiClient,
                dualisClient,
                credentialsManager,
                userPreferences,
                site,
                course
            ) as T
        }
    }
}
