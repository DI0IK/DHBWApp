package dev.dominikstahl.dhbwapp.data.repository

import dev.dominikstahl.dhbwapp.data.model.EnrichedLectureEvent
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent

class CalendarRepository(
    private val apiClient: ApiClient,
    private val enrichers: List<CalendarEnricher> = emptyList()
) {
    suspend fun getLecturesForCourse(course: String, archived: Boolean = false): List<EnrichedLectureEvent> {
        val raw = apiClient.getLecturesForCourse(course, archived)
        return applyEnrichment(raw)
    }

    suspend fun getLectures(site: String, archived: Boolean = false): List<EnrichedLectureEvent> {
        val raw = apiClient.getLectures(site, archived)
        return applyEnrichment(raw)
    }

    private suspend fun applyEnrichment(rawEvents: List<RaplaLectureEvent>): List<EnrichedLectureEvent> {
        var enriched = rawEvents.map { EnrichedLectureEvent(it) }
        enriched = deduplicateEvents(enriched)
        for (enricher in enrichers) {
            enriched = enricher.enrich(enriched)
        }
        return enriched
    }

    private fun deduplicateEvents(events: List<EnrichedLectureEvent>): List<EnrichedLectureEvent> {
        val grouped = events.groupBy { event ->
            val lecture = event.lecture
            val date = dev.dominikstahl.dhbwapp.ui.lectures.LecturesViewModel.apiDateToLocalDate(lecture.date)
            val startTime = lecture.startTime.substringAfter("T").take(5)
            val endTime = lecture.endTime.substringAfter("T").take(5)
            val name = lecture.name.normalizeEncoding().lowercase().trim()
            Triple(date, startTime to endTime, name)
        }

        return grouped.map { (_, group) ->
            if (group.size == 1) {
                val event = group.first()
                val lecture = event.lecture
                event.copy(
                    lecture = lecture.copy(
                        name = lecture.name.normalizeEncoding(),
                        lecturer = lecture.lecturer?.normalizeEncoding(),
                        rooms = lecture.rooms.map { it.normalizeEncoding() }.distinct(),
                        course = lecture.course.normalizeEncoding()
                    )
                )
            } else {
                val baseEvent = group.first()
                val baseLecture = baseEvent.lecture
                
                val mergedRooms = group.flatMap { it.lecture.rooms }.map { it.normalizeEncoding() }.distinct()
                val mergedCourses = group.map { it.lecture.course }.map { it.normalizeEncoding() }.distinct().sorted().joinToString(", ")
                val mergedLecturer = group.mapNotNull { it.lecture.lecturer?.normalizeEncoding() }.distinct().joinToString(", ").ifBlank { null }
                val mergedEnrichments = group.flatMap { it.enrichments.entries }.associate { it.key to it.value }

                baseEvent.copy(
                    lecture = baseLecture.copy(
                        name = baseLecture.name.normalizeEncoding(),
                        rooms = mergedRooms,
                        course = mergedCourses,
                        lecturer = mergedLecturer
                    ),
                    enrichments = mergedEnrichments
                )
            }
        }
    }

    companion object {
        fun String.normalizeEncoding(): String {
            return this.replace("Ã¤", "ä")
                .replace("Ã¶", "ö")
                .replace("Ã¼", "ü")
                .replace("Ã", "Ä")
                .replace("Ã", "Ö")
                .replace("Ã", "Ü")
                .replace("Ã", "ß")
                .replace("&#196;", "Ä")
                .replace("&#214;", "Ö")
                .replace("&#220;", "Ü")
                .replace("&#228;", "ä")
                .replace("&#246;", "ö")
                .replace("&#252;", "ü")
                .replace("&#223;", "ß")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
