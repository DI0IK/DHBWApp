package dev.dominikstahl.dhbwapp.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "moodle_course_contents")
data class CachedMoodleContent(
    @PrimaryKey
    val id: Int, // cmid (course module ID)
    val courseId: Int,
    val sectionName: String,
    val name: String,
    val type: String, // "resource", "url", "folder", "page"
    val url: String?, // download URL or link URL
    val fileSize: Int,
    val instanceId: Int? = null
)
