package dev.dominikstahl.dhbwapp.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.nio.charset.Charset

class DualisClient(private val httpClient: HttpClient) {

    companion object {
        private const val BASE_URL = "https://dualis.dhbw.de/scripts/mgrqispi.dll"
    }

    suspend fun login(email: String, password: String): DualisSession {
        // Construct URL-encoded body
        val requestBody = "usrname=${encode(email)}&pass=${encode(password)}&APPNAME=CampusNet&PRGNAME=LOGINCHECK&ARGUMENTS=clino%2Cusrname%2Cpass%2Cmenuno%2Cmenu_type%2Cbrowser%2Cplatform&clino=000000000000001&menuno=000324&menu_type=classic&browser=&platform="

        val response = httpClient.post(BASE_URL) {
            setBody(requestBody)
            headers {
                append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
            }
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Dualis Server antwortete mit Status: ${response.status.value}")
        }

        // Get Cookies
        val cookieHeaders = response.headers.getAll(HttpHeaders.SetCookie) ?: emptyList()
        val cookies = cookieHeaders.map { it.substringBefore(";") }
        if (cookies.isEmpty()) {
            throw Exception("Login fehlgeschlagen: Keine Session-Cookies erhalten.")
        }

        // Get REFRESH header
        val refreshHeader = response.headers["Refresh"] 
            ?: response.headers["refresh"]
            ?: throw Exception("Login fehlgeschlagen: Ungültige Antwort (kein Refresh-Header).")

        // Parse refreshHeader for arguments:
        // 0; URL=/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=COURSERESULTS&ARGUMENTS=-N000000000000000,-N000019,-N000000000000000,-N000324,-N000324,-N000324
        val argumentsSegment = refreshHeader.split("&")
            .firstOrNull { it.startsWith("ARGUMENTS=") }
            ?: throw Exception("Login fehlgeschlagen: Keine Argumente im Refresh-Header gefunden.")

        val refactoredArguments = refactorArguments(argumentsSegment)

        return DualisSession(cookies = cookies, arguments = refactoredArguments)
    }

    suspend fun getSemesters(session: DualisSession): List<DualisSemester> {
        val url = "$BASE_URL?APPNAME=CampusNet&PRGNAME=COURSERESULTS&${session.arguments}"
        val html = fetchPage(url, session.cookies)
        return DualisHtmlParser.parseSemesters(html)
    }

    suspend fun getCourses(session: DualisSession, semesterValue: String): List<DualisSemesterCourse> {
        val url = "$BASE_URL?APPNAME=CampusNet&PRGNAME=COURSERESULTS&${session.arguments},-N$semesterValue"
        val html = fetchPage(url, session.cookies)
        return DualisHtmlParser.parseCourses(html)
    }

    suspend fun getExams(session: DualisSession, examLink: String): List<DualisExam> {
        val url = if (examLink.startsWith("http")) examLink else "https://dualis.dhbw.de$examLink"
        val html = fetchPage(url, session.cookies)
        return DualisHtmlParser.parseExams(html)
    }

    suspend fun getOverallData(session: DualisSession): DualisOverallData {
        val url = "$BASE_URL?APPNAME=CampusNet&PRGNAME=STUDENT_RESULT&${session.arguments}"
        val html = fetchPage(url, session.cookies)
        return DualisHtmlParser.parseOverallData(html)
    }

    suspend fun getGPA(session: DualisSession): DualisGPA {
        val url = "$BASE_URL?APPNAME=CampusNet&PRGNAME=STUDENT_RESULT&${session.arguments}"
        val html = fetchPage(url, session.cookies)
        return DualisHtmlParser.parseGPA(html)
    }

    suspend fun getDocuments(session: DualisSession): List<DualisDocument> {
        val url = "$BASE_URL?APPNAME=CampusNet&PRGNAME=CREATEDOCUMENT&${session.arguments}"
        val html = fetchPage(url, session.cookies)
        return DualisHtmlParser.parseDocuments(html)
    }

    private suspend fun fetchPage(url: String, cookies: List<String>): String {
        val response = httpClient.get(url) {
            headers {
                cookies.forEach { cookie ->
                    append(HttpHeaders.Cookie, cookie)
                }
            }
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Fehler beim Laden von Dualis: Status ${response.status.value}")
        }

        // Read raw bytes and decode with ISO-8859-1 (as requested by the guide)
        val bytes = response.readBytes()
        val rawHtml = String(bytes, Charset.forName("ISO-8859-1"))
        
        // As per guide: utf8String = new String(rawResponse.getBytes(ISO_8859_1), UTF_8)
        // If the string was already loaded, converting from ISO-8859-1 bytes back to UTF-8
        // resolves character issues. Let's do that:
        return String(rawHtml.toByteArray(Charset.forName("ISO-8859-1")), Charsets.UTF_8)
    }

    private fun refactorArguments(rawArguments: String): String {
        return rawArguments
            .replace("-N000000000000000", "")
            .replace("-N000019", "-N000307")
    }

    private fun encode(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }
}

data class DualisSession(
    val cookies: List<String>,
    val arguments: String
)
