package com.reink.ui.readlater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reink.R
import com.reink.data.model.FetchStatus
import com.reink.data.model.ReadLaterItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReadLaterListItem(
    item: ReadLaterItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDelete by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (item.isRead) Modifier.alpha(0.5f) else Modifier),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .combinedClickable(
                    enabled = item.fetchStatus == FetchStatus.FETCHED,
                    onClick = {
                        if (showDelete) showDelete = false else onClick()
                    },
                    onLongClick = { showDelete = !showDelete },
                )
                .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.title.ifBlank { item.url },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.isRead) {
                        Text(
                            text = "Read",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!item.sourceDomain.isNullOrBlank()) {
                            Text(
                                text = "\u00B7",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    when (item.fetchStatus) {
                        FetchStatus.PENDING, FetchStatus.FETCHING -> {
                            Text(
                                text = if (item.fetchStatus == FetchStatus.FETCHING) "Fetching\u2026" else "Pending",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!item.sourceDomain.isNullOrBlank()) {
                                Text(
                                    text = "\u00B7",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        FetchStatus.FAILED -> {
                            Text(
                                text = "Failed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!item.sourceDomain.isNullOrBlank()) {
                                Text(
                                    text = "\u00B7",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        else -> {}
                    }
                    if (!item.sourceDomain.isNullOrBlank()) {
                        Text(
                            text = item.sourceDomain,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showDelete,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                IconButton(
                    onClick = {
                        showDelete = false
                        onRemove()
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
