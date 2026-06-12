package dev.dominikstahl.dhbwapp.data.repository

import dev.dominikstahl.dhbwapp.data.model.EnrichedLectureEvent
import dev.dominikstahl.dhbwapp.data.repository.CalendarRepository.Companion.normalizeEncoding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.jsoup.Jsoup
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

private data class ScrapedEvent(
    val date: LocalDate,
    val startTime: String,
    val endTime: String,
    val title: String,
    val lecturer: String?,
    val rooms: List<String>
)

private data class WeekCacheEntry(
    val events: List<ScrapedEvent>,
    val timestamp: Long,
)

class RaplaScraperCalendarEnricher(private val httpClient: HttpClient) : CalendarEnricher {

    /** Scraped events keyed by the Monday of each ISO week. */
    private val weekCache = ConcurrentHashMap<LocalDate, WeekCacheEntry>()

    /** Past weeks' HTML never changes — cache for 24 h. */
    private val PAST_WEEK_TTL_MS = 86_400_000L

    /** Current and future weeks may update — cache for 5 min. */
    private val LIVE_WEEK_TTL_MS = 300_000L

    private val berlinZone = java.time.ZoneId.of("Europe/Berlin")

    // -------------------------------------------------------------------------
    // CalendarEnricher impl
    // -------------------------------------------------------------------------

    override suspend fun enrich(
        events: List<EnrichedLectureEvent>,
        cacheOnly: Boolean,
    ): List<EnrichedLectureEvent> {
        if (events.isEmpty()) return events
        val isKa = events.any { it.lecture.site.equals("KA", ignoreCase = true) }
        if (!isKa) return events

        // Collect the unique ISO-week Mondays we need to cover
        val today = LocalDate.now()
        val neededMondays: Set<LocalDate> = events
            .mapNotNull { dev.dominikstahl.dhbwapp.ui.lectures.LecturesViewModel.apiDateToLocalDate(it.lecture.date) }
            .map { mondayOf(it) }
            .toSet()

        android.util.Log.d("RaplaScraper", "enrich: need ${neededMondays.size} week(s): $neededMondays")

        // For each needed week, return cached data or scrape fresh
        val allScraped = mutableListOf<ScrapedEvent>()
        for (monday in neededMondays) {
            val ttl = if (monday < mondayOf(today)) PAST_WEEK_TTL_MS else LIVE_WEEK_TTL_MS
            val now = System.currentTimeMillis()
            val cached = weekCache[monday]

            if (cacheOnly) {
                // Never make outbound requests — use whatever is in cache (even stale)
                if (cached != null) allScraped += cached.events
                else android.util.Log.d("RaplaScraper", "cacheOnly: no data for week $monday, skipping")
                continue
            }

            val weekEvents = if (cached != null && now - cached.timestamp < ttl) {
                android.util.Log.d("RaplaScraper", "Cache hit for week $monday")
                cached.events
            } else {
                try {
                    scrapeWeek(monday).also {
                        weekCache[monday] = WeekCacheEntry(it, now)
                        android.util.Log.d("RaplaScraper", "Scraped ${it.size} events for week $monday")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RaplaScraper", "Failed scraping week $monday: ${e.message}", e)
                    // Fall back to stale cache if available
                    if (cached != null) cached.events else emptyList()
                }
            }
            allScraped += weekEvents
        }

        return matchAndEnrich(events, allScraped)
    }

    // -------------------------------------------------------------------------
    // Per-week scraping
    // -------------------------------------------------------------------------

    private suspend fun scrapeWeek(monday: LocalDate): List<ScrapedEvent> {
        val url = "https://rapla.dhbw-karlsruhe.de/rapla?page=calendar&user=roethig&page=default" +
                "&day=${monday.dayOfMonth}&month=${monday.monthValue}&year=${monday.year}"
        android.util.Log.d("RaplaScraper", "Fetching: $url")

        val html = httpClient.get(url).bodyAsText()
        android.util.Log.d("RaplaScraper", "Fetched ${html.length} chars for week $monday")

        val doc = Jsoup.parse(html)

        // Build the column-date map from week headers
        val headerElements = doc.select("th.week_header, td.week_header")
        val columnDates = mutableListOf<LocalDate>()
        for (header in headerElements) {
            val date = parseRaplaHeaderDate(header.text()) ?: continue
            val colspan = header.attr("colspan").toIntOrNull() ?: 1
            repeat(colspan) { columnDates.add(date) }
        }

        if (columnDates.isEmpty()) {
            android.util.Log.w("RaplaScraper", "No column dates parsed for week $monday")
            return emptyList()
        }

        val parsedList = mutableListOf<ScrapedEvent>()
        val colCount = columnDates.size
        val rowspanRemaining = IntArray(colCount) { 0 }

        val rows = doc.select("table.week_table tr").filter { row ->
            row.parents().none { it.hasClass("tooltip") || it.hasClass("infotable") }
        }

        for (row in rows) {
            if (row.select("td.week_header, th.week_header").isNotEmpty()) continue
            val cells = row.children()
            if (cells.isEmpty()) continue

            var cellIndex = 0
            var colIndex = 0

            val firstCell = cells.firstOrNull()
            if (firstCell != null &&
                (firstCell.hasClass("week_time") || firstCell.hasClass("week_times") ||
                        firstCell.text().contains(":"))) {
                cellIndex = 1
            }

            while (colIndex < colCount) {
                if (rowspanRemaining[colIndex] > 0) {
                    rowspanRemaining[colIndex]--
                    colIndex++
                    continue
                }
                if (cellIndex >= cells.size) break

                val td = cells[cellIndex]
                val rowspan = td.attr("rowspan").toIntOrNull() ?: 1
                val colspan = td.attr("colspan").toIntOrNull() ?: 1

                if (td.hasClass("week_block")) {
                    val date = columnDates.getOrNull(colIndex)
                    if (date != null) {
                        val tooltip = td.select(".tooltip")

                        val title = tooltip.select("tr:contains(Titel) td.value").text().ifBlank {
                            td.select("a").firstOrNull()
                                ?.text()
                                ?.replace(Regex("^[0-9:\\s\u00A0&#160;-]+"), "")
                                ?.trim() ?: ""
                        }

                        val lecturer = tooltip.select("tr:contains(Personen) td.value").text()
                            .ifBlank { td.select(".person").text() }
                            .ifBlank { null }

                        val roomsText = tooltip.select("tr:contains(Ressourcen) td.value").text()
                        val rooms = if (roomsText.isNotBlank()) {
                            roomsText.split(",").map { it.trim() }
                        } else {
                            td.select(".resource").map { it.text().trim() }
                        }

                        val normalizedText = td.text().replace(Regex("[\\s\u00A0]+"), " ")
                        val timeRegex = Regex("(\\d{2}:\\d{2})\\s*-\\s*(\\d{2}:\\d{2})")
                        val match = timeRegex.find(normalizedText)
                        val startTime = match?.groupValues?.get(1)
                        val endTime = match?.groupValues?.get(2)

                        if (startTime != null && endTime != null) {
                            parsedList.add(
                                ScrapedEvent(
                                    date = date,
                                    startTime = startTime,
                                    endTime = endTime,
                                    title = title.normalizeEncoding(),
                                    lecturer = lecturer?.normalizeEncoding(),
                                    rooms = rooms.map { it.normalizeEncoding() }
                                )
                            )
                        }
                    }
                }

                for (i in 0 until colspan) {
                    if (colIndex + i < colCount) {
                        rowspanRemaining[colIndex + i] = rowspan - 1
                    }
                }
                cellIndex++
                colIndex += colspan
            }
        }

        return parsedList
    }

    // -------------------------------------------------------------------------
    // Matching / enrichment
    // -------------------------------------------------------------------------

    private fun matchAndEnrich(
        events: List<EnrichedLectureEvent>,
        scraped: List<ScrapedEvent>,
    ): List<EnrichedLectureEvent> {
        android.util.Log.d("RaplaScraper", "Matching ${events.size} API events against ${scraped.size} scraped events")
        return events.map { event ->
            val lecture = event.lecture
            val eventDate = dev.dominikstahl.dhbwapp.ui.lectures.LecturesViewModel.apiDateToLocalDate(lecture.date)
                ?: return@map event

            val match = scraped.find { s ->
                s.date == eventDate &&
                        formatTimeMatches(lecture.startTime, s.startTime) &&
                        formatTimeMatches(lecture.endTime, s.endTime) &&
                        s.title.lowercase().trim() == lecture.name.normalizeEncoding().lowercase().trim()
            } ?: return@map event

            val enrichedLecturer = if (lecture.lecturer.isNullOrBlank() && !match.lecturer.isNullOrBlank()) {
                match.lecturer
            } else {
                lecture.lecturer
            }

            android.util.Log.d(
                "RaplaScraper",
                "MATCH: '${lecture.name}' on $eventDate — lecturer: '${lecture.lecturer}' → '${enrichedLecturer}'"
            )

            event.copy(
                lecture = lecture.copy(lecturer = enrichedLecturer),
                enrichments = event.enrichments + ("rapla_scraped" to true)
            )
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private fun mondayOf(date: LocalDate): LocalDate =
        date.with(DayOfWeek.MONDAY)

    private fun getLocalTime(isoTime: String): String {
        return try {
            java.time.OffsetDateTime.parse(isoTime)
                .atZoneSameInstant(berlinZone)
                .toLocalTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            isoTime.take(5)
        }
    }

    private fun formatTimeMatches(apiTime: String, scrapedTime: String): Boolean =
        getLocalTime(apiTime) == scrapedTime

    private fun parseRaplaHeaderDate(text: String): LocalDate? {
        val clean = text.replace(Regex("^[^0-9]*"), "").trim()
        val parts = clean.split(".")
        if (parts.size >= 2) {
            val day = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val year = if (parts.size >= 3 && parts[2].isNotBlank()) {
                val y = parts[2].trim().toIntOrNull() ?: return null
                if (y < 100) 2000 + y else y
            } else {
                LocalDate.now().year
            }
            return LocalDate.of(year, month, day)
        }
        return null
    }
}
