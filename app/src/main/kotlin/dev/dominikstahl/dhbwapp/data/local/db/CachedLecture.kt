package dev.dominikstahl.dhbwapp.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent

@Entity(tableName = "lectures")
data class CachedLecture(
    @PrimaryKey
    val id: Int,
    val date: String,
    val site: String,
    val startTime: String,
    val endTime: String,
    val name: String,
    val type: String,
    val lecturer: String?,
    val rooms: List<String>,
    val course: String
) {
    fun toRaplaLectureEvent(): RaplaLectureEvent {
        return RaplaLectureEvent(
            id = id,
            date = date,
            site = site,
            startTime = startTime,
            endTime = endTime,
            name = name,
            type = type,
            lecturer = lecturer,
            rooms = rooms,
            course = course
        )
    }

    companion object {
        fun fromRaplaLectureEvent(event: RaplaLectureEvent): CachedLecture {
            return CachedLecture(
                id = event.id,
                date = event.date,
                site = event.site,
                startTime = event.startTime,
                endTime = event.endTime,
                name = event.name,
                type = event.type,
                lecturer = event.lecturer,
                rooms = event.rooms,
                course = event.course
            )
        }
    }
}
