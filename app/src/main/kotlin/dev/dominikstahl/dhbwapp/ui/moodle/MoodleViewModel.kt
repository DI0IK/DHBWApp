// Portions of this file are derived from dawdle (https://codeberg.org/fynngodau/dawdle)
// Copyright (c) 2020-2024 Fynn Godau
// Licensed under the GPLv3

package dev.dominikstahl.dhbwapp.ui.moodle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dominikstahl.dhbwapp.data.local.MoodleSessionManager
import dev.dominikstahl.dhbwapp.data.local.UserPreferences
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleAssignment
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleCourse
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleContent
import dev.dominikstahl.dhbwapp.data.repository.MoodleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

data class MoodleUiState(
    val isLoggedIn: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val courses: List<CachedMoodleCourse> = emptyList(),
    val assignments: List<CachedMoodleAssignment> = emptyList(),
    val lastSyncTime: Long? = null,
    val siteUrl: String? = null
)

class MoodleViewModel(
    private val sessionManager: MoodleSessionManager,
    private val repository: MoodleRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoodleUiState())
    val uiState: StateFlow<MoodleUiState> = _uiState

    init {
        val session = sessionManager.getSession()
        val loggedIn = session != null
        _uiState.value = _uiState.value.copy(
            isLoggedIn = loggedIn,
            siteUrl = session?.siteUrl
        )

        viewModelScope.launch {
            repository.coursesFlow.collect { list ->
                _uiState.value = _uiState.value.copy(courses = list)
            }
        }

        viewModelScope.launch {
            repository.assignmentsFlow.collect { list ->
                _uiState.value = _uiState.value.copy(assignments = list)
            }
        }

        viewModelScope.launch {
            userPreferences.moodleLastSyncTime.collect { time ->
                _uiState.value = _uiState.value.copy(lastSyncTime = time)
            }
        }
    }

    fun loginWithToken(token: String, userId: Int, siteUrl: String) {
        sessionManager.saveSession(token, userId, siteUrl)
        _uiState.value = _uiState.value.copy(
            isLoggedIn = true,
            siteUrl = siteUrl,
            error = null
        )
        triggerSync()
    }

    fun logout() {
        sessionManager.clearSession()
        _uiState.value = MoodleUiState(isLoggedIn = false)
        viewModelScope.launch {
            repository.clearCache()
            userPreferences.clearMoodleCache()
        }
    }

    fun triggerSync() {
        val session = sessionManager.getSession() ?: return
        _uiState.value = _uiState.value.copy(isSyncing = true, error = null)
        viewModelScope.launch {
            try {
                repository.syncMoodle(session.siteUrl, session.token, session.userId)
                userPreferences.setMoodleLastSyncTime(System.currentTimeMillis())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Synchronisierung fehlgeschlagen")
            } finally {
                _uiState.value = _uiState.value.copy(isSyncing = false)
            }
        }
    }

    fun getCourse(courseId: Int): CachedMoodleCourse? {
        return uiState.value.courses.find { it.id == courseId }
    }

    fun getAssignmentsForCourse(courseId: Int): List<CachedMoodleAssignment> {
        return uiState.value.assignments.filter { it.courseId == courseId }
    }

    fun getContentForCourseFlow(courseId: Int): Flow<List<CachedMoodleContent>> {
        return repository.getContentForCourseFlow(courseId)
    }

    fun downloadAndOpenFile(
        context: android.content.Context,
        url: String,
        suggestedFilename: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        val session = sessionManager.getSession()
        if (session == null) {
            onComplete(Result.failure(Exception("Nicht angemeldet")))
            return
        }
        viewModelScope.launch {
            try {
                val fileData = repository.downloadFile(url, session.token)
                val cacheDir = java.io.File(context.cacheDir, "moodle_files").apply { mkdirs() }
                
                // Clean suggested filename by appending extension if missing
                val extensionFromUrl = url.substringAfterLast('.', "").substringBefore('?')
                val filename = if (extensionFromUrl.isNotEmpty() && !suggestedFilename.endsWith(".$extensionFromUrl", ignoreCase = true)) {
                    "${suggestedFilename}.$extensionFromUrl"
                } else {
                    suggestedFilename
                }

                // Replace invalid characters for filesystems
                val safeFilename = filename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val destFile = java.io.File(cacheDir, safeFilename)
                destFile.writeBytes(fileData)

                val authority = "${context.packageName}.fileprovider"
                val fileUri = androidx.core.content.FileProvider.getUriForFile(context, authority, destFile)

                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    val ext = safeFilename.substringAfterLast('.', "").lowercase()
                    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                    setDataAndType(fileUri, mime)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    fun isFileDownloaded(context: android.content.Context, url: String, suggestedFilename: String): Boolean {
        val cacheDir = java.io.File(context.cacheDir, "moodle_files")
        val extensionFromUrl = url.substringAfterLast('.', "").substringBefore('?')
        val filename = if (extensionFromUrl.isNotEmpty() && !suggestedFilename.endsWith(".$extensionFromUrl", ignoreCase = true)) {
            "${suggestedFilename}.$extensionFromUrl"
        } else {
            suggestedFilename
        }
        val safeFilename = filename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return java.io.File(cacheDir, safeFilename).exists()
    }
    fun uploadAndSaveAssignmentFile(
        assignmentId: Int,
        filename: String,
        fileBytes: ByteArray,
        onResult: (Result<Unit>) -> Unit
    ) {
        val session = sessionManager.getSession()
        if (session == null) {
            onResult(Result.failure(Exception("Nicht angemeldet")))
            return
        }
        viewModelScope.launch {
            try {
                val uploadResp = repository.uploadFile(session.siteUrl, session.token, filename, fileBytes)
                val draftItemId = uploadResp.firstOrNull()?.itemid ?: throw Exception("Keine Item ID erhalten")
                repository.saveSubmission(session.siteUrl, session.token, assignmentId, draftItemId)
                repository.syncMoodle(session.siteUrl, session.token, session.userId)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun submitAssignmentForGrading(
        assignmentId: Int,
        onResult: (Result<Unit>) -> Unit
    ) {
        val session = sessionManager.getSession()
        if (session == null) {
            onResult(Result.failure(Exception("Nicht angemeldet")))
            return
        }
        viewModelScope.launch {
            try {
                repository.submitForGrading(session.siteUrl, session.token, assignmentId)
                repository.syncMoodle(session.siteUrl, session.token, session.userId)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun deleteAssignmentSubmission(
        assignmentId: Int,
        onResult: (Result<Unit>) -> Unit
    ) {
        val session = sessionManager.getSession()
        if (session == null) {
            onResult(Result.failure(Exception("Nicht angemeldet")))
            return
        }
        viewModelScope.launch {
            try {
                repository.saveSubmission(session.siteUrl, session.token, assignmentId, 0)
                repository.syncMoodle(session.siteUrl, session.token, session.userId)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }
    class Factory(
        private val sessionManager: MoodleSessionManager,
        private val repository: MoodleRepository,
        private val userPreferences: UserPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MoodleViewModel(sessionManager, repository, userPreferences) as T
        }
    }
}
