package dev.dominikstahl.dhbwapp.data.repository

import dev.dominikstahl.dhbwapp.data.local.db.CachedLecture
import dev.dominikstahl.dhbwapp.data.local.db.LectureDao
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TimetableRepository(
    private val apiClient: ApiClient,
    private val lectureDao: LectureDao
) {
    fun getLectures(course: String): Flow<List<RaplaLectureEvent>> {
        return lectureDao.getLecturesForCourse(course).map { cachedList ->
            cachedList.map { it.toRaplaLectureEvent() }
        }
    }

    suspend fun syncLectures(course: String) {
        if (course.isBlank()) return
        val remoteLectures = apiClient.getLecturesForCourse(course, archived = false)
        val cached = remoteLectures.map { CachedLecture.fromRaplaLectureEvent(it) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            lectureDao.refreshLecturesForCourse(course, cached)
        }
    }
}
