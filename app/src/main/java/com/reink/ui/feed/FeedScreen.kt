package com.reink.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reink.VolumeKey
import com.reink.ui.components.DateHeader
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import com.reink.ui.components.EmptyState
import com.reink.ui.components.ErrorBanner
import com.reink.ui.components.FilterBar
import com.reink.ui.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onArticleClick: (Long) -> Unit = {},
    volumeKeyEvents: SharedFlow<VolumeKey> = MutableSharedFlow(),
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.currentPage, state.unreadOnly) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(Unit) {
        volumeKeyEvents.collect { key ->
            when (key) {
                VolumeKey.DOWN -> viewModel.nextPage()
                VolumeKey.UP -> viewModel.previousPage()
            }
        }
    }

    // Build a lookup map for feed titles
    val feedTitles = state.feeds.associate { it.id to it.title }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "re:ink",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.sync() },
                        enabled = !state.isSyncing,
                    ) {
                        Text(
                            text = if (state.isSyncing) "Syncing\u2026" else "Sync",
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            state.error?.let { errorMessage ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ErrorBanner(
                        message = errorMessage,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("Dismiss")
                    }
                }
            }

            if (state.feeds.isNotEmpty()) {
                FilterBar(
                    feeds = state.feeds,
                    selectedFeedId = state.selectedFeedId,
                    onFeedSelected = { viewModel.selectFeed(it) },
                    unreadOnly = state.unreadOnly,
                    onToggleUnread = { viewModel.toggleUnreadOnly() },
                )
            }

            when {
                state.isSyncing && state.sections.isEmpty() -> {
                    LoadingIndicator()
                }
                state.sections.isEmpty() -> {
                    EmptyState(
                        message = if (state.feeds.isEmpty()) {
                            "No feeds yet. Add one in Settings."
                        } else {
                            "No articles. Tap Sync to fetch."
                        },
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        state.sections.forEachIndexed { index, section ->
                            item(key = "header_${index}_${section.dateHeader}") {
                                DateHeader(title = section.dateHeader)
                            }
                            items(
                                items = section.articles,
                                key = { it.id },
                            ) { article ->
                                ArticleListItem(
                                    article = article,
                                    feedTitle = feedTitles[article.feedId] ?: "",
                                    onClick = { onArticleClick(article.id) },
                                )
                            }
                        }
                    }

                    if (state.hasPrevious || state.hasNext) {
                        PaginationBar(
                            currentPage = state.currentPage,
                            hasPrevious = state.hasPrevious,
                            hasNext = state.hasNext,
                            onPrevious = { viewModel.previousPage() },
                            onNext = { viewModel.nextPage() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaginationBar(
    currentPage: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            border = BorderStroke(
                width = if (hasPrevious) 2.dp else 1.dp,
                color = if (hasPrevious) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("Previous", style = MaterialTheme.typography.labelLarge)
        }

        Text(
            text = "Page ${currentPage + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onNext,
            enabled = hasNext,
            border = BorderStroke(
                width = if (hasNext) 2.dp else 1.dp,
                color = if (hasNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("Next", style = MaterialTheme.typography.labelLarge)
        }
    }
}
