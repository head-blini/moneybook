package com.moneybook.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    tag: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().testTag(tag).verticalScroll(rememberScrollState())
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(description, style = MaterialTheme.typography.bodyLarge)
        if (actionLabel != null) {
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(actionLabel)
            }
        }
    }
}
