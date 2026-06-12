package dev.dominikstahl.dhbwapp.ui.directory

import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent

object DirectoryExtractor {

    /**
     * Cleans a raw lecturer string from the scraper, handling three known inconsistencies:
     *  1. Literal "null" strings returned by the API.
     *  2. Parenthesized schedule data like "(Mi 27.05.26 15:00...)" injected into the value.
     *  3. Mashed-up multi-lecturer strings without spaces after delimiters
     *     (e.g. "Becker, Holger,Möbius, Christian") where names are "LastName, FirstName" pairs.
     *
     * @return A list of individual lecturer names in "LastName, FirstName" format,
     *         or an empty list if the input is null/blank/"null".
     */
    fun cleanLecturers(rawScraped: String?): List<String> {
        // 1. Handle actual nulls, empty strings, and the literal "null" string
        if (rawScraped.isNullOrBlank() || rawScraped.trim() == "null") {
            return emptyList()
        }

        // 2. Strip out all schedule data inside parentheses
        //    e.g. "Hirsch, Julia (Mi 27.05...),Renker, Lisa" -> "Hirsch, Julia ,Renker, Lisa"
        val withoutSchedules = rawScraped.replace(Regex("\\([^)]*\\)"), "")

        // 3. Split by comma to get all individual name parts
        val parts = withoutSchedules.split(",")

        // 4. Clean, pair up (chunk), and format the names.
        //    Names are stored as "LastName, FirstName" so every two comma-parts form one person.
        return parts
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .chunked(2)
            .map { chunk ->
                if (chunk.size == 2) {
                    "${chunk[0]}, ${chunk[1]}"
                } else {
                    chunk.first()
                }
            }
    }

    fun extractLecturers(lectures: List<RaplaLectureEvent>): List<String> {
        return lectures
            .flatMap { cleanLecturers(it.lecturer) }
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
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
}
