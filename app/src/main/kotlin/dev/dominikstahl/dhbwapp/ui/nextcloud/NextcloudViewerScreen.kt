package dev.dominikstahl.dhbwapp.ui.nextcloud

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.io.File
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.jeziellago.compose.markdowntext.MarkdownText
import dev.dominikstahl.dhbwapp.data.remote.NextcloudFile
import dev.dominikstahl.dhbwapp.ui.moodle.PdfViewer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextcloudViewerScreen(
    file: NextcloudFile,
    viewModel: NextcloudViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val ext = file.name.substringAfterLast('.').lowercase()
    val isPdf = ext == "pdf"
    val isMarkdown = ext in listOf("md", "markdown")
    val isImage = ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    val isText = ext == "txt"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name, maxLines = 1) },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (downloadedFile != null) {
                        IconButton(onClick = {
                            openFileExternally(context, downloadedFile!!)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Extern öffnen")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isPdf && downloadedFile != null -> {
                    PdfViewer(
                        file = downloadedFile!!,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                isMarkdown && downloadedFile != null -> {
                    MarkdownViewer(file = downloadedFile!!)
                }
                isImage && downloadedFile != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(downloadedFile!!)
                            .crossfade(true)
                            .build(),
                        contentDescription = file.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                isText && downloadedFile != null -> {
                    TextFileViewer(file = downloadedFile!!)
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        downloadError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (isDownloading) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Lade Datei herunter...")
                        } else if (downloadedFile == null) {
                            Button(
                                onClick = {
                                    isDownloading = true
                                    downloadError = null
                                    viewModel.downloadAndViewFile(
                                        ncFile = file,
                                        onSuccess = { f ->
                                            downloadedFile = f
                                            isDownloading = false
                                        },
                                        onError = { err ->
                                            downloadError = err
                                            isDownloading = false
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Datei herunterladen")
                            }
                        } else {
                            Button(
                                onClick = { openFileExternally(context, downloadedFile!!) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Datei öffnen")
                            }
                        }
                    }
                }
            }

        }
    }

    // Auto-download for PDF, markdown, images, and text
    if ((isPdf || isMarkdown || isImage || isText) && downloadedFile == null && !isDownloading && downloadError == null) {
        LaunchedEffect(file.path) {
            isDownloading = true
            viewModel.downloadAndViewFile(
                ncFile = file,
                onSuccess = { f ->
                    downloadedFile = f
                    isDownloading = false
                },
                onError = { err ->
                    downloadError = err
                    isDownloading = false
                }
            )
        }
    }
}

@Composable
private fun TextFileViewer(file: File) {
    val text by produceState<String?>(initialValue = null, key1 = file) {
        value = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    if (text == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Fehler beim Lesen der Datei", color = MaterialTheme.colorScheme.error)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = text!!,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun MarkdownViewer(file: File) {
    val markdownText by produceState<String?>(initialValue = null, key1 = file) {
        value = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    if (markdownText == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Fehler beim Lesen der Datei", color = MaterialTheme.colorScheme.error)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            MarkdownText(
                markdown = markdownText!!,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun openFileExternally(context: android.content.Context, file: File) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val fileUri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            val ext = file.name.substringAfterLast('.').lowercase()
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            setDataAndType(fileUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler beim Öffnen: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
