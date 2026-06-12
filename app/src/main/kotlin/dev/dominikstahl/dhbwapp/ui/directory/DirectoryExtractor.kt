package dev.dominikstahl.dhbwapp.ui.directory

import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent

object DirectoryExtractor {
    fun extractLecturers(lectures: List<RaplaLectureEvent>): List<String> {
        return lectures.mapNotNull { it.lecturer }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    fun extractRooms(lectures: List<RaplaLectureEvent>): List<String> {
        return lectures.flatMap { it.rooms }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    fun extractCourses(lectures: List<RaplaLectureEvent>): List<String> {
        return lectures.mapNotNull { it.course }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
}
