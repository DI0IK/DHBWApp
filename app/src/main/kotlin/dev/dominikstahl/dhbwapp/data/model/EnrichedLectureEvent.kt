package dev.dominikstahl.dhbwapp.data.model

import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent

data class EnrichedLectureEvent(
    val lecture: RaplaLectureEvent,
    val enrichments: Map<String, Any> = emptyMap()
)
