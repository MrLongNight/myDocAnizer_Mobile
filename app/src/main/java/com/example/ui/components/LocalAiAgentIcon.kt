package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Renders the custom local AI agent button icon from 'res/drawable/ic_local_ai_agent.xml'
 * converted directly from 'Local-AI-Agent_button-icon.svg'.
 * Uses native hardware-accelerated VectorDrawable rendering with dynamic theme tinting.
 */
@Composable
fun LocalAiAgentIcon(
    modifier: Modifier = Modifier.size(44.dp),
    tint: Color = MaterialTheme.colorScheme.onSecondary
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_local_ai_agent),
        contentDescription = "Lokaler KI Agent",
        modifier = modifier,
        tint = tint
    )
}
