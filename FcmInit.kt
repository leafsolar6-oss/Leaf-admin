package ng.leafsolar.admin

import com.google.firebase.messaging.FirebaseMessaging

object Fcm {
    // Firebase is auto-initialized by the google-services Gradle plugin.
    fun available(): Boolean = true

    fun token(onResult: (String?) -> Unit) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { onResult(it) }
                .addOnFailureListener { onResult(null) }
        } catch (_: Throwable) { onResult(null) }
    }
}
