package dev.dominikstahl.dhbwapp.data.remote

import android.util.Xml
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader

data class NextcloudFile(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: String?,
    val contentType: String?
)

class NextcloudClient(private val httpClient: HttpClient) {

    fun normalizeUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean.removeSuffix("/")
    }

    private fun davBaseUrl(serverUrl: String, username: String): String {
        return "${normalizeUrl(serverUrl)}/remote.php/dav/files/$username"
    }

    suspend fun testConnection(serverUrl: String, username: String, password: String): Result<Unit> {
        return try {
            val base = davBaseUrl(serverUrl, username)
            httpClient.request("$base/") {
                method = HttpMethod("PROPFIND")
                header("Depth", "0")
                basicAuth(username, password)
                setBody("""<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>""")
                contentType(ContentType.Text.Xml)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(
        serverUrl: String,
        username: String,
        password: String,
        path: String = "/"
    ): Result<List<NextcloudFile>> {
        return try {
            val base = davBaseUrl(serverUrl, username)
            val davPath = if (path == "/") "/" else "/${path.removePrefix("/")}/"
            val targetUrl = "$base$davPath"

            val response = httpClient.request(targetUrl) {
                method = HttpMethod("PROPFIND")
                header("Depth", "1")
                basicAuth(username, password)
                setBody("""<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:getcontenttype/><d:getcontentlength/><d:getlastmodified/><d:resourcetype/></d:prop></d:propfind>""")
                contentType(ContentType.Text.Xml)
            }

            val xmlText = response.bodyAsText()
            // The href in PROPFIND responses contains URL path (not full URL)
            val urlPath = "/${targetUrl.substringAfter("://").substringAfter("/").trimEnd('/').removeSuffix("/")}/"
            val files = parsePropfindResponse(xmlText, urlPath, path)
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePropfindResponse(xml: String, davPrefixUrl: String, parentPath: String = "/"): List<NextcloudFile> {
        val files = mutableListOf<NextcloudFile>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        parser.nextTag()

        var currentHref: String? = null
        var currentName: String? = null
        var currentContentType: String? = null
        var currentContentLength: Long = 0
        var currentLastModified: String? = null
        var currentIsCollection = false
        var inResponse = false
        var inPropstat = false
        var inProp = false
        var statusOk = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name
                    val namespace = parser.namespace
                    when {
                        !inResponse && tagName == "response" && namespace == "DAV:" -> {
                            inResponse = true
                            currentHref = null
                            currentName = null
                            currentContentType = null
                            currentContentLength = 0
                            currentLastModified = null
                            currentIsCollection = false
                            statusOk = false
                        }
                        inResponse && tagName == "href" && !inPropstat -> {
                            currentHref = parser.nextText().trim()
                        }
                        inResponse && tagName == "propstat" -> inPropstat = true
                        inPropstat && tagName == "prop" -> inProp = true
                        inProp && tagName == "displayname" -> {
                            currentName = parser.nextText().trim()
                        }
                        inProp && tagName == "getcontenttype" -> {
                            currentContentType = parser.nextText().trim().ifEmpty { null }
                        }
                        inProp && tagName == "getcontentlength" -> {
                            currentContentLength = parser.nextText().trim().toLongOrNull() ?: 0
                        }
                        inProp && tagName == "getlastmodified" -> {
                            currentLastModified = parser.nextText().trim()
                        }
                        inProp && tagName == "resourcetype" -> {
                            val depth = parser.depth
                            if (parser.next() == XmlPullParser.START_TAG && parser.name == "collection") {
                                currentIsCollection = true
                            }
                        }
                        inPropstat && tagName == "status" -> {
                            val status = parser.nextText().trim()
                            if (status.contains("200")) {
                                statusOk = true
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tagName = parser.name
                    val namespace = parser.namespace
                    when {
                        inProp && tagName == "prop" -> inProp = false
                        inPropstat && tagName == "propstat" -> inPropstat = false
                        inResponse && tagName == "response" && namespace == "DAV:" -> {
                            inResponse = false
                            if (statusOk && currentHref != null) {
                                val decodedHref = java.net.URLDecoder.decode(currentHref, "UTF-8")
                                val relativePath = decodedHref.removePrefix(davPrefixUrl).trimEnd('/')
                                val name = currentName?.ifEmpty { null }
                                    ?: relativePath.substringAfterLast('/').ifEmpty { relativePath }

                                if (relativePath.isNotEmpty()) {
                                    val basePrefix = if (parentPath == "/") "" else parentPath.removeSuffix("/")
                                    files.add(
                                        NextcloudFile(
                                            path = "$basePrefix/$relativePath",
                                            name = name,
                                            isDirectory = currentIsCollection,
                                            size = currentContentLength,
                                            lastModified = currentLastModified,
                                            contentType = currentContentType
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            parser.next()
        }

        return files
    }

    suspend fun downloadFile(
        serverUrl: String,
        username: String,
        password: String,
        path: String,
        destination: File
    ): Result<File> {
        return try {
            val base = davBaseUrl(serverUrl, username)
            val fileUrl = "$base${path}"

            val response = httpClient.get(fileUrl) {
                basicAuth(username, password)
            }

            destination.parentFile?.mkdirs()
            withContext(Dispatchers.IO) {
                response.bodyAsChannel().toInputStream().use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Result.success(destination)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getFileName(path: String): String {
        return path.substringAfterLast('/').ifEmpty { path }
    }

    fun getParentPath(path: String): String {
        val trimmed = path.removeSuffix("/")
        val parent = trimmed.substringBeforeLast('/')
        return if (parent.isEmpty()) "/" else parent
    }
}
