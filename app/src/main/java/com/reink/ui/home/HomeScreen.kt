package com.reink.ui.home

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
import com.reink.ui.components.LoadingIndicator
import com.reink.ui.components.UpdateBanner
import com.reink.ui.feed.ArticleListItem
import com.reink.ui.readlater.ReadLaterListItem

@Composable
fun HomeScreen(
    onArticleClick: (Long) -> Unit = {},
    onReadLaterClick: (Long) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "re:ink",
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { viewModel.sync() },
                        enabled = !state.isSyncing,
                    ) {
                        Text(
                            text = if (state.isSyncing) "Syncing\u2026" else "\u21BB",
                            style = if (state.isSyncing)
                                MaterialTheme.typography.labelLarge
                            else
                                MaterialTheme.typography.titleLarge,
                            color = if (state.isSyncing)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(
                        onClick = onNavigateToSettings,
                    ) {
                        Text(
                            text = "\u2261",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            val pendingUpdate = state.availableUpdate
            if (state.updateReady && pendingUpdate != null) {
                UpdateBanner(
                    versionName = pendingUpdate.versionName,
                    onInstall = { viewModel.installUpdate() },
                )
            }

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

            when {
                state.isSyncing && state.sections.isEmpty() -> {
                    LoadingIndicator()
                }
                state.sections.isEmpty() -> {
                    EmptyState(message = "All caught up.")
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        state.sections.forEachIndexed { index, section ->
                            item(key = "home_header_${index}_${section.dateHeader}") {
                                DateHeader(title = section.dateHeader)
                            }
                            items(
                                items = section.items,
                                key = { homeItem ->
                                    when (homeItem) {
                                        is HomeItem.ArticleItem -> "article_${homeItem.id}"
                                        is HomeItem.ReadLaterHomeItem -> "readlater_${homeItem.id}"
                                    }
                                },
                            ) { homeItem ->
                                when (homeItem) {
                                    is HomeItem.ArticleItem -> ArticleListItem(
                                        article = homeItem.article,
                                        feedTitle = homeItem.feedTitle,
                                        onClick = { onArticleClick(homeItem.id) },
                                        onArchive = { viewModel.archiveArticle(homeItem.id) },
                                        onDelete = { viewModel.deleteArticle(homeItem.id) },
                                    )
                                    is HomeItem.ReadLaterHomeItem -> ReadLaterListItem(
                                        item = homeItem.item,
                                        onClick = { onReadLaterClick(homeItem.id) },
                                        onArchive = { viewModel.archiveReadLater(homeItem.id) },
                                        onRemove = { viewModel.removeReadLater(homeItem.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
