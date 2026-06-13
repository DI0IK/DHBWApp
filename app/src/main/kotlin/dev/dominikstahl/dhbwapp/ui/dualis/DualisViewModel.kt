package dev.dominikstahl.dhbwapp.ui.dualis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dominikstahl.dhbwapp.data.local.DualisCredentialsManager
import dev.dominikstahl.dhbwapp.data.remote.DualisClient
import dev.dominikstahl.dhbwapp.data.remote.DualisDocument
import dev.dominikstahl.dhbwapp.data.remote.DualisGPA
import dev.dominikstahl.dhbwapp.data.remote.DualisOverallData
import dev.dominikstahl.dhbwapp.data.remote.DualisSemester
import dev.dominikstahl.dhbwapp.data.remote.DualisSemesterCourse
import dev.dominikstahl.dhbwapp.data.remote.DualisSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DualisUiState(
    val isLoggedIn: Boolean = false,
    val isAutoLoggingIn: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val loginError: String? = null,
    val semesters: List<DualisSemester> = emptyList(),
    val selectedSemesterIndex: Int = 0,
    val overallData: DualisOverallData? = null,
    val gpa: DualisGPA? = null,
    val documents: List<DualisDocument> = emptyList(),
)

class DualisViewModel(
    private val dualisClient: DualisClient,
    private val credentialsManager: DualisCredentialsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DualisUiState())
    val uiState: StateFlow<DualisUiState> = _uiState

    private var session: DualisSession? = null

    init {
        checkSavedCredentialsAndAutoLogin()
    }

    private fun checkSavedCredentialsAndAutoLogin() {
        val creds = credentialsManager.getCredentials()
        if (creds != null) {
            _uiState.value = _uiState.value.copy(isAutoLoggingIn = true, loading = true)
            viewModelScope.launch {
                try {
                    val newSession = dualisClient.login(creds.first, creds.second)
                    session = newSession
                    _uiState.value = _uiState.value.copy(
                        isLoggedIn = true,
                        isAutoLoggingIn = false,
                        loginError = null
                    )
                    loadData()
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isAutoLoggingIn = false,
                        loading = false,
                        loginError = "Automatischer Login fehlgeschlagen: ${e.message}"
                    )
                }
            }
        }
    }

    fun login(email: String, password: String) {
        _uiState.value = _uiState.value.copy(loading = true, loginError = null)
        viewModelScope.launch {
            try {
                val newSession = dualisClient.login(email, password)
                session = newSession
                credentialsManager.saveCredentials(email, password)
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = true,
                    loading = false,
                    loginError = null
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    loginError = e.message ?: "Login fehlgeschlagen"
                )
            }
        }
    }

    fun logout() {
        credentialsManager.clearCredentials()
        session = null
        _uiState.value = DualisUiState()
    }

    fun loadData() {
        val currentSession = session ?: return
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                // 1. Get semesters
                val semesterList = dualisClient.getSemesters(currentSession)
                
                // 2. Get overall module data and GPA
                val overall = dualisClient.getOverallData(currentSession)
                val gpaData = dualisClient.getGPA(currentSession)
                
                // 3. Get documents
                val docList = dualisClient.getDocuments(currentSession)

                _uiState.value = _uiState.value.copy(
                    semesters = semesterList,
                    overallData = overall,
                    gpa = gpaData,
                    documents = docList,
                    selectedSemesterIndex = 0,
                    loading = false
                )

                // 4. Auto-load courses for the first semester if available
                if (semesterList.isNotEmpty()) {
                    loadCoursesForSemester(0)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "Fehler beim Laden der Daten: ${e.message}"
                )
            }
        }
    }

    fun selectSemester(index: Int) {
        if (index in _uiState.value.semesters.indices) {
            _uiState.value = _uiState.value.copy(selectedSemesterIndex = index)
            loadCoursesForSemester(index)
        }
    }

    private fun loadCoursesForSemester(index: Int) {
        val currentSession = session ?: return
        val semester = _uiState.value.semesters.getOrNull(index) ?: return
        
        // If courses are already loaded, we don't strictly need to reload, but we can do it to refresh
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            try {
                val courses = dualisClient.getCourses(currentSession, semester.value)
                val updatedSemesters = _uiState.value.semesters.toMutableList()
                updatedSemesters[index] = semester.copy(courses = courses)
                
                _uiState.value = _uiState.value.copy(
                    semesters = updatedSemesters,
                    loading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "Fehler beim Laden des Semesters ${semester.name}: ${e.message}"
                )
            }
        }
    }

    fun loadExamsForCourse(semesterIndex: Int, courseIndex: Int) {
        val currentSession = session ?: return
        val semester = _uiState.value.semesters.getOrNull(semesterIndex) ?: return
        val course = semester.courses.getOrNull(courseIndex) ?: return
        val link = course.examLink ?: return

        // Skip loading if exams already loaded
        if (course.exams.isNotEmpty()) return

        viewModelScope.launch {
            try {
                val exams = dualisClient.getExams(currentSession, link)
                val updatedCourses = semester.courses.toMutableList()
                updatedCourses[courseIndex] = course.copy(exams = exams)
                
                val updatedSemesters = _uiState.value.semesters.toMutableList()
                updatedSemesters[semesterIndex] = semester.copy(courses = updatedCourses)

                _uiState.value = _uiState.value.copy(
                    semesters = updatedSemesters
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Fehler beim Laden der Prüfungsdetails: ${e.message}"
                )
            }
        }
    }

    class Factory(
        private val dualisClient: DualisClient,
        private val credentialsManager: DualisCredentialsManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DualisViewModel(dualisClient, credentialsManager) as T
        }
    }
}
