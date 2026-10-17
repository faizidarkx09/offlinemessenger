package com.offlinemessenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinemessenger.ui.theme.Charcoal700
import kotlin.math.abs

/** Deterministic pastel-ish avatar from a name, no images needed offline. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val color = remember(name) {
        val palette = listOf(
            Color(0xFF2E7D5B), Color(0xFF3A6EA5), Color(0xFF7A5FBF),
            Color(0xFFB25B3E), Color(0xFF4E9B8F), Color(0xFF8F6BB3),
        )
        palette[abs(name.hashCode()) % palette.size]
    }
    val initials = remember(name) {
        name.trim().split("\\s+".toRegex()).take(2)
            .mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
            .ifEmpty { "?" }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}
