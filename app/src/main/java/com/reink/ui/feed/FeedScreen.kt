package com.reink.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reink.ui.components.DateHeader
import com.reink.ui.components.EmptyState
import com.reink.ui.components.ErrorBanner
import com.reink.ui.components.FilterBar
import com.reink.ui.components.LoadingIndicator

@Composable
fun FeedScreen(
    onArticleClick: (Long) -> Unit = {},
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val feedTitles = state.feeds.associate { it.id to it.title }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "re:ink",
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(
                    onClick = { viewModel.sync() },
                    enabled = !state.isSyncing,
                ) {
                    Text(
                        text = if (state.isSyncing) "Syncing\u2026" else "Sync",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
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
                        } else if (state.unreadOnly) {
                            "All caught up."
                        } else {
                            "No articles. Tap Sync to fetch."
                        },
                    )
                }
                else -> {
                    LazyColumn(
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
                }
            }
        }
    }
}
