package com.offlinemessenger.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Profile avatar: shows the peer's photo when available, otherwise a
 * deterministic colored initial (stable per name — no flicker).
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    photoB64: String = "",
) {
    val bitmap = remember(photoB64) {
        if (photoB64.isBlank()) null
        else runCatching {
            val bytes = Base64.decode(photoB64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
        return
    }
    val color = remember(name) {
        val palette = listOf(
            Color(0xFF0A84FF), Color(0xFF30D158), Color(0xFFBF5AF2),
            Color(0xFFFF9F0A), Color(0xFF64D2FF), Color(0xFFFF6482),
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
            .background(color.copy(alpha = 0.25f)),
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
