package com.catchpro.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String,
    showHeader: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeader) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}
