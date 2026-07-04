package com.mallorca.explorer.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mallorca.explorer.MainActivity
import com.mallorca.explorer.R
import com.mallorca.explorer.core.domain.model.Event

const val CHANNEL_ID = "mallorca_events"

fun createNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.notification_channel_desc)
    }
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}

fun sendEventNotification(context: Context, event: Event, notifId: Int) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

    val tapIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        context, notifId, tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val title = event.titleEs.ifEmpty { event.title }
    val priceText = if (event.isFree) context.getString(R.string.explore_events_free) else event.price ?: ""
    val body = "${event.category.emoji} ${event.municipality}${if (priceText.isNotEmpty()) " · $priceText" else ""}"

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.notification_title_prefix, title))
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(notifId, notification)
}
