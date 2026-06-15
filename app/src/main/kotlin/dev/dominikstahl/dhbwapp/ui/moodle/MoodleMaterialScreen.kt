package dev.dominikstahl.dhbwapp.ui.moodle

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodleMaterialScreen(
    contentId: Int,
    url: String,
    title: String,
    type: String,
    viewModel: MoodleViewModel,
    onBackClick: () -> Unit
) {
    val contentItem by viewModel.getContentFlow(contentId).collectAsState(initial = null)

    val displayType = contentItem?.type ?: type
    val displayTitle = contentItem?.name ?: title

    if (displayType in listOf("forum", "quiz", "choice", "feedback", "bigbluebuttonbn", "page")) {
        MoodleWebViewContent(
            url = url,
            title = displayTitle,
            onBackClick = onBackClick
        )
    } else {
        MoodleNativeMaterialContent(
            content = contentItem,
            fallbackTitle = displayTitle,
            viewModel = viewModel,
            onBackClick = onBackClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MoodleWebViewContent(
    url: String,
    title: String,
    onBackClick: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            
                            val defaultUserAgent = settings.userAgentString
                            settings.userAgentString = "$defaultUserAgent MoodleMobile"

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    progress = newProgress / 100f
                                    if (newProgress >= 100) {
                                        isLoading = false
                                    }
                                }
                            }

                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodleNativeMaterialContent(
    content: CachedMoodleContent?,
    fallbackTitle: String,
    viewModel: MoodleViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var refreshCounter by remember { mutableIntStateOf(0) }
    
    val isPdf = remember(content) {
        val url = content?.url ?: ""
        url.substringAfterLast('.', "").substringBefore('?').lowercase() == "pdf"
    }

    val downloaded = remember(content?.url, refreshCounter) {
        content?.url?.let { url -> viewModel.isFileDownloaded(context, url, content.name) } ?: false
    }

    val localFile = remember(content?.url, refreshCounter) {
        content?.url?.let { url -> viewModel.getDownloadedFile(context, url, content.name) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(content?.name ?: fallbackTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                windowInsets = WindowInsets(0.dp),
                actions = {
                    if (isPdf && downloaded && localFile != null) {
                        IconButton(onClick = {
                            viewModel.openFileExternally(context, localFile)
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Extern öffnen")
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
            if (content == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (isPdf) {
                var downloadProgress by remember { mutableStateOf(false) }

                LaunchedEffect(content.url, downloaded) {
                    if (!downloaded) {
                        content.url?.let { url ->
                            downloadProgress = true
                            viewModel.downloadFile(context, url, content.name) { res ->
                                downloadProgress = false
                                if (res.isSuccess) {
                                    refreshCounter++
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Fehler beim Laden: ${res.exceptionOrNull()?.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                }

                if (downloaded && localFile != null) {
                    PdfViewer(file = localFile, modifier = Modifier.fillMaxSize())
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = content.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (downloadProgress) {
                            CircularProgressIndicator()
                        } else {
                            Button(
                                onClick = {
                                    content.url?.let { url ->
                                        downloadProgress = true
                                        viewModel.downloadFile(context, url, content.name) { res ->
                                            downloadProgress = false
                                            if (res.isSuccess) {
                                                refreshCounter++
                                            } else {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Fehler beim Laden: ${res.exceptionOrNull()?.message}",
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PDF herunterladen und anzeigen")
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = content.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    when (content.type) {
                        "page" -> {
                            val pageHtml = content.description ?: "Kein Inhalt vorhanden."
                            AndroidView(
                                factory = { context ->
                                    TextView(context).apply {
                                        setTextColor(context.getColor(android.R.color.black))
                                        textSize = 16f
                                    }
                                },
                                update = { textView ->
                                    textView.text = HtmlCompat.fromHtml(pageHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {
                            Text(
                                text = "Dieses Material (${content.type}) kann direkt auf deinem Gerät geöffnet werden.",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Button(
                                onClick = {
                                    content.url?.let { url ->
                                        if (downloaded) {
                                            viewModel.downloadAndOpenFile(context, url, content.name) {}
                                        } else {
                                            viewModel.downloadAndOpenFile(context, url, content.name) { res ->
                                                if (res.isSuccess) {
                                                    refreshCounter++
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (downloaded) Icons.Default.Check else Icons.Default.Download,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (downloaded) "Datei öffnen" else "Datei herunterladen")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfViewer(
    file: java.io.File,
    modifier: Modifier = Modifier
) {
    var pageCount by remember(file) { mutableIntStateOf(0) }
    var pdfRenderer by remember(file) { mutableStateOf<PdfRenderer?>(null) }
    
    DisposableEffect(file) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            pdfRenderer = renderer
            pageCount = renderer.pageCount
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        onDispose {
            try {
                renderer?.close()
                pfd?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (pageCount > 0 && pdfRenderer != null) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Gray.copy(alpha = 0.1f)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(pageCount) { pageIndex ->
                PdfPageItem(pdfRenderer = pdfRenderer!!, pageIndex = pageIndex)
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun PdfPageItem(pdfRenderer: PdfRenderer, pageIndex: Int) {
    var bitmap by remember(pdfRenderer, pageIndex) { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(pdfRenderer, pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                val page = pdfRenderer.openPage(pageIndex)
                val width = page.width * 2
                val height = page.height * 2
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "PDF Seite ${pageIndex + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap!!.width.toFloat() / bitmap!!.height.toFloat()),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
