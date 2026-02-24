package com.reink.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reink.data.model.ReadingPreferences

private val fontOptions = listOf("Literata", "Source Serif 4", "Atkinson Hyperlegible")
private val alignOptions = listOf("left" to "Left", "justify" to "Justify")

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
                // Font family
                Text(
                    text = "Font",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                fontOptions.forEach { font ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = preferences.fontFamily == font,
                            onClick = {
                                onPreferencesChanged(preferences.copy(fontFamily = font))
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.onSurface,
                                unselectedColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                        Text(
                            text = font,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                // Font size
                LabeledSlider(
                    label = "Size",
                    value = preferences.fontSize.toFloat(),
                    valueRange = 14f..28f,
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
                    valueRange = 8f..48f,
                    displayValue = "${preferences.marginHorizontal}dp",
                    onValueChange = {
                        onPreferencesChanged(preferences.copy(marginHorizontal = it.toInt()))
                    },
                )

                // Text alignment
                Text(
                    text = "Alignment",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    alignOptions.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RadioButton(
                                selected = preferences.textAlign == value,
                                onClick = {
                                    onPreferencesChanged(preferences.copy(textAlign = value))
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedColor = MaterialTheme.colorScheme.outline,
                                ),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                // Preview
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "The quick brown fox jumps over the lazy dog. " +
                            "This is a preview of your reading preferences.",
                        modifier = Modifier.padding(
                            horizontal = preferences.marginHorizontal.dp,
                            vertical = 12.dp,
                        ),
                        style = TextStyle(
                            fontSize = preferences.fontSize.sp,
                            lineHeight = (preferences.fontSize * preferences.lineHeight).sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = if (preferences.textAlign == "justify") {
                                TextAlign.Justify
                            } else {
                                TextAlign.Start
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
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
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
