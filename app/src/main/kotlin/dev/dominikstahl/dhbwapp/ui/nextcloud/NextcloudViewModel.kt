package dev.dominikstahl.dhbwapp.ui.nextcloud

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dominikstahl.dhbwapp.data.local.NextcloudSessionManager
import dev.dominikstahl.dhbwapp.data.remote.NextcloudClient
import dev.dominikstahl.dhbwapp.data.remote.NextcloudFile
import dev.dominikstahl.dhbwapp.data.repository.NextcloudRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class NextcloudUiState(
    val isLoggedIn: Boolean = false,
    val isInitializing: Boolean = true,
    val currentPath: String = "/",
    val files: List<NextcloudFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverUrl: String = "",
    val breadcrumbs: List<Breadcrumb> = listOf(Breadcrumb("/", "Root"))
)

data class Breadcrumb(
    val path: String,
    val name: String
)

class NextcloudViewModel(
    private val repository: NextcloudRepository,
    private val context: Context,
    private val client: NextcloudClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(NextcloudUiState())
    val uiState: StateFlow<NextcloudUiState> = _uiState.asStateFlow()

    private var pathStack = mutableListOf<String>()

    init {
        val config = repository.getConfig()
        if (config != null) {
            _uiState.value = _uiState.value.copy(
                isLoggedIn = true,
                isInitializing = false,
                serverUrl = config.serverUrl
            )
            loadFiles("/")
        } else {
            _uiState.value = _uiState.value.copy(isInitializing = false)
        }
    }

    fun login(serverUrl: String, username: String, appPassword: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = client.testConnection(serverUrl, username, appPassword)
                if (result.isSuccess) {
                    repository.saveConfig(
                        NextcloudSessionManager.NextcloudConfig(
                            serverUrl = serverUrl,
                            username = username,
                            appPassword = appPassword
                        )
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoggedIn = true,
                        isLoading = false,
                        serverUrl = serverUrl
                    )
                    loadFiles("/")
                    onResult(Result.success(Unit))
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onResult(Result.failure(result.exceptionOrNull() ?: Exception("Verbindung fehlgeschlagen")))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                onResult(Result.failure(e))
            }
        }
    }

    fun logout() {
        repository.logout()
        pathStack.clear()
        pathStack.add("/")
        _uiState.value = NextcloudUiState(isInitializing = false)
    }

    fun loadFiles(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.listFiles(path, context.cacheDir)
            result.fold(
                onSuccess = { files ->
                    val sorted = files.sortedByDescending { it.isDirectory }.sortedBy { it.name.lowercase() }
                    val breadcrumbs = buildBreadcrumbs(path)
                    _uiState.value = _uiState.value.copy(
                        files = sorted,
                        currentPath = path,
                        breadcrumbs = breadcrumbs,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Fehler beim Laden"
                    )
                }
            )
        }
    }

    fun navigateToFolder(path: String) {
        pathStack.add(_uiState.value.currentPath)
        loadFiles(path)
    }

    fun navigateBack(): Boolean {
        if (pathStack.isNotEmpty()) {
            val prevPath = pathStack.removeLast()
            loadFiles(prevPath)
            return true
        }
        return false
    }

    fun refresh() {
        loadFiles(_uiState.value.currentPath)
    }

    private fun buildBreadcrumbs(path: String): List<Breadcrumb> {
        if (path == "/") return listOf(Breadcrumb("/", "Root"))
        val parts = path.split("/").filter { it.isNotEmpty() }
        val crumbs = mutableListOf(Breadcrumb("/", "Root"))
        var current = ""
        for (part in parts) {
            current += "/$part"
            crumbs.add(Breadcrumb(current, part))
        }
        return crumbs
    }

    fun navigateToBreadcrumb(path: String) {
        val currentPath = _uiState.value.currentPath
        if (path != currentPath) {
            // Trim stack to match
            while (pathStack.isNotEmpty()) {
                val last = pathStack.last()
                if (last == path || path.startsWith(last)) break
                pathStack.removeLast()
            }
            loadFiles(path)
        }
    }

    fun downloadAndViewFile(
        ncFile: NextcloudFile,
        cacheDir: File = context.cacheDir,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = repository.downloadFile(ncFile.path, cacheDir)
                result.fold(
                    onSuccess = { file ->
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        onSuccess(file)
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        onError(e.message ?: "Download fehlgeschlagen")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onError(e.message ?: "Download fehlgeschlagen")
            }
        }
    }

    class Factory(
        private val repository: NextcloudRepository,
        private val context: Context,
        private val client: NextcloudClient
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NextcloudViewModel(repository, context, client) as T
        }
    }
}
