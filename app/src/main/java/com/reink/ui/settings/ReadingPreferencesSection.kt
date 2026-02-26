package com.reink.ui.settings

import android.content.res.AssetManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reink.data.model.ReadingPreferences

internal val readingModeOptions = listOf(
    "scroll" to "Scroll",
    "paginated" to "Paginated",
)
internal val fontOptions = listOf("Literata", "Source Serif 4", "Atkinson Hyperlegible")
internal val alignOptions = listOf(
    "left" to "Left",
    "center" to "Center",
    "right" to "Right",
    "justify" to "Justify",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingPreferencesSection(
    preferences: ReadingPreferences,
    onPreferencesChanged: (ReadingPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "READING PREFERENCES",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Reading mode
                ReadingModePicker(
                    selected = preferences.paginationMode,
                    onSelected = {
                        onPreferencesChanged(preferences.copy(paginationMode = it))
                    },
                )

                // Font family dropdown
                FontDropdown(
                    selected = preferences.fontFamily,
                    onSelected = {
                        onPreferencesChanged(preferences.copy(fontFamily = it))
                    },
                )

                // Font size
                LabeledSlider(
                    label = "Size",
                    value = preferences.fontSize.toFloat(),
                    valueRange = 14f..36f,
                    displayValue = "${preferences.fontSize}px",
                    onValueChange = {
                        onPreferencesChanged(preferences.copy(fontSize = it.toInt()))
                    },
                )

                // Line height
                LabeledSlider(
                    label = "Line height",
                    value = preferences.lineHeight,
                    valueRange = 1.2f..2.2f,
                    displayValue = "%.1f".format(preferences.lineHeight),
                    onValueChange = {
                        val rounded = (it * 10).toInt() / 10f
                        onPreferencesChanged(preferences.copy(lineHeight = rounded))
                    },
                )

                // Margins
                LabeledSlider(
                    label = "Margins",
                    value = preferences.marginHorizontal.toFloat(),
                    valueRange = 8f..192f,
                    displayValue = "${preferences.marginHorizontal}dp",
                    onValueChange = {
                        onPreferencesChanged(preferences.copy(marginHorizontal = it.toInt()))
                    },
                )

                // Text alignment
                AlignmentPicker(
                    selected = preferences.textAlign,
                    onSelected = {
                        onPreferencesChanged(preferences.copy(textAlign = it))
                    },
                )

                // Preview
                val assetManager = LocalContext.current.assets
                val previewFontFamily = remember(preferences.fontFamily) {
                    fontFamilyFromAssets(preferences.fontFamily, assetManager)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "The quick brown fox jumps over the lazy dog. " +
                            "Pack my box with five dozen liquor jugs. " +
                            "How vexingly quick daft zebras jump.\n\n" +
                            "A second paragraph to show spacing between blocks of text. " +
                            "The margins, line height, and font size all affect " +
                            "how comfortable long-form reading feels on an e-ink display.",
                        modifier = Modifier.padding(
                            horizontal = preferences.marginHorizontal.dp,
                            vertical = 12.dp,
                        ),
                        style = TextStyle(
                            fontFamily = previewFontFamily,
                            fontSize = preferences.fontSize.sp,
                            lineHeight = (preferences.fontSize * preferences.lineHeight).sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = when (preferences.textAlign) {
                                "center" -> TextAlign.Center
                                "right" -> TextAlign.End
                                "justify" -> TextAlign.Justify
                                else -> TextAlign.Start
                            },
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FontDropdown(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "Font",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                fontOptions.forEach { font ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = font,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (font == selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onSelected(font)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReadingModePicker(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Reading mode",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            readingModeOptions.forEach { (value, label) ->
                Surface(
                    onClick = { onSelected(value) },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(
                        width = if (selected == value) 2.dp else 1.dp,
                        color = if (selected == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
                    color = if (selected == value) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Text(
                        text = label,
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected == value) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AlignmentPicker(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Alignment",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            alignOptions.forEach { (value, label) ->
                Surface(
                    onClick = { onSelected(value) },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(
                        width = if (selected == value) 2.dp else 1.dp,
                        color = if (selected == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
                    color = if (selected == value) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Text(
                        text = label,
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected == value) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

internal val fontAssetPaths = mapOf(
    "Literata" to ("fonts/Literata-Regular.ttf" to "fonts/Literata-Bold.ttf"),
    "Source Serif 4" to ("fonts/SourceSerif4-Regular.ttf" to "fonts/SourceSerif4-Bold.ttf"),
    "Atkinson Hyperlegible" to ("fonts/AtkinsonHyperlegible-Regular.ttf" to "fonts/AtkinsonHyperlegible-Bold.ttf"),
)

internal fun fontFamilyFromAssets(fontName: String, assetManager: AssetManager): FontFamily {
    val paths = fontAssetPaths[fontName] ?: return FontFamily.Serif
    return FontFamily(
        Font(paths.first, assetManager, weight = FontWeight.Normal),
        Font(paths.second, assetManager, weight = FontWeight.Bold),
    )
}
