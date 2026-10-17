package com.offlinemessenger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.offlinemessenger.OfflineMessengerApp
import com.offlinemessenger.di.ServiceLocator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun rememberLocator(): ServiceLocator {
    val app = LocalContext.current.applicationContext as OfflineMessengerApp
    return remember { app.locator }
}

@Suppress("UNCHECKED_CAST")
class VmFactory(private val create: () -> ViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}

fun formatTime(ms: Long): String {
    val now = System.currentTimeMillis()
    val day = 24 * 60 * 60 * 1000L
    return when {
        now - ms < day && isSameDay(now, ms) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
        now - ms < 7 * day ->
            SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ms))
        else ->
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(ms))
    }
}

fun formatBubbleTime(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

private fun isSameDay(a: Long, b: Long): Boolean {
    val fmt = SimpleDateFormat("yyyyDDD", Locale.getDefault())
    return fmt.format(Date(a)) == fmt.format(Date(b))
}

/** Short readable id: first 8 chars of the UUID string, uppercase. */
fun shortId(id: String): String = id.take(8).uppercase()
