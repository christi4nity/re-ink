package com.reink.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AuthSection(
                    substackSid = state.substackSid,
                    onSidChanged = { viewModel.updateSubstackSid(it) },
                )
            }

            item {
                FeedManagementSection(
                    feeds = state.feeds,
                    showAddDialog = state.showAddFeedDialog,
                    onShowAddDialog = { viewModel.showAddFeedDialog() },
                    onDismissAddDialog = { viewModel.dismissAddFeedDialog() },
                    onAddFeed = { title, url, requiresAuth ->
                        viewModel.addFeed(title, url, requiresAuth)
                    },
                    onDeleteFeed = { viewModel.deleteFeed(it) },
                )
            }

            item {
                ReadingPreferencesSection(
                    preferences = state.readingPreferences,
                    onPreferencesChanged = { viewModel.updateReadingPreferences(it) },
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
