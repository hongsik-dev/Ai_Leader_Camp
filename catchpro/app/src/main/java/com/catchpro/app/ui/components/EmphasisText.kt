package com.catchpro.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

data class EmphasisSegment(
    val text: String,
    val highlighted: Boolean = false,
)

@Composable
fun EmphasisText(
    segments: List<EmphasisSegment>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        modifier = modifier,
        text = buildAnnotatedString {
            segments.forEach { segment ->
                if (segment.highlighted) {
                    withStyle(
                        SpanStyle(
                            color = highlightColor,
                            fontWeight = FontWeight.Black,
                        ),
                    ) {
                        append(segment.text)
                    }
                } else {
                    append(segment.text)
                }
            }
        },
        style = style,
        color = color,
    )
}
