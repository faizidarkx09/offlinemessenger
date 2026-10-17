package com.offlinemessenger.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.offlinemessenger.MainActivity
import com.offlinemessenger.OfflineMessengerApp
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the Accept / Decline buttons on an incoming chat-request
 * notification. Accept sends the handshake reply and opens the chat.
 */
class ChatActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val peerId = intent.getStringExtra(EXTRA_PEER_ID) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as OfflineMessengerApp
                val repo = app.locator.chatRepository
                val uuid = runCatching { UUID.fromString(peerId) }.getOrNull() ?: return@launch
                when (intent.action) {
                    ACTION_ACCEPT -> {
                        repo.acceptConnectionRequest(uuid)
                        // Dismiss the request notification…
                        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                            .cancel(TAG_REQUEST, peerId.hashCode())
                        // …and open the chat right away.
                        val open = Intent(context, MainActivity::class.java).apply {
                            putExtra("open_chat", peerId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(open)
                    }
                    ACTION_DECLINE -> {
                        repo.declineConnectionRequest(uuid)
                        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                            .cancel(TAG_REQUEST, peerId.hashCode())
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.offlinemessenger.action.CONN_ACCEPT"
        const val ACTION_DECLINE = "com.offlinemessenger.action.CONN_DECLINE"
        const val EXTRA_PEER_ID = "peer_id"
        const val TAG_REQUEST = "connreq"
    }
}
