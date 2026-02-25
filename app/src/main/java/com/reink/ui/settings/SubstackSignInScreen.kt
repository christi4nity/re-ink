package com.reink.ui.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val SID_REGEX = Regex("""substack\.sid=([^;]+)""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstackSignInScreen(
    onBack: () -> Unit,
    onSignInComplete: () -> Unit,
    viewModel: SubstackSignInViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var urlBarText by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Substack Sign In") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // URL bar for pasting magic links
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = urlBarText,
                    onValueChange = { urlBarText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Paste magic link URL here") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                Surface(
                    onClick = {
                        val url = urlBarText.trim()
                        if (url.isNotBlank()) {
                            webView?.loadUrl(url)
                        }
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = "Go",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Status + Done row
            if (status == SignInStatus.Complete) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Signed in",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Surface(
                        onClick = onSignInComplete,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = "Done",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Full-size WebView
            SignInWebView(
                onUrlChanged = { urlBarText = it },
                onSidDetected = { sid -> viewModel.onSidDetected(sid) },
                onWebViewCreated = { webView = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SignInWebView(
    onUrlChanged: (String) -> Unit,
    onSidDetected: (String) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            @SuppressLint("SetJavaScriptEnabled")
            fun createWebView(): WebView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        val host = request.url?.host?.lowercase() ?: return false
                        // Allow all Substack navigation
                        if (host.endsWith("substack.com") || host == "substack.com") return false
                        // Allow email provider redirects (magic link flow)
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        url ?: return
                        onUrlChanged(url)
                        checkForSid(onSidDetected)
                    }
                }

                loadUrl("https://substack.com/sign-in")
                onWebViewCreated(this)
            }
            createWebView()
        },
        modifier = modifier,
    )
}

private fun checkForSid(onSidDetected: (String) -> Unit) {
    val cookies = CookieManager.getInstance().getCookie("https://substack.com") ?: return
    val match = SID_REGEX.find(cookies) ?: return
    val sid = match.groupValues[1]
    if (sid.isNotBlank()) {
        onSidDetected(sid)
    }
}
