package dev.dominikstahl.dhbwapp.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "moodle_courses")
data class CachedMoodleCourse(
    @PrimaryKey
    val id: Int,
    val shortName: String,
    val fullName: String,
    val summary: String?,
    val timeStart: Long,
    val timeEnd: Long
)
