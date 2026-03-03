package com.reink.ui.settings

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.reink.data.model.CloudQueueConfig

@Composable
fun CrossDeviceSharingSection(
    config: CloudQueueConfig,
    setupInProgress: Boolean,
    status: String?,
    onSetup: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "CROSS-DEVICE SHARING",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (config.isConfigured) {
                    ConfiguredSharingContent(
                        config = config,
                        status = status,
                        onDisable = onDisable,
                    )
                } else {
                    NotConfiguredSharingContent(
                        setupInProgress = setupInProgress,
                        status = status,
                        onSetup = onSetup,
                    )
                }
            }
        }

        Text(
            text = "Share article URLs from any device to your read-later queue. " +
                "Use the share URL with an iOS Shortcut or any HTTP client.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfiguredSharingContent(
    config: CloudQueueConfig,
    status: String?,
    onDisable: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val shareUrl = config.shareUrl

    Text(
        text = "Share URL",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
    )

    Surface(
        onClick = { clipboardManager.setText(AnnotatedString(shareUrl)) },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                text = shareUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Tap to copy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val setupUrl = config.setupUrl
    val qrBitmap = remember(setupUrl) { generateQrBitmap(setupUrl) }
    if (qrBitmap != null) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = BitmapPainter(qrBitmap.asImageBitmap()),
                contentDescription = "QR code for setup page",
                modifier = Modifier.size(200.dp),
            )
            Text(
                text = "Scan to set up iOS Shortcut",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (status != null) {
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Surface(
        onClick = onDisable,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Disable sharing",
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun NotConfiguredSharingContent(
    setupInProgress: Boolean,
    status: String?,
    onSetup: () -> Unit,
) {
    Surface(
        onClick = onSetup,
        enabled = !setupInProgress,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = if (setupInProgress) "Setting up..." else "Set up cross-device sharing",
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }

    if (status != null) {
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun generateQrBitmap(content: String): Bitmap? {
    return try {
        val size = 512
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}
