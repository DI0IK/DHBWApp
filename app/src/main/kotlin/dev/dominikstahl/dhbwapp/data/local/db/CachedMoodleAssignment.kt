package dev.dominikstahl.dhbwapp.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class CachedMoodleAttachment(
    val filename: String,
    val fileurl: String,
    val filesize: Int
)

@Entity(tableName = "moodle_assignments")
data class CachedMoodleAssignment(
    @PrimaryKey
    val id: Int, // Instance ID
    val cmid: Int, // Course module ID
    val courseId: Int,
    val name: String,
    val dueDate: Long, // Unix timestamp in seconds
    val description: String?, // Intro HTML
    val isSubmitted: Boolean = false,
    val statusText: String? = null,
    val attachments: List<CachedMoodleAttachment> = emptyList(),
    val submittedFiles: List<CachedMoodleAttachment> = emptyList(),
    val feedbackComments: String? = null,
    val feedbackGrade: String? = null
)
