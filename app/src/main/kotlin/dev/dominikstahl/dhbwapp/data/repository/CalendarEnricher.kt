package dev.dominikstahl.dhbwapp.data.repository

import dev.dominikstahl.dhbwapp.data.model.EnrichedLectureEvent

interface CalendarEnricher {
    /**
     * Enriches the given events.
     *
     * @param cacheOnly When true the enricher must NOT make any outbound network
     *   requests — it may only apply data that is already in its internal cache.
     *   Stale or missing cache entries are silently skipped.
     *   When false (default) the enricher may fetch missing data as needed.
     */
    suspend fun enrich(
        events: List<EnrichedLectureEvent>,
        cacheOnly: Boolean = false,
    ): List<EnrichedLectureEvent>
}
