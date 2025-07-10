package com.ble_finder.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ble_finder.MainActivity
import com.ble_finder.R
import com.ble_finder.data.SavedDevice

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "device_notifications"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Device Notifications"
            val descriptionText = "Notifications for device range status"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showDeviceOutOfRangeNotification(device: SavedDevice) {
        // Create Google Maps intent
        val mapsUri = Uri.parse("https://www.google.com/maps?q=${device.lastKnownLatitude},${device.lastKnownLongitude}")
        val mapsIntent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            device.macAddress.hashCode(),
            mapsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Device Out of Range")
            .setContentText("${device.name} is no longer in range. Tap to view last known location.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(device.macAddress.hashCode(), notification)
    }

    fun cancelNotification(device: SavedDevice) {
        notificationManager.cancel(device.macAddress.hashCode())
    }
} 