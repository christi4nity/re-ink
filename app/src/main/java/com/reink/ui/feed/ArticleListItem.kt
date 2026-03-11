package com.reink.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.reink.data.model.Article

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleListItem(
    article: Article,
    feedTitle: String,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (article.isRead) Modifier.alpha(0.5f) else Modifier),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        if (showActions) showActions = false else onClick()
                    },
                    onLongClick = { showActions = !showActions },
                )
                .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (article.summary.isNotBlank()) {
                    Text(
                        text = article.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (article.isRead) {
                        Text(
                            text = "Read",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "\u00B7",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = feedTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (article.author.isNotBlank() && !article.author.equals(feedTitle, ignoreCase = true)) {
                        Text(
                            text = "\u00B7",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = article.author,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showActions,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row {
                    IconButton(
                        onClick = {
                            showActions = false
                            onArchive()
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_archive),
                            contentDescription = "Archive",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            showActions = false
                            onDelete()
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
}
