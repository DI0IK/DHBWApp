package dev.dominikstahl.dhbwapp.data.repository

import dev.dominikstahl.dhbwapp.data.model.EnrichedLectureEvent
import dev.dominikstahl.dhbwapp.data.model.LecturesPage
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import dev.dominikstahl.dhbwapp.ui.lectures.LecturesViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class CalendarRepository(
    private val apiClient: ApiClient,
    private val enrichers: List<CalendarEnricher> = emptyList()
) {
    // -------------------------------------------------------------------------
    // Level-1 cache: raw API data, normalized (no enrichers run)
    // Key: "$site:$archived"
    // -------------------------------------------------------------------------
    private data class RawCacheEntry(val lectures: List<EnrichedLectureEvent>, val timestamp: Long)
    private val rawCache = ConcurrentHashMap<String, RawCacheEntry>()
    private val rawCacheTtlMs = 300_000L // 5 minutes

    // -------------------------------------------------------------------------
    // Level-2 cache: enriched events for a specific ISO week
    // Key: "$site:$archived:$weekMonday"
    // Populated lazily when getLecturesPage*() is called.
    // -------------------------------------------------------------------------
    private data class WeekEnrichedEntry(val events: List<EnrichedLectureEvent>, val timestamp: Long)
    private val enrichedWeekCache = ConcurrentHashMap<String, WeekEnrichedEntry>()
    private val enrichedWeekTtlMs = 300_000L // 5 minutes

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns the full normalized + cache-only-enriched event list for a site.
     *
     * Used by the **directory** (all lecturers/rooms/courses). Enrichers are
     * called in cache-only mode: they apply whatever Rapla data they already
     * have in memory but make **zero** outbound HTTP requests.
     */
    suspend fun getLectures(site: String, archived: Boolean = false): List<EnrichedLectureEvent> {
        val raw = getRawLectures(site, archived)
        return applyCacheOnlyEnrichers(raw)
    }

    /**
     * Returns one week's worth of events for a site, **fully enriched**
     * (the scraper will fetch that week's Rapla HTML if not already cached).
     */
    suspend fun getLecturesPage(
        site: String,
        date: LocalDate,
        archived: Boolean = false,
    ): LecturesPage {
        val raw = getRawLectures(site, archived)
        val page = buildPage(raw, mondayOf(date))
        val enriched = getOrEnrichWeek("$site:$archived", page.weekMonday, page.events)
        return page.copy(events = enriched)
    }

    /**
     * Returns one week's worth of events for a specific course, **fully enriched**.
     */
    suspend fun getLecturesPageForCourse(
        course: String,
        date: LocalDate,
        archived: Boolean = false,
    ): LecturesPage {
        val site = course.substringBefore("-")
        val raw = getRawLectures(site, archived).filter { e ->
            e.lecture.course.split(",").map { it.trim().lowercase() }
                .contains(course.trim().lowercase())
        }
        val page = buildPage(raw, mondayOf(date))
        val enriched = getOrEnrichWeek("$site:$archived", page.weekMonday, page.events)
        return page.copy(events = enriched)
    }

    /**
     * Builds a [LecturesPage] from a caller-supplied pre-filtered list and
     * **fully enriches** the resulting week slice.
     *
     * Used by [EntityTimetableViewModel] which pre-filters by entity type before
     * asking for the page.
     *
     * @param cacheKey  A unique string identifying the data source, used as the
     *   prefix for the enriched-week cache key. Typically "$site:$archived".
     */
    suspend fun buildAndEnrichPage(
        events: List<EnrichedLectureEvent>,
        cacheKey: String,
        date: LocalDate,
    ): LecturesPage {
        val page = buildPage(events, mondayOf(date))
        val enriched = getOrEnrichWeek("$cacheKey:entity", page.weekMonday, page.events)
        return page.copy(events = enriched)
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Fetches raw API data and normalizes it (encoding fixes).
     * No enrichers are called here.  Result is cached at level-1.
     */
    private suspend fun getRawLectures(site: String, archived: Boolean): List<EnrichedLectureEvent> {
        val cacheKey = "$site:$archived"
        val now = System.currentTimeMillis()
        rawCache[cacheKey]?.let { entry ->
            if (now - entry.timestamp < rawCacheTtlMs) {
                android.util.Log.d("CalendarRepository", "raw cache hit: $cacheKey")
                return entry.lectures
            }
        }
        android.util.Log.d("CalendarRepository", "raw cache miss: fetching $cacheKey")
        val raw = apiClient.getLectures(site, archived)
        val normalized = normalizeRaw(raw)
        rawCache[cacheKey] = RawCacheEntry(normalized, now)
        return normalized
    }

    /**
     * Returns the enriched events for [weekMonday], using the level-2 cache.
     * If the cache is empty or stale, runs the enrichers in **full** mode
     * (may trigger Rapla scraping for that week).
     */
    private suspend fun getOrEnrichWeek(
        prefix: String,
        weekMonday: LocalDate,
        events: List<EnrichedLectureEvent>,
    ): List<EnrichedLectureEvent> {
        val cacheKey = "$prefix:$weekMonday"
        val now = System.currentTimeMillis()
        enrichedWeekCache[cacheKey]?.let { entry ->
            if (now - entry.timestamp < enrichedWeekTtlMs) {
                android.util.Log.d("CalendarRepository", "enriched-week cache hit: $cacheKey")
                return entry.events
            }
        }
        android.util.Log.d("CalendarRepository", "enriched-week cache miss: enriching $cacheKey")
        val enriched = applyFullEnrichers(events)
        enrichedWeekCache[cacheKey] = WeekEnrichedEntry(enriched, now)
        return enriched
    }

    /** Runs all enrichers in cache-only mode (no outbound requests). */
    private suspend fun applyCacheOnlyEnrichers(
        events: List<EnrichedLectureEvent>,
    ): List<EnrichedLectureEvent> {
        var result = events
        for (enricher in enrichers) {
            result = enricher.enrich(result, cacheOnly = true)
        }
        return result
    }

    /** Runs all enrichers in full mode (may fetch missing data). */
    private suspend fun applyFullEnrichers(
        events: List<EnrichedLectureEvent>,
    ): List<EnrichedLectureEvent> {
        var result = events
        for (enricher in enrichers) {
            result = enricher.enrich(result, cacheOnly = false)
        }
        return result
    }

    /** Wraps raw API events into [EnrichedLectureEvent] with encoding normalized. */
    private fun normalizeRaw(rawEvents: List<RaplaLectureEvent>): List<EnrichedLectureEvent> {
        return rawEvents.map { event ->
            EnrichedLectureEvent(
                lecture = event.copy(
                    name = event.name.normalizeEncoding(),
                    lecturer = event.lecturer?.normalizeEncoding(),
                    rooms = event.rooms.map { it.normalizeEncoding() }.distinct(),
                    course = event.course.normalizeEncoding()
                )
            )
        }
    }

    private fun mondayOf(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    /**
     * Groups [events] by ISO-week Monday and returns the [LecturesPage] for
     * [targetMonday], clamping to the nearest available week if the exact one
     * has no events.
     */
    private fun buildPage(
        events: List<EnrichedLectureEvent>,
        targetMonday: LocalDate,
    ): LecturesPage {
        val byWeek = events
            .groupBy { e ->
                val d = LecturesViewModel.apiDateToLocalDate(e.lecture.date)
                    ?: return@groupBy null
                mondayOf(d)
            }
            .filterKeys { it != null }
            .mapKeys { it.key!! }
            .toSortedMap()

        val weeks = byWeek.keys.toList()

        if (weeks.isEmpty()) {
            return LecturesPage(
                weekMonday = targetMonday,
                events = emptyList(),
                hasPrevious = false,
                hasNext = false,
            )
        }

        val index = when {
            targetMonday <= weeks.first() -> 0
            targetMonday >= weeks.last()  -> weeks.lastIndex
            else -> {
                val exact = weeks.indexOf(targetMonday)
                if (exact >= 0) exact
                else weeks.indexOfFirst { it > targetMonday }
                    .let { if (it >= 0) it else weeks.lastIndex }
            }
        }

        return LecturesPage(
            weekMonday = weeks[index],
            events = byWeek[weeks[index]] ?: emptyList(),
            hasPrevious = index > 0,
            hasNext = index < weeks.lastIndex,
        )
    }

    // =========================================================================
    // Deduplication (unchanged helper, kept for future use)
    // =========================================================================

    @Suppress("unused")
    private fun deduplicateEvents(events: List<EnrichedLectureEvent>): List<EnrichedLectureEvent> {
        val grouped = events.groupBy { event ->
            val lecture = event.lecture
            val date = LecturesViewModel.apiDateToLocalDate(lecture.date)
            val startTime = lecture.startTime.substringAfter("T").take(5)
            val endTime = lecture.endTime.substringAfter("T").take(5)
            val name = lecture.name.normalizeEncoding().lowercase().trim()
            Triple(date, startTime to endTime, name)
        }
        return grouped.map { (_, group) ->
            if (group.size == 1) {
                group.first()
            } else {
                val base = group.first()
                base.copy(
                    lecture = base.lecture.copy(
                        rooms = group.flatMap { it.lecture.rooms }.map { it.normalizeEncoding() }.distinct(),
                        course = group.map { it.lecture.course }.map { it.normalizeEncoding() }.distinct().sorted().joinToString(", "),
                        lecturer = group.mapNotNull { it.lecture.lecturer?.normalizeEncoding() }.distinct().joinToString(", ").ifBlank { null }
                    ),
                    enrichments = group.flatMap { it.enrichments.entries }.associate { it.key to it.value }
                )
            }
        }
    }

    companion object {
        fun String.normalizeEncoding(): String {
            return this.replace("Ã¤", "ä")
                .replace("Ã¶", "ö")
                .replace("Ã¼", "ü")
                .replace("Ã", "Ä")
                .replace("Ã", "Ö")
                .replace("Ã", "Ü")
                .replace("Ã", "ß")
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
