package dev.dominikstahl.dhbwapp

import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import dev.dominikstahl.dhbwapp.ui.directory.DirectoryExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryExtractorTest {

    // -------------------------------------------------------------------------
    // cleanLecturers() test cases
    // -------------------------------------------------------------------------

    @Test
    fun cleanLecturers_normalEntry() {
        val result = DirectoryExtractor.cleanLecturers("Kirchhoff, Dietrich")
        assertEquals(listOf("Kirchhoff, Dietrich"), result)
    }

    @Test
    fun cleanLecturers_mashedMultipleLecturers() {
        // "Becker, Holger,Möbius, Christian" -> two names
        val result = DirectoryExtractor.cleanLecturers("Becker, Holger,Möbius, Christian")
        assertEquals(listOf("Becker, Holger", "Möbius, Christian"), result)
    }

    @Test
    fun cleanLecturers_injectedScheduleData() {
        val input = "Hirsch, Julia (Mi 27.05.26 15:00),Renker, Lisa (Fr 19.06.26 08:30)"
        val result = DirectoryExtractor.cleanLecturers(input)
        assertEquals(listOf("Hirsch, Julia", "Renker, Lisa"), result)
    }

    @Test
    fun cleanLecturers_literalNullString() {
        val result = DirectoryExtractor.cleanLecturers("null")
        assertTrue(result.isEmpty())
    }

    @Test
    fun cleanLecturers_actualNull() {
        val result = DirectoryExtractor.cleanLecturers(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun cleanLecturers_blankString() {
        val result = DirectoryExtractor.cleanLecturers("   ")
        assertTrue(result.isEmpty())
    }

    // -------------------------------------------------------------------------
    // extractLecturers / extractRooms / extractCourses integration tests
    // -------------------------------------------------------------------------

    private val sampleLectures = listOf(
        RaplaLectureEvent(
            id = 1,
            date = "2026-06-12T08:00:00Z",
            site = "Stuttgart",
            startTime = "08:00",
            endTime = "09:30",
            name = "Mathematics",
            type = "Lecture",
            lecturer = "Schmidt, Markus",
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
            lecturer = "Meier, Anna",
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
            // Compound mashed entry: two lecturers, one of which is a duplicate of lecture 1
            lecturer = "Schmidt, Markus,Bauer, Julia",
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
        ),
        RaplaLectureEvent(
            id = 5,
            date = "2026-06-12T16:00:00Z",
            site = "Stuttgart",
            startTime = "16:00",
            endTime = "17:30",
            name = "Art",
            type = "Lecture",
            lecturer = "null", // Literal null string
            rooms = listOf("Raum 104"),
            course = "TINF21B3"
        )
    )

    @Test
    fun testExtractLecturers() {
        val result = DirectoryExtractor.extractLecturers(sampleLectures)
        // Bauer, Meier, Schmidt - sorted, duplicates and blanks/nulls removed
        assertEquals(3, result.size)
        assertEquals("Bauer, Julia", result[0])
        assertEquals("Meier, Anna", result[1])
        assertEquals("Schmidt, Markus", result[2])
    }

    @Test
    fun testExtractRooms() {
        val result = DirectoryExtractor.extractRooms(sampleLectures)
        // 101, 102, 103, 104. Duplicates and blanks removed, sorted
        assertEquals(4, result.size)
        assertEquals("Raum 101", result[0])
        assertEquals("Raum 102", result[1])
        assertEquals("Raum 103", result[2])
        assertEquals("Raum 104", result[3])
    }

    @Test
    fun testExtractCourses() {
        val result = DirectoryExtractor.extractCourses(sampleLectures)
        // TINF21B1, TINF21B2, TINF21B3. Duplicates and blanks removed, sorted
        assertEquals(3, result.size)
        assertEquals("TINF21B1", result[0])
        assertEquals("TINF21B2", result[1])
        assertEquals("TINF21B3", result[2])
    }
}
