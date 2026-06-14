// Portions of this file are derived from dawdle (https://codeberg.org/fynngodau/dawdle)
// Copyright (c) 2020-2024 Fynn Godau
// Licensed under the GPLv3

package dev.dominikstahl.dhbwapp.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.statement.readBytes
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MoodleClient(private val httpClient: HttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun normalizeUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean.removeSuffix("/")
    }

    suspend fun getSiteInfo(siteUrl: String, token: String): MoodleSiteInfo {
        val base = normalizeUrl(siteUrl)
        val response = httpClient.get("$base/webservice/rest/server.php") {
            parameter("wsfunction", "core_webservice_get_site_info")
            parameter("wstoken", token)
            parameter("moodlewsrestformat", "json")
        }
        val text = response.bodyAsText()
        if (text.contains("exception")) {
            throw Exception("Moodle Error: $text")
        }
        return json.decodeFromString(text)
    }

    suspend fun getCourses(siteUrl: String, token: String, userId: Int): List<MoodleCourseDto> {
        val base = normalizeUrl(siteUrl)
        val response = httpClient.get("$base/webservice/rest/server.php") {
            parameter("wsfunction", "core_enrol_get_users_courses")
            parameter("wstoken", token)
            parameter("userid", userId)
            parameter("moodlewsrestformat", "json")
        }
        val text = response.bodyAsText()
        if (text.contains("exception")) {
            throw Exception("Moodle Error: $text")
        }
        return json.decodeFromString(text)
    }

    suspend fun getAssignments(siteUrl: String, token: String): MoodleAssignmentsResponse {
        val base = normalizeUrl(siteUrl)
        val response = httpClient.get("$base/webservice/rest/server.php") {
            parameter("wsfunction", "mod_assign_get_assignments")
            parameter("wstoken", token)
            parameter("moodlewsrestformat", "json")
        }
        val text = response.bodyAsText()
        if (text.contains("exception")) {
            throw Exception("Moodle Error: $text")
        }
        return json.decodeFromString(text)
    }

    suspend fun getSubmissionStatus(siteUrl: String, token: String, assignmentId: Int): MoodleSubmissionStatusResponse {
        val base = normalizeUrl(siteUrl)
        val response = httpClient.get("$base/webservice/rest/server.php") {
            parameter("wsfunction", "mod_assign_get_submission_status")
            parameter("wstoken", token)
            parameter("assignid", assignmentId)
            parameter("moodlewsrestformat", "json")
        }
        val text = response.bodyAsText()
        if (text.contains("exception")) {
            throw Exception("Moodle Error: $text")
        }
        return json.decodeFromString(text)
    }

    suspend fun getCourseContents(siteUrl: String, token: String, courseId: Int): List<MoodleSectionDto> {
        val base = normalizeUrl(siteUrl)
        val response = httpClient.get("$base/webservice/rest/server.php") {
            parameter("wsfunction", "core_course_get_contents")
            parameter("wstoken", token)
            parameter("courseid", courseId)
            parameter("moodlewsrestformat", "json")
        }
        val text = response.bodyAsText()
        if (text.contains("exception")) {
            throw Exception("Moodle Error: $text")
        }
        return json.decodeFromString(text)
    }

    suspend fun downloadFile(url: String, token: String): ByteArray {
        val response = httpClient.get(url) {
            parameter("token", token)
        }
        return response.readBytes()
    }

    suspend fun uploadFile(
        siteUrl: String,
        token: String,
        filename: String,
        fileBytes: ByteArray
    ): List<MoodleUploadResponse> {
        val base = normalizeUrl(siteUrl)
        val response = httpClient.post("$base/webservice/upload.php") {
            parameter("token", token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("filearea", "draft")
                        append("itemid", "0")
                        append("file", fileBytes, Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                        })
                    }
                )
            )
        }
        val text = response.bodyAsText()
        if (text.contains("exception") || text.contains("error")) {
            throw Exception("Moodle Upload Error: $text")
        }
        return json.decodeFromString(text)
    }

    suspend fun saveSubmission(
        siteUrl: String,
        token: String,
        assignmentId: Int,
        draftItemId: Int
    ) {
        val base = normalizeUrl(siteUrl)
        val response = httpClient.post("$base/webservice/rest/server.php") {
            parameter("wsfunction", "mod_assign_save_submission")
            parameter("wstoken", token)
            parameter("assignid", assignmentId)
            parameter("plugindata[files_filemanager]", draftItemId)
            parameter("moodlewsrestformat", "json")
        }
        val text = response.bodyAsText()
        if (text.contains("exception")) {
            throw Exception("Moodle Save Submission Error: $text")
        }
    }

    suspend fun submitForGrading(
        siteUrl: String,
        token: String,
        assignmentId: Int
    ) {
        val base = normalizeUrl(siteUrl)
        val response = httpClient.post("$base/webservice/rest/server.php") {
            parameter("wsfunction", "mod_assign_submit_for_grading")
            parameter("wstoken", token)
            parameter("assignid", assignmentId)
            parameter("acceptsubmissionstatement", 1)
            parameter("moodlewsrestformat", "json")
        }
        val text = response.bodyAsText()
        if (text.contains("exception")) {
            throw Exception("Moodle Submit for Grading Error: $text")
        }
    }
}

@Serializable
data class MoodleSiteInfo(
    val userid: Int,
    val username: String? = null,
    val fullname: String? = null
)

@Serializable
data class MoodleCourseDto(
    val id: Int,
    val shortname: String,
    val fullname: String,
    val summary: String? = null,
    val startdate: Long = 0,
    val enddate: Long = 0
)

@Serializable
data class MoodleAssignmentsResponse(
    val courses: List<MoodleCourseAssignmentsDto> = emptyList()
)

@Serializable
data class MoodleCourseAssignmentsDto(
    val id: Int,
    val assignments: List<MoodleAssignmentDto> = emptyList()
)

@Serializable
data class MoodleAttachmentDto(
    val filename: String,
    val fileurl: String,
    val filesize: Int
)

@Serializable
data class MoodleAssignmentDto(
    val id: Int,
    val cmid: Int,
    val course: Int,
    val name: String,
    val duedate: Long,
    val intro: String? = null,
    val introattachments: List<MoodleAttachmentDto>? = null
)

@Serializable
data class MoodleUploadResponse(
    val itemid: Int,
    val filename: String,
    val filepath: String? = null
)

@Serializable
data class MoodleSubmissionStatusResponse(
    val lastattempt: MoodleLastAttemptDto? = null,
    val feedback: MoodleFeedbackDto? = null
)

@Serializable
data class MoodleLastAttemptDto(
    val submission: MoodleSubmissionDto? = null
)

@Serializable
data class MoodleSubmissionDto(
    val id: Int,
    val status: String,
    val plugins: List<MoodleSubmissionPluginDto>? = null
)

@Serializable
data class MoodleSubmissionPluginDto(
    val type: String,
    val name: String,
    val fileareas: List<MoodleFileAreaDto>? = null
)

@Serializable
data class MoodleFileAreaDto(
    val area: String,
    val files: List<MoodleAttachmentDto>? = null
)

@Serializable
data class MoodleFeedbackDto(
    val grade: MoodleGradeDto? = null,
    val gradehtml: String? = null,
    val plugins: List<MoodleFeedbackPluginDto>? = null
)

@Serializable
data class MoodleGradeDto(
    val id: Int,
    val grade: String? = null,
    val grader: Int? = null,
    val timecreated: Long? = null,
    val timemodified: Long? = null
)

@Serializable
data class MoodleFeedbackPluginDto(
    val type: String,
    val name: String,
    val editorfields: List<MoodleEditorFieldDto>? = null
)

@Serializable
data class MoodleEditorFieldDto(
    val name: String,
    val text: String,
    val format: Int
)

@Serializable
data class MoodleSectionDto(
    val id: Int,
    val name: String,
    val modules: List<MoodleModuleDto>? = emptyList()
)

@Serializable
data class MoodleModuleDto(
    val id: Int,
    val name: String,
    val modname: String,
    val instance: Int? = null,
    val url: String? = null,
    val contents: List<MoodleContentFileDto>? = emptyList()
)

@Serializable
data class MoodleContentFileDto(
    val filename: String,
    val fileurl: String,
    val filesize: Int = 0
)
