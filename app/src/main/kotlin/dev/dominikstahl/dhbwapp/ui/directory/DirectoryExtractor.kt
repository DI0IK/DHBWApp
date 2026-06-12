package dev.dominikstahl.dhbwapp.ui.directory

import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent

object DirectoryExtractor {
    fun parseLecturerNames(lecturersStr: String?): List<String> {
        if (lecturersStr.isNullOrBlank()) return emptyList()
        val parts = lecturersStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        var i = 0
        while (i < parts.size) {
            if (i + 1 < parts.size) {
                result.add("${parts[i]}, ${parts[i+1]}")
                i += 2
            } else {
                result.add(parts[i])
                i++
            }
        }
        return result
    }

    fun extractLecturers(lectures: List<RaplaLectureEvent>): List<String> {
        return lectures.flatMap { parseLecturerNames(it.lecturer) }
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
