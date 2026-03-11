package com.reink.ui.readlater

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

@Composable
fun ReadLaterScreen(
    onItemClick: (Long) -> Unit = {},
    viewModel: ReadLaterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.sections.isEmpty()) {
                EmptyState(message = "No saved articles yet.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.sections.forEachIndexed { index, section ->
                        item(key = "header_${index}_${section.dateHeader}") {
                            DateHeader(title = section.dateHeader)
                        }
                        items(
                            items = section.items,
                            key = { it.id },
                        ) { item ->
                            ReadLaterListItem(
                                item = item,
                                onClick = { onItemClick(item.id) },
                                onArchive = { viewModel.archive(item.id) },
                                onRemove = { viewModel.remove(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
