package ng.leafsolar.admin

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

object Fcm {
    @Volatile private var initialized = false

    fun init(ctx: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val senderId = ctx.getString(R.string.fcm_sender_id)
            val appId = ctx.getString(R.string.fcm_app_id)
            val apiKey = ctx.getString(R.string.fcm_api_key)
            val projectId = ctx.getString(R.string.fcm_project_id)
            // Skip until the real Firebase credentials are in strings.xml
            if (senderId.startsWith("REPLACE_") || appId.startsWith("REPLACE_")) {
                initialized = true
                return
            }
            try {
                if (FirebaseApp.getApps(ctx).isEmpty()) {
                    val opts = FirebaseOptions.Builder()
                        .setApplicationId(appId)
                        .setApiKey(apiKey)
                        .setProjectId(projectId)
                        .setGcmSenderId(senderId)
                        .build()
                    FirebaseApp.initializeApp(ctx, opts)
                }
            } catch (_: Throwable) {}
            initialized = true
        }
    }

    fun available(ctx: Context): Boolean {
        val s = ctx.getString(R.string.fcm_sender_id)
        return !s.startsWith("REPLACE_")
    }

    fun token(onResult: (String?) -> Unit) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { onResult(it) }
                .addOnFailureListener { onResult(null) }
        } catch (_: Throwable) { onResult(null) }
    }
}
