package dev.dominikstahl.dhbwapp

import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.data.repository.CalendarRepository
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import java.time.LocalDate

class CalendarRepositoryTest {

    @Test
    @Ignore("Deduplication disabled for now")
    fun testCalendarRepositoryDeduplication() = runBlocking {
        val sampleLectures = listOf(
            RaplaLectureEvent(
                id = 1,
                date = "2026-06-12T00:00:00Z",
                site = "Karlsruhe",
                startTime = "08:30",
                endTime = "11:45",
                name = "Software Engineering",
                type = "Lecture",
                rooms = listOf("E209"),
                course = "TINF25B5",
                lecturer = "Dr. Müller"
            ),
            RaplaLectureEvent(
                id = 2,
                date = "2026-06-12T00:00:00Z",
                site = "Karlsruhe",
                startTime = "08:30",
                endTime = "11:45",
                name = "Software Engineering",
                type = "Lecture",
                rooms = listOf("E209"),
                course = "TINF25B6",
                lecturer = "Dr. Müller"
            )
        )

        val json = Json { ignoreUnknownKeys = true }
        val responseBody = json.encodeToString(sampleLectures)

        val mockEngine = MockEngine { _ ->
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        val apiClient = ApiClient(httpClient)
        val repository = CalendarRepository(apiClient)

        val result = repository.getLecturesPageForCourse("TINF25B6", LocalDate.of(2026, 6, 12))
        val enriched = result.events

        assertEquals(1, enriched.size)

        val mergedEvent = enriched.first().lecture
        assertEquals("TINF25B5, TINF25B6", mergedEvent.course)
        assertEquals("E209", mergedEvent.rooms.first())
    }
}
