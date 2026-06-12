package dev.dominikstahl.dhbwapp.ui.directory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DirectoryUiState(
    val lecturers: List<String> = emptyList(),
    val rooms: List<String> = emptyList(),
    val courses: List<String> = emptyList(),
    val filteredLecturers: List<String> = emptyList(),
    val filteredRooms: List<String> = emptyList(),
    val filteredCourses: List<String> = emptyList(),
    val selectedTab: Int = 0,
    val searchQuery: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class DirectoryViewModel(
    private val apiClient: ApiClient,
    private val site: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DirectoryUiState())
    val uiState: StateFlow<DirectoryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val lectures = apiClient.getLectures(site)
                
                val lecturersList = DirectoryExtractor.extractLecturers(lectures)
                val roomsList = DirectoryExtractor.extractRooms(lectures)
                val coursesList = DirectoryExtractor.extractCourses(lectures)

                _uiState.value = _uiState.value.copy(
                    lecturers = lecturersList,
                    rooms = roomsList,
                    courses = coursesList,
                    filteredLecturers = lecturersList,
                    filteredRooms = roomsList,
                    filteredCourses = coursesList,
                    loading = false
                )
                applyFilter(_uiState.value.searchQuery)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Fehler beim Laden des Verzeichnisses"
                )
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilter(query)
    }

    private fun applyFilter(query: String) {
        val state = _uiState.value
        if (query.isBlank()) {
            _uiState.value = state.copy(
                filteredLecturers = state.lecturers,
                filteredRooms = state.rooms,
                filteredCourses = state.courses
            )
        } else {
            val lowercaseQuery = query.lowercase()
            _uiState.value = state.copy(
                filteredLecturers = state.lecturers.filter { it.lowercase().contains(lowercaseQuery) },
                filteredRooms = state.rooms.filter { it.lowercase().contains(lowercaseQuery) },
                filteredCourses = state.courses.filter { it.lowercase().contains(lowercaseQuery) }
            )
        }
    }

    class Factory(
        private val apiClient: ApiClient,
        private val site: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DirectoryViewModel(apiClient, site) as T
        }
    }
}
