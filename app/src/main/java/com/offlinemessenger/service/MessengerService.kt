package com.offlinemessenger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.offlinemessenger.MainActivity
import com.offlinemessenger.OfflineMessengerApp
import com.offlinemessenger.R
import com.offlinemessenger.repository.NotifyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the BLE radio alive while the app is in the
 * background, so incoming messages and relay traffic can arrive.
 *
 * Justification: without a foreground service, Android stops BLE advertising,
 * scanning and the GATT server minutes after the app leaves the foreground,
 * which would silently break offline messaging. The service does no network
 * I/O (the app has no INTERNET permission at all).
 *
 * Users can stop the radio from Settings; stopping the service ends
 * advertising/scanning/listening entirely.
 */
class MessengerService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "om_radio"
        const val MSG_CHANNEL_ID = "om_messages"
        const val REQ_CHANNEL_ID = "om_requests"
        const val ACTION_STOP = "com.offlinemessenger.STOP_RADIO"
    }

    private val locator get() = (application as OfflineMessengerApp).locator
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        createMessageChannel()
        createRequestChannel()
        // Wire packet handling, ACK/timeout loops, route adverts.
        locator.chatRepository.start()
        scope.launch {
            locator.chatRepository.notifications.collect { postMessageNotification(it) }
        }
        scope.launch {
            locator.chatRepository.incomingRequests.collect { postRequestNotification(it.peerId) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            locator.bleTransport.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        val radioUp = locator.bleTransport.start()
        if (!radioUp) {
            // Bluetooth off or unavailable: keep the service so we retry when
            // the user enables Bluetooth (transport observes adapter state).
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        locator.bleTransport.stop()
        super.onDestroy()
    }

    private fun createMessageChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            MSG_CHANNEL_ID,
            getString(R.string.msg_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = getString(R.string.msg_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    private fun createRequestChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            REQ_CHANNEL_ID,
            "Chat requests",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Someone nearby wants to chat with you" }
        manager.createNotificationChannel(channel)
    }

    /**
     * "X wants to chat" notification with Accept / Decline buttons.
     * Accept sends the handshake reply and opens the chat immediately.
     */
    private fun postRequestNotification(peerId: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        scope.launch(Dispatchers.IO) {
            val contact = locator.peerRepository.contacts.firstOrNull()
                ?.firstOrNull { it.deviceId == peerId }
            val name = contact?.displayName ?: "Someone nearby"
            val largeIcon = contact?.photoB64?.takeIf { it.isNotBlank() }?.let { b64 ->
                runCatching {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }

            val acceptIntent = Intent(this@MessengerService, ChatActionReceiver::class.java).apply {
                action = ChatActionReceiver.ACTION_ACCEPT
                putExtra(ChatActionReceiver.EXTRA_PEER_ID, peerId)
            }
            val declineIntent = Intent(this@MessengerService, ChatActionReceiver::class.java).apply {
                action = ChatActionReceiver.ACTION_DECLINE
                putExtra(ChatActionReceiver.EXTRA_PEER_ID, peerId)
            }
            val acceptPending = PendingIntent.getBroadcast(
                this@MessengerService, peerId.hashCode(), acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val declinePending = PendingIntent.getBroadcast(
                this@MessengerService, peerId.hashCode() + 1, declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val openIntent = Intent(this@MessengerService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                this@MessengerService, peerId.hashCode() + 2, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(this@MessengerService, REQ_CHANNEL_ID)
                .setContentTitle("Chat request")
                .setContentText("$name wants to chat with you")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_launcher_foreground, "Accept", acceptPending,
                    ).build(),
                )
                .addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_launcher_foreground, "Decline", declinePending,
                    ).build(),
                )
                .apply { largeIcon?.let { setLargeIcon(it) } }
                .build()
            NotificationManagerCompat.from(this@MessengerService)
                .notify(ChatActionReceiver.TAG_REQUEST, peerId.hashCode(), notification)
        }
    }

    private fun postMessageNotification(event: NotifyEvent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_chat", event.conversationId)
        }
        val pending = PendingIntent.getActivity(
            this, event.conversationId.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, MSG_CHANNEL_ID)
            .setContentTitle(event.title)
            .setContentText(event.body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this)
            .notify(event.conversationId.hashCode(), notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.service_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
}
