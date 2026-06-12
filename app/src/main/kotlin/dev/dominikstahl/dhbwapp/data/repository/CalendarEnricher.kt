package dev.dominikstahl.dhbwapp.data.repository

import dev.dominikstahl.dhbwapp.data.model.EnrichedLectureEvent

interface CalendarEnricher {
    suspend fun enrich(events: List<EnrichedLectureEvent>): List<EnrichedLectureEvent>
}
