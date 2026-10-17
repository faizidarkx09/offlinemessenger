package com.offlinemessenger.ui

import com.offlinemessenger.data.local.entity.MessageEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Rows in the chat list: messages (newest first) with day separators. */
sealed interface ChatItem {
    data class Msg(val m: MessageEntity) : ChatItem
    data class DateHeader(val label: String) : ChatItem
}

/**
 * Builds display rows from ASC messages: reverses to newest-first,
 * deduplicates defensively, and inserts a date header where the day changes.
 */
fun buildChatItems(messages: List<MessageEntity>): List<ChatItem> {
    val ordered = messages.asReversed().distinctBy { it.msgId }
    val out = ArrayList<ChatItem>(ordered.size + 2)
    var lastDay = -1L
    for (m in ordered) {
        val day = startOfDay(m.timestampMs)
        if (day != lastDay) {
            out.add(ChatItem.DateHeader(dayLabel(day)))
            lastDay = day
        }
        out.add(ChatItem.Msg(m))
    }
    return out
}

private fun startOfDay(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun dayLabel(dayStartMs: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    val yesterday = today - 24 * 60 * 60 * 1000L
    return when (dayStartMs) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(dayStartMs))
    }
}
