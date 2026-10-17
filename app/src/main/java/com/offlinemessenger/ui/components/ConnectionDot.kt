package com.offlinemessenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.offlinemessenger.ui.theme.Emerald500
import com.offlinemessenger.ui.theme.InkLow

/** Green = live BLE link, grey = not connected. */
@Composable
fun ConnectionDot(connected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (connected) Emerald500 else InkLow),
    )
}
