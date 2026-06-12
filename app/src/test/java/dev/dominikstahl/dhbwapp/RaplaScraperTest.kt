package dev.dominikstahl.dhbwapp

import dev.dominikstahl.dhbwapp.data.model.EnrichedLectureEvent
import dev.dominikstahl.dhbwapp.data.repository.RaplaScraperCalendarEnricher
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RaplaScraperTest {

    @Test
    fun testScrapingAndEnrichment() = runBlocking {
        val mockHtml = """
            <html>
            <body>
            <table class="week_table">
              <thead>
                <tr>
                  <th class="week_header">Mo 08.06.26</th>
                  <th class="week_header">Di 09.06.26</th>
                  <th class="week_header">Mi 10.06.26</th>
                  <th class="week_header">Do 11.06.26</th>
                  <th class="week_header">Fr 12.06.26</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="week_time">08:00</td>
                  <td class="week_emptycell"></td>
                  <td class="week_emptycell"></td>
                  <td class="week_emptycell"></td>
                  <td valign="top" class="week_block" rowspan="14" style="background-color:#b0c2c9">
                    <a href="#1022">08:30&#160;-11:45<br/>Algorithm and complexity
                      <span class="tooltip">
                        <div>Do 08:30-11:45 wöchentlich</div>
                        <strong>Lehrveranstaltung</strong>
                        <table class="infotable" cellpadding="1">
                          <tr><td class="label">Titel:</td><td class="value">Algorithm and complexity</td></tr>
                          <tr><td class="label">Personen:</td><td class="value">Li, Nuo</td></tr>
                          <tr><td class="label">Ressourcen:</td><td class="value">International Students,E209,TINF25B6</td></tr>
                        </table>
                      </span>
                    </a>
                  </td>
                  <td class="week_emptycell"></td>
                </tr>
              </tbody>
            </table>
            </body>
            </html>
        """.trimIndent()

        val mockEngine = MockEngine { _ ->
            respond(
                content = mockHtml,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8")
            )
        }
        val httpClient = HttpClient(mockEngine)
        val enricher = RaplaScraperCalendarEnricher(httpClient)

        val apiEvent = RaplaLectureEvent(
            id = 123,
            date = "2026-06-11T00:00:00Z",
            site = "KA",
            startTime = "2026-06-11T06:30:00Z",
            endTime = "2026-06-11T09:45:00Z",
            name = "Algorithm and complexity",
            type = "Lecture",
            rooms = listOf("E209"),
            course = "TINF25B6",
            lecturer = "" 
        )

        val enriched = enricher.enrich(listOf(EnrichedLectureEvent(apiEvent)))

        assertEquals(1, enriched.size)
        assertEquals("Li, Nuo", enriched.first().lecture.lecturer)
        assertEquals(true, enriched.first().enrichments["rapla_scraped"])
    }

    @Test
    fun testRealRaplaHtmlStructure() = runBlocking {
        val mockHtml = """
            <html>
            <body>
            <table class="week_table">
              <tbody>
                <tr>
                  <th class="week_number">KW 24</th>
                  <td class="week_header" colspan="3"><nobr>Mo 08.06.</nobr></td>
                  <td class="week_header" colspan="3"><nobr>Di 09.06.</nobr></td>
                  <td class="week_header" colspan="3"><nobr>Mi 10.06.</nobr></td>
                  <td class="week_header" colspan="3"><nobr>Do 11.06.</nobr></td>
                  <td class="week_header" colspan="3"><nobr>Fr 12.06.</nobr></td>
                  <td class="week_header" colspan="3"><nobr>Sa 13.06.</nobr></td>
                  <td class="week_header" colspan="3"><nobr>So 14.06.</nobr></td>
                </tr>
                <tr>
                  <th class="week_times" rowspan="4"><nobr>8:00</nobr> &nbsp;</th>
                  <td class="week_smallseparatorcell_black">&nbsp;</td><td class="week_emptycell_black">&nbsp;</td><td class="week_separatorcell_black">&nbsp;</td>
                  <td class="week_smallseparatorcell_black">&nbsp;</td><td class="week_emptycell_black">&nbsp;</td><td class="week_separatorcell_black">&nbsp;</td>
                  <td class="week_smallseparatorcell_black">&nbsp;</td>
                  <td valign="top" class="week_block" rowspan="17" style="background-color: rgb(238, 238, 238);">
                    <a href="#5">08:00&nbsp;-12:15<br>IT Security
                      <span class="tooltip">
                        <div>Mi 08:00-12:15 wöchentlich</div>
                        <strong>Lehrveranstaltung</strong>
                        <table class="infotable" cellpadding="1">
                          <tbody>
                            <tr><td class="label">Titel:</td><td class="value">IT Security</td></tr>
                            <tr><td class="label">Personen:</td><td class="value">Budurushi, Jurlind</td></tr>
                            <tr><td class="label">Ressourcen:</td><td class="value">International Students,TINF24B6,A266</td></tr>
                          </tbody>
                        </table>
                      </span>
                    </a>
                  </td>
                  <td class="week_separatorcell_black">&nbsp;</td>
                  <td class="week_smallseparatorcell_black">&nbsp;</td><td class="week_emptycell_black">&nbsp;</td><td class="week_separatorcell_black">&nbsp;</td>
                  <td class="week_smallseparatorcell_black">&nbsp;</td><td class="week_emptycell_black">&nbsp;</td><td class="week_separatorcell_black">&nbsp;</td>
                  <td class="week_smallseparatorcell_black">&nbsp;</td><td class="week_emptycell_black">&nbsp;</td><td class="week_separatorcell_black">&nbsp;</td>
                  <td class="week_smallseparatorcell_black">&nbsp;</td><td class="week_emptycell_black">&nbsp;</td><td class="week_separatorcell_black">&nbsp;</td>
                </tr>
              </tbody>
            </table>
            </body>
            </html>
        """.trimIndent()

        val mockEngine = MockEngine { _ ->
            respond(
                content = mockHtml,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8")
            )
        }
        val httpClient = HttpClient(mockEngine)
        val enricher = RaplaScraperCalendarEnricher(httpClient)

        val apiEvent = RaplaLectureEvent(
            id = 456,
            date = "2026-06-10T00:00:00Z", // Wednesday
            site = "KA",
            startTime = "2026-06-10T06:00:00Z", // 08:00 CEST (UTC+2)
            endTime = "2026-06-10T10:15:00Z", // 12:15 CEST
            name = "IT Security",
            type = "Lecture",
            rooms = listOf("A266"),
            course = "TINF24B6",
            lecturer = ""
        )

        val enriched = enricher.enrich(listOf(EnrichedLectureEvent(apiEvent)))

        assertEquals(1, enriched.size)
        assertEquals("Budurushi, Jurlind", enriched.first().lecture.lecturer)
        assertEquals(true, enriched.first().enrichments["rapla_scraped"])
    }
}
