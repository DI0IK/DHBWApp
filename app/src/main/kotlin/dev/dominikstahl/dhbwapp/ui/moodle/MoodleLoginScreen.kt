// Portions of this file are derived from dawdle (https://codeberg.org/fynngodau/dawdle)
// Copyright (c) 2020-2024 Fynn Godau
// Licensed under the GPLv3

package dev.dominikstahl.dhbwapp.ui.moodle

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.dominikstahl.dhbwapp.data.remote.MoodleClient
import kotlinx.coroutines.launch
import android.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MoodleLoginScreen(
    moodleClient: MoodleClient,
    viewModel: MoodleViewModel,
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var siteUrlInput by remember { mutableStateOf("https://moodle.dhbw.de") }
    var activeUrl by remember { mutableStateOf<String?>(null) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var isVerifyingToken by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val popularSites = listOf(
        "moodle.dhbw.de" to "DHBW Moodle"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moodle Login") },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeUrl != null) {
                            activeUrl = null
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
            if (activeUrl == null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Mit Moodle verbinden",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bitte wähle deine DHBW-Moodle-Instanz oder gib die URL manuell ein.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = siteUrlInput,
                            onValueChange = { siteUrlInput = it },
                            label = { Text("Moodle Server URL") },
                            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    item {
                        Button(
                            onClick = {
                                var url = siteUrlInput.trim()
                                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                    url = "https://$url"
                                }
                                activeUrl = url
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Verbinden")
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "DHBW Standorte",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    items(popularSites) { (url, label) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    siteUrlInput = "https://$url"
                                    activeUrl = "https://$url"
                                },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = label, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val passport = remember { (100000..999999).random().toString() }
                val launchUrl = remember(activeUrl) {
                    val cleanUrl = activeUrl!!.removeSuffix("/")
                    "$cleanUrl/admin/tool/mobile/launch.php?service=moodle_mobile_app&passport=$passport&urlscheme=moodledirect"
                }

                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoadingPage = true
                                    super.onPageStarted(view, url, favicon)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoadingPage = false
                                    super.onPageFinished(view, url)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    if (handleTokenRedirection(url)) {
                                        return true
                                    }
                                    return super.shouldOverrideUrlLoading(view, request)
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (url != null && handleTokenRedirection(url)) {
                                        return true
                                    }
                                    return super.shouldOverrideUrlLoading(view, url)
                                }

                                private fun handleTokenRedirection(url: String): Boolean {
                                    if (url.startsWith("moodledirect://") || url.startsWith("moodlemobile://")) {
                                        val tokenSegment = url.substringAfter("token=")
                                        try {
                                            val decodedBytes = Base64.decode(tokenSegment, Base64.DEFAULT)
                                            val decoded = String(decodedBytes, Charsets.UTF_8)
                                            val parts = decoded.split(":::")
                                            if (parts.size >= 2) {
                                                val token = parts[1]
                                                verifyAndCompleteLogin(token)
                                                return true
                                            }
                                        } catch (e: Exception) {
                                            verificationError = "Token-Dekodierung fehlgeschlagen: ${e.localizedMessage}"
                                        }
                                    }
                                    return false
                                }

                                private fun verifyAndCompleteLogin(token: String) {
                                    isVerifyingToken = true
                                    coroutineScope.launch {
                                        try {
                                            val siteUrl = activeUrl!!
                                            val siteInfo = moodleClient.getSiteInfo(siteUrl, token)
                                            viewModel.loginWithToken(token, siteInfo.userid, siteUrl)
                                            onLoginSuccess()
                                        } catch (e: Exception) {
                                            verificationError = "Verifizierung fehlgeschlagen: ${e.localizedMessage}"
                                            isVerifyingToken = false
                                        }
                                    }
                                }
                            }
                            loadUrl(launchUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoadingPage) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (isVerifyingToken) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.8f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Verifiziere Verbindung...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            verificationError?.let { errorMsg ->
                AlertDialog(
                    onDismissRequest = { verificationError = null },
                    title = { Text("Fehler") },
                    text = { Text(errorMsg) },
                    confirmButton = {
                        TextButton(onClick = { verificationError = null }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}
