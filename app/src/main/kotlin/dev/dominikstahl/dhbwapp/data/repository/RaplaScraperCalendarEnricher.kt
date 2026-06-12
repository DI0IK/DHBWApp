package dev.dominikstahl.dhbwapp.data.repository

import dev.dominikstahl.dhbwapp.data.model.EnrichedLectureEvent
import dev.dominikstahl.dhbwapp.data.repository.CalendarRepository.Companion.normalizeEncoding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.jsoup.Jsoup
import java.time.LocalDate

private data class ScrapedEvent(
    val date: LocalDate,
    val startTime: String,
    val endTime: String,
    val title: String,
    val lecturer: String?,
    val rooms: List<String>
)

class RaplaScraperCalendarEnricher(private val httpClient: HttpClient) : CalendarEnricher {
    private var cachedEvents: List<ScrapedEvent>? = null
    private var lastFetchedTime: Long = 0

    override suspend fun enrich(events: List<EnrichedLectureEvent>): List<EnrichedLectureEvent> {
        val currentTime = System.currentTimeMillis()
        val scrapedEvents = if (cachedEvents != null && (currentTime - lastFetchedTime) < 300_000) {
            cachedEvents!!
        } else {
            val raplaUrl = "https://rapla.dhbw-karlsruhe.de/rapla?page=calendar&user=roethig&page=default"
            val html = try {
                httpClient.get(raplaUrl).bodyAsText()
            } catch (e: Exception) {
                return events
            }
            val doc = Jsoup.parse(html)
            
            val headerElements = doc.select("th.week_header, td.week_header")
            val columnDates = headerElements.mapNotNull { header ->
                parseRaplaHeaderDate(header.text())
            }
            
            if (columnDates.isEmpty()) return events
            
            val parsedList = mutableListOf<ScrapedEvent>()
            val rows = doc.select("tr")
            val colCount = columnDates.size
            val rowspanRemaining = IntArray(colCount) { 0 }

            for (row in rows) {
                if (row.select("th").isNotEmpty()) continue
                val tds = row.select("td")
                if (tds.isEmpty()) continue
                
                var tdIndex = 0
                var colIndex = 0
                
                val firstTd = tds.firstOrNull()
                if (firstTd != null && (firstTd.hasClass("week_time") || firstTd.text().contains(":"))) {
                    tdIndex = 1
                }
                
                while (colIndex < colCount) {
                    if (rowspanRemaining[colIndex] > 0) {
                        rowspanRemaining[colIndex]--
                        colIndex++
                        continue
                    }
                    
                    if (tdIndex >= tds.size) break
                    
                    val tdActual = tds[tdIndex]
                    val rowspan = tdActual.attr("rowspan").toIntOrNull() ?: 1
                    
                    if (tdActual.hasClass("week_block")) {
                        val date = columnDates.getOrNull(colIndex)
                        if (date != null) {
                            val tooltip = tdActual.select(".tooltip")
                            val title = tooltip.select("tr:contains(Titel) td.value").text().ifBlank {
                                tdActual.select("a").firstOrNull()?.text()?.replace(Regex("^[0-9:\\s&#160;-]+"), "")?.trim() ?: ""
                            }
                            val lecturer = tooltip.select("tr:contains(Personen) td.value").text().ifBlank {
                                tdActual.select(".person").text()
                            }.ifBlank { null }
                            
                            val roomsText = tooltip.select("tr:contains(Ressourcen) td.value").text()
                            val rooms = if (roomsText.isNotBlank()) {
                                roomsText.split(",").map { it.trim() }
                            } else {
                                tdActual.select(".resource").map { it.text().trim() }
                            }
                            
                            val timeRegex = Regex("(\\d{2}:\\d{2})\\s*-\\s*(\\d{2}:\\d{2})")
                            val match = timeRegex.find(tdActual.text())
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
                    rowspanRemaining[colIndex] = rowspan - 1
                    tdIndex++
                    colIndex++
                }
            }
            
            cachedEvents = parsedList
            lastFetchedTime = currentTime
            parsedList
        }

        // Match with dhbw.app events and enrich them
        return events.map { event ->
            val lecture = event.lecture
            val eventDate = dev.dominikstahl.dhbwapp.ui.lectures.LecturesViewModel.apiDateToLocalDate(lecture.date)
            
            if (eventDate != null) {
                val match = scrapedEvents.find { scraped ->
                    scraped.date == eventDate &&
                    formatTimeMatches(lecture.startTime, scraped.startTime) &&
                    formatTimeMatches(lecture.endTime, scraped.endTime) &&
                    scraped.title.lowercase().trim() == lecture.name.normalizeEncoding().lowercase().trim()
                }
                
                if (match != null) {
                    val enrichedLecturer = if (lecture.lecturer.isNullOrBlank() && !match.lecturer.isNullOrBlank()) {
                        match.lecturer
                    } else {
                        lecture.lecturer
                    }
                    
                    return@map event.copy(
                        lecture = lecture.copy(lecturer = enrichedLecturer),
                        enrichments = event.enrichments + ("rapla_scraped" to true)
                    )
                }
            }
            event
        }
    }

    private fun formatTimeMatches(apiTime: String, scrapedTime: String): Boolean {
        val cleanApi = if (apiTime.contains("T")) {
            apiTime.substringAfter("T").take(5)
        } else {
            apiTime.take(5)
        }
        return cleanApi == scrapedTime
    }

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
