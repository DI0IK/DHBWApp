package dev.dominikstahl.dhbwapp

import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import dev.dominikstahl.dhbwapp.ui.directory.DirectoryExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectoryExtractorTest {

    private val sampleLectures = listOf(
        RaplaLectureEvent(
            id = 1,
            date = "2026-06-12T08:00:00Z",
            site = "Stuttgart",
            startTime = "08:00",
            endTime = "09:30",
            name = "Mathematics",
            type = "Lecture",
            lecturer = "Prof. Dr. Schmidt",
            rooms = listOf("Raum 101", "Raum 102"),
            course = "TINF21B1"
        ),
        RaplaLectureEvent(
            id = 2,
            date = "2026-06-12T10:00:00Z",
            site = "Stuttgart",
            startTime = "10:00",
            endTime = "11:30",
            name = "Physics",
            type = "Lecture",
            lecturer = "Dr. Meier",
            rooms = listOf("Raum 102"),
            course = "TINF21B2"
        ),
        RaplaLectureEvent(
            id = 3,
            date = "2026-06-12T12:00:00Z",
            site = "Stuttgart",
            startTime = "12:00",
            endTime = "13:30",
            name = "Chemistry",
            type = "Lecture",
            lecturer = "Prof. Dr. Schmidt", // Duplicate lecturer
            rooms = listOf(""), // Blank room
            course = "TINF21B1" // Duplicate course
        ),
        RaplaLectureEvent(
            id = 4,
            date = "2026-06-12T14:00:00Z",
            site = "Stuttgart",
            startTime = "14:00",
            endTime = "15:30",
            name = "Computer Science",
            type = "Lecture",
            lecturer = "", // Blank lecturer
            rooms = listOf("Raum 103"),
            course = "" // Blank course
        )
    )

    @Test
    fun testExtractLecturers() {
        val result = DirectoryExtractor.extractLecturers(sampleLectures)
        // Schmidt and Meier, sorted, duplicates and blanks removed
        assertEquals(2, result.size)
        assertEquals("Dr. Meier", result[0])
        assertEquals("Prof. Dr. Schmidt", result[1])
    }

    @Test
    fun testExtractRooms() {
        val result = DirectoryExtractor.extractRooms(sampleLectures)
        // 101, 102, 103. Duplicates and blanks removed, sorted
        assertEquals(3, result.size)
        assertEquals("Raum 101", result[0])
        assertEquals("Raum 102", result[1])
        assertEquals("Raum 103", result[2])
    }

    @Test
    fun testExtractCourses() {
        val result = DirectoryExtractor.extractCourses(sampleLectures)
        // TINF21B1, TINF21B2. Duplicates and blanks removed, sorted
        assertEquals(2, result.size)
        assertEquals("TINF21B1", result[0])
        assertEquals("TINF21B2", result[1])
    }
}
