package com.offlinemessenger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.offlinemessenger.OfflineMessengerApp
import com.offlinemessenger.R

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
        const val ACTION_STOP = "com.offlinemessenger.STOP_RADIO"
    }

    private val locator get() = (application as OfflineMessengerApp).locator

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Wire packet handling, ACK/timeout loops, route adverts.
        locator.chatRepository.start()
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
        locator.bleTransport.stop()
        super.onDestroy()
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
