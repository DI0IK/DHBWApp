package dev.dominikstahl.dhbwapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.SiteDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val sites: List<SiteDto> = emptyList(),
    val selectedSite: String? = null,
    val courses: List<String> = emptyList(),
    val selectedCourse: String? = null,
    val selectedUserType: String? = null,
    val loading: Boolean = false,
    val coursesLoading: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        loadSites()
    }

    private fun loadSites() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val sites = apiClient.getSites()
                _uiState.value = _uiState.value.copy(sites = sites, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load sites",
                )
            }
        }
    }

    fun selectSite(site: String) {
        _uiState.value = _uiState.value.copy(selectedSite = site, selectedCourse = null, courses = emptyList())
        loadCourses(site)
    }

    private fun loadCourses(site: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(coursesLoading = true)
            try {
                val courses = apiClient.getCoursesForSite(site)
                    _uiState.value = _uiState.value.copy(courses = courses.sorted(), coursesLoading = false)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(coursesLoading = false)
            }
        }
    }

    fun selectCourse(course: String) {
        _uiState.value = _uiState.value.copy(selectedCourse = course)
    }

    fun selectUserType(userType: String) {
        _uiState.value = _uiState.value.copy(selectedUserType = userType)
    }

    class Factory(
        private val apiClient: ApiClient,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(apiClient) as T
        }
    }
}
