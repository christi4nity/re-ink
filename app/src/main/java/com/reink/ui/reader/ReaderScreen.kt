package com.reink.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reink.VolumeKey
import com.reink.ui.components.LoadingIndicator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.abs

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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = "Back",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(innerPadding))
        } else if (isPaginated) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                ArticleWebView(
                    contentHtml = state.contentHtml,
                    preferences = state.preferences,
                    onLinkTapped = { url -> pendingLinkUrl = url },
                    currentPage = currentPage,
                    onPageCountChanged = { totalPages = it },
                    modifier = Modifier.fillMaxSize(),
                )
                // Gesture overlay — intercepts drags for page turns, lets taps through
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var totalDragX = 0f
                                var totalDragY = 0f
                                var dragged = false

                                var continueLoop = true
                                while (continueLoop) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull()
                                    if (change == null) {
                                        continueLoop = false
                                    } else {
                                        val delta = change.positionChange()
                                        totalDragX += delta.x
                                        totalDragY += delta.y

                                        if (abs(totalDragX) > 30f || abs(totalDragY) > 30f) {
                                            dragged = true
                                            change.consume()
                                        }

                                        if (!change.pressed) {
                                            continueLoop = false
                                        }
                                    }
                                }

                                if (dragged) {
                                    if (abs(totalDragX) > abs(totalDragY)) {
                                        // Horizontal: swipe left = next, swipe right = prev
                                        if (totalDragX < -80f && currentPage < totalPages - 1) {
                                            currentPage++
                                        } else if (totalDragX > 80f && currentPage > 0) {
                                            currentPage--
                                        }
                                    } else {
                                        // Vertical: swipe up = next, swipe down = prev
                                        if (totalDragY < -80f && currentPage < totalPages - 1) {
                                            currentPage++
                                        } else if (totalDragY > 80f && currentPage > 0) {
                                            currentPage--
                                        }
                                    }
                                }
                            }
                        },
                )
            }
        } else {
            ArticleWebView(
                contentHtml = state.contentHtml,
                preferences = state.preferences,
                onLinkTapped = { url -> pendingLinkUrl = url },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
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
