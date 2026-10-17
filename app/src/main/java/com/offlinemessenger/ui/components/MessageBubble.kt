package com.offlinemessenger.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.data.local.entity.MessageStatus
import com.offlinemessenger.data.local.entity.reactions
import com.offlinemessenger.ui.formatBubbleTime
import com.offlinemessenger.ui.theme.BubbleIn
import com.offlinemessenger.ui.theme.BubbleOut
import com.offlinemessenger.ui.theme.Charcoal700
import com.offlinemessenger.ui.theme.Emerald400
import com.offlinemessenger.ui.theme.InkHigh
import com.offlinemessenger.ui.theme.InkLow
import com.offlinemessenger.ui.theme.InkMid
import com.offlinemessenger.ui.theme.ReadBlue

/**
 * Premium WhatsApp-style bubble: reply quotes, forwarded label, reactions,
 * honest delivery ticks (✓ sent · ✓✓ delivered · blue ✓✓ read · ⏳ · ✕).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    myId: String,
    senderName: String? = null,
    animateIn: Boolean = true,
    onLongPress: (MessageEntity) -> Unit = {},
    onReactionTap: (MessageEntity, String) -> Unit = { _, _ -> },
    onReplyTap: (String) -> Unit = {},
) {
    val isMine = message.isMine
    val reactions = remember(message.reactionsJson) { message.reactions() }

    AnimatedVisibility(
        visible = true,
        enter = (if (animateIn) slideInVertically { it / 3 } else slideInVertically { 0 }) + fadeIn(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            ) {
                Surface(
                    color = if (isMine) BubbleOut else BubbleIn,
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isMine) 16.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 16.dp,
                    ),
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { onLongPress(message) },
                    ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (message.forwarded) {
                            Text(
                                "Forwarded",
                                color = InkLow,
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        if (!isMine && senderName != null) {
                            Text(
                                senderName,
                                color = Emerald400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        if (message.replyToId != null) {
                            ReplyQuote(
                                sender = message.replyToSender ?: "?",
                                body = message.replyToBody ?: "",
                                onClick = { onReplyTap(message.replyToId) },
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(text = message.body, color = InkHigh, fontSize = 15.sp)
                        Spacer(Modifier.height(2.dp))
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
                                    text = "  " + tickFor(message.status),
                                    color = tickColor(message.status),
                                    fontSize = 12.sp,
                                    fontWeight = if (message.status == MessageStatus.READ) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
                if (reactions.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        reactions.forEach { (emoji, ids) ->
                            val mine = myId in ids
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (mine) Emerald400.copy(alpha = 0.25f) else Charcoal700)
                                    .clickable { onReactionTap(message, emoji) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    "$emoji ${ids.size}",
                                    fontSize = 12.sp,
                                    color = InkHigh,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyQuote(sender: String, body: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Charcoal700.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Emerald400),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(sender, color = Emerald400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                body.take(80),
                color = InkMid,
                fontSize = 12.sp,
                maxLines = 2,
            )
        }
    }
}

private fun tickFor(status: MessageStatus): String = when (status) {
    MessageStatus.PENDING -> "⏳"
    MessageStatus.SENT -> "✓"
    MessageStatus.DELIVERED -> "✓✓"
    MessageStatus.READ -> "✓✓"
    MessageStatus.FAILED -> "✕"
    MessageStatus.RECEIVED -> ""
}

@Composable
private fun tickColor(status: MessageStatus) = when (status) {
    MessageStatus.READ -> ReadBlue
    else -> InkLow
}
