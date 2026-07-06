package org.siloserver.silo.android.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.siloserver.silo.android.MainActivity
import org.siloserver.silo.android.R
import org.siloserver.silo.model.notifications.NotificationRow
import org.siloserver.silo.repository.NotificationsRepository

class PushNotificationPresenter(
    private val context: Context,
    private val notificationsRepository: NotificationsRepository,
) {
    suspend fun present(deliveryId: String) {
        fetch(deliveryId)
        if (!canPostNotifications()) return
        ensureChannel()

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DELIVERY_ID, deliveryId)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Silo has a new notification")
            .setContentText("Open Silo to view it.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Open Silo to view it."))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    deliveryId.hashCode(),
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(deliveryId.hashCode(), notification)
        }
    }

    private suspend fun fetch(deliveryId: String): NotificationRow? =
        runCatching {
            notificationsRepository.refresh()
            notificationsRepository.rows.value.firstOrNull { it.id == deliveryId }
        }.getOrNull()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Notifications",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Private Silo notification alerts"
        }
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "silo_notifications"
        const val EXTRA_DELIVERY_ID = "silo_notification_delivery_id"
    }
}
