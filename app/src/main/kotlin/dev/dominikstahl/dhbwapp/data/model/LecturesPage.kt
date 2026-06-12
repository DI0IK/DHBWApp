package dev.dominikstahl.dhbwapp.data.model

import java.time.LocalDate

/**
 * One week's worth of enriched lecture events, together with navigation flags
 * indicating whether adjacent weeks exist in the dataset.
 *
 * [weekMonday] is the Monday of the ISO week this page represents.
 * [events]     are already enriched and contain only events from this week.
 * [hasPrevious]/[hasNext] reflect whether the full cached dataset contains
 * events in the week before / after this one.
 */
data class LecturesPage(
    val weekMonday: LocalDate,
    val events: List<EnrichedLectureEvent>,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)
