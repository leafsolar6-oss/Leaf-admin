package ng.leafsolar.admin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushStore.saveToken(this, token)
        // Best-effort registration; user must be logged in first.
        Thread { try { PushRegistrar.register(this, token) } catch (_: Exception) {} }.start()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"] ?: "Leaf Solar"
        val body = message.notification?.body
            ?: message.data["body"] ?: "New update"
        val orderId = message.data["order_id"]
        showNotification(title, body, orderId)
    }

    private fun showNotification(title: String, body: String, orderId: String?) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Orders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New orders and order updates"
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_orders", true)
            orderId?.let { putExtra("order_id", it) }
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultSound)
            .setContentIntent(pi)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt(), notif)
    }

    companion object {
        const val CHANNEL_ID = "leaf_orders"
    }
}
