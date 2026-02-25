package com.reink.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reink.VolumeKey
import com.reink.ui.components.LoadingIndicator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit = {},
    volumeKeyEvents: SharedFlow<VolumeKey> = MutableSharedFlow(),
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var pendingLinkUrl by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // Pagination state
    var currentPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    val isPaginated = state.preferences.paginationMode == "paginated"

    // Overlay visibility — tap content to toggle
    var showOverlay by remember { mutableStateOf(false) }

    // Reset page when content changes
    LaunchedEffect(state.contentHtml) {
        currentPage = 0
    }

    // Volume key navigation (only when paginated)
    LaunchedEffect(isPaginated) {
        if (!isPaginated) return@LaunchedEffect
        volumeKeyEvents.collect { key ->
            when (key) {
                VolumeKey.DOWN -> if (currentPage < totalPages - 1) currentPage++
                VolumeKey.UP -> if (currentPage > 0) currentPage--
            }
        }
    }

    LaunchedEffect(state.savedForLater) {
        if (state.savedForLater) {
            snackbarHostState.showSnackbar("Saved for later")
            viewModel.dismissSavedConfirmation()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isLoading) {
                LoadingIndicator(modifier = Modifier.fillMaxSize())
            } else {
                // Full-size extraction WebView behind the reader —
                // must be full-size so Android doesn't throttle JS execution
                val extractionUrl = state.articleUrl
                if (extractionUrl != null) {
                    SubstackWebView(
                        articleUrl = extractionUrl,
                        sid = state.substackSid,
                        onExtractionResult = { html, success ->
                            viewModel.onContentExtracted(html, success)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Reader WebView on top (covers extraction WebView)
                ArticleWebView(
                    contentHtml = state.contentHtml,
                    preferences = state.preferences,
                    onLinkTapped = { url -> pendingLinkUrl = url },
                    currentPage = currentPage,
                    onPageCountChanged = { totalPages = it },
                    onPageTurn = { delta ->
                        val newPage = (currentPage + delta).coerceIn(0, totalPages - 1)
                        currentPage = newPage
                    },
                    onContentTapped = { showOverlay = !showOverlay },
                    modifier = Modifier.fillMaxSize(),
                )

                // Retry banner for failed extractions
                if (state.extractionFailed) {
                    ExtractionFailedBanner(
                        onRetry = { viewModel.retryExtraction() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            // Overlay — top bar
            if (showOverlay) {
                ReaderOverlayTopBar(
                    title = state.title,
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            // Progress bar — always at bottom when paginated
            if (isPaginated && totalPages > 1) {
                ReadingProgressBar(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (pendingLinkUrl != null) {
        val url = pendingLinkUrl!!
        ModalBottomSheet(
            onDismissRequest = { pendingLinkUrl = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            LinkActionSheet(
                url = url,
                onOpenInBrowser = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    pendingLinkUrl = null
                },
                onSaveForLater = {
                    viewModel.saveForLater(url)
                    pendingLinkUrl = null
                },
                onDismiss = { pendingLinkUrl = null },
            )
        }
    }
}

@Composable
private fun ReaderOverlayTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f,
                )
            }
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "\u2190",
                fontSize = 39.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        // Invisible spacer matching back button width to keep title centered
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun ReadingProgressBar(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
) {
    val progress = if (totalPages > 1) {
        (currentPage + 1).toFloat() / totalPages.toFloat()
    } else {
        0f
    }
    val fillColor = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .drawBehind {
                // Track
                drawRect(color = trackColor)
                // Fill
                drawRect(
                    color = fillColor,
                    size = size.copy(width = size.width * progress),
                )
            },
    )
}

@Composable
private fun ExtractionFailedBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 2f,
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Full article unavailable",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onRetry,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        ) {
            Text("Retry", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LinkActionSheet(
    url: String,
    onOpenInBrowser: () -> Unit,
    onSaveForLater: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = url,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        OutlinedButton(
            onClick = onOpenInBrowser,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text("Open in Browser", style = MaterialTheme.typography.labelLarge)
        }

        OutlinedButton(
            onClick = onSaveForLater,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Save for Later", style = MaterialTheme.typography.labelLarge)
        }

        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(
                "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
