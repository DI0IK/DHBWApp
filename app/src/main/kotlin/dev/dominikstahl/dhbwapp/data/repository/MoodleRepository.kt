// Portions of this file are derived from dawdle (https://codeberg.org/fynngodau/dawdle)
// Copyright (c) 2020-2024 Fynn Godau
// Licensed under the GPLv3

package dev.dominikstahl.dhbwapp.data.repository

import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleAssignment
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleAttachment
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleCourse
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleContent
import dev.dominikstahl.dhbwapp.data.local.db.MoodleDao
import dev.dominikstahl.dhbwapp.data.remote.MoodleClient
import dev.dominikstahl.dhbwapp.data.remote.MoodleUploadResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MoodleRepository(
    private val moodleClient: MoodleClient,
    private val moodleDao: MoodleDao
) {
    val coursesFlow: Flow<List<CachedMoodleCourse>> = moodleDao.getCoursesFlow()
    val assignmentsFlow: Flow<List<CachedMoodleAssignment>> = moodleDao.getAssignmentsFlow()

    fun getAssignmentsForCourseFlow(courseId: Int): Flow<List<CachedMoodleAssignment>> {
        return moodleDao.getAssignmentsForCourseFlow(courseId)
    }

    fun getContentForCourseFlow(courseId: Int): Flow<List<CachedMoodleContent>> {
        return moodleDao.getContentForCourseFlow(courseId)
    }

    suspend fun syncMoodle(siteUrl: String, token: String, userId: Int) {
        val remoteCourses = moodleClient.getCourses(siteUrl, token, userId)
        val cachedCourses = remoteCourses.map {
            CachedMoodleCourse(
                id = it.id,
                shortName = it.shortname,
                fullName = it.fullname,
                summary = it.summary,
                timeStart = it.startdate,
                timeEnd = it.enddate
            )
        }

        val assignmentsResponse = moodleClient.getAssignments(siteUrl, token)
        val rawAssignments = assignmentsResponse.courses.flatMap { course ->
            course.assignments.map { assign ->
                CachedMoodleAssignment(
                    id = assign.id,
                    cmid = assign.cmid,
                    courseId = assign.course,
                    name = assign.name,
                    dueDate = assign.duedate,
                    description = assign.intro,
                    isSubmitted = false,
                    statusText = null,
                    attachments = assign.introattachments?.map {
                        CachedMoodleAttachment(
                            filename = it.filename,
                            fileurl = it.fileurl,
                            filesize = it.filesize
                        )
                    } ?: emptyList()
                )
            }
        }

        val allCachedContents = coroutineScope {
            cachedCourses.map { course ->
                async {
                    try {
                        val sections = moodleClient.getCourseContents(siteUrl, token, course.id)
                        sections.flatMap { section ->
                            section.modules?.filter { it.modname in listOf("resource", "url", "folder", "page", "assign") }
                                ?.map { module ->
                                    val file = module.contents?.firstOrNull()
                                    val downloadUrl = when (module.modname) {
                                        "resource" -> file?.fileurl
                                        "url" -> module.url
                                        else -> module.url ?: file?.fileurl
                                    }
                                    CachedMoodleContent(
                                        id = module.id,
                                        courseId = course.id,
                                        sectionName = section.name,
                                        name = module.name,
                                        type = module.modname,
                                        url = downloadUrl,
                                        fileSize = file?.filesize ?: 0,
                                        instanceId = module.instance
                                    )
                                } ?: emptyList()
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }

        val assignmentsWithStatus = coroutineScope {
            rawAssignments.map { assignment ->
                async {
                    try {
                        val statusResp = moodleClient.getSubmissionStatus(siteUrl, token, assignment.id)
                        val submission = statusResp.lastattempt?.submission
                        val isSubmitted = submission?.status == "submitted"
                        val statusText = submission?.status

                        // Find submitted files
                        val filePlugin = submission?.plugins?.find { it.type == "file" }
                        val files = filePlugin?.fileareas?.flatMap { area ->
                            area.files?.map { file ->
                                CachedMoodleAttachment(
                                    filename = file.filename,
                                    fileurl = file.fileurl,
                                    filesize = file.filesize
                                )
                            } ?: emptyList()
                        } ?: emptyList()

                        // Feedback grade
                        val feedbackGrade = statusResp.feedback?.grade?.grade

                        // Feedback comments
                        val commentPlugin = statusResp.feedback?.plugins?.find { it.type == "comments" }
                        val feedbackComments = commentPlugin?.editorfields?.firstOrNull()?.text

                        assignment.copy(
                            isSubmitted = isSubmitted,
                            statusText = statusText,
                            submittedFiles = files,
                            feedbackGrade = feedbackGrade,
                            feedbackComments = feedbackComments
                        )
                     } catch (e: Exception) {
                        assignment
                     }
                }
            }.awaitAll()
        }

        withContext(Dispatchers.IO) {
            moodleDao.refreshCourses(cachedCourses)
            moodleDao.refreshAssignments(assignmentsWithStatus)
            moodleDao.deleteContents()
            moodleDao.insertContents(allCachedContents)
        }
    }

    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            moodleDao.deleteCourses()
            moodleDao.deleteAssignments()
            moodleDao.deleteContents()
        }
    }

    suspend fun downloadFile(url: String, token: String): ByteArray {
        return moodleClient.downloadFile(url, token)
    }

    suspend fun uploadFile(
        siteUrl: String,
        token: String,
        filename: String,
        fileBytes: ByteArray
    ): List<MoodleUploadResponse> {
        return moodleClient.uploadFile(siteUrl, token, filename, fileBytes)
    }

    suspend fun saveSubmission(
        siteUrl: String,
        token: String,
        assignmentId: Int,
        draftItemId: Int
    ) {
        moodleClient.saveSubmission(siteUrl, token, assignmentId, draftItemId)
    }

    suspend fun submitForGrading(
        siteUrl: String,
        token: String,
        assignmentId: Int
    ) {
        moodleClient.submitForGrading(siteUrl, token, assignmentId)
    }
}
