package com.offlinemessenger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.data.local.entity.MessageStatus
import com.offlinemessenger.ui.formatBubbleTime
import com.offlinemessenger.ui.theme.BubbleIn
import com.offlinemessenger.ui.theme.BubbleOut
import com.offlinemessenger.ui.theme.Emerald400
import com.offlinemessenger.ui.theme.InkHigh
import com.offlinemessenger.ui.theme.InkLow

/**
 * WhatsApp-style bubble. Delivery ticks reflect real ACK state:
 *  ✓ sent to radio · ✓✓ destination acknowledged · ⏳ queued · ✕ failed.
 */
@Composable
fun MessageBubble(message: MessageEntity) {
    val isMine = message.isMine
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isMine) BubbleOut else BubbleIn,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp,
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(text = message.body, color = InkHigh, fontSize = 15.sp)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatBubbleTime(message.timestampMs),
                        color = InkLow,
                        fontSize = 11.sp,
                    )
                    if (isMine) {
                        Text(
                            text = "  " + when (message.status) {
                                MessageStatus.PENDING -> "⏳"
                                MessageStatus.SENT -> "✓"
                                MessageStatus.DELIVERED -> "✓✓"
                                MessageStatus.FAILED -> "✕"
                                MessageStatus.RECEIVED -> ""
                            },
                            color = if (message.status == MessageStatus.DELIVERED) Emerald400 else InkLow,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}
