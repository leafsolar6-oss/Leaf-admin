package ng.leafsolar.admin

import android.content.Context
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object PushStore {
    private const val PREF = "leaf-admin"
    fun saveToken(ctx: Context, token: String) {
        ctx.getSharedPreferences(PREF, 0).edit().putString("fcm_token", token).apply()
    }
    fun getToken(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, 0).getString("fcm_token", null)

    fun getCreds(ctx: Context): Pair<String, String>? {
        val p = ctx.getSharedPreferences(PREF, 0)
        val u = p.getString("u", null); val pw = p.getString("p", null)
        return if (u != null && pw != null) u to pw else null
    }
}

object PushRegistrar {
    private val client = OkHttpClient()

    fun register(ctx: Context, token: String) {
        val (user, pass) = PushStore.getCreds(ctx) ?: return
        val cred = okhttp3.Credentials.basic(user.trim(), pass.trim())
        val json = JSONObject().apply {
            put("token", token)
            put("name", android.os.Build.MODEL)
        }.toString()
        val req = Request.Builder()
            .url("https://leafsolar.ng/wp-json/lfx/v1/register-push")
            .header("Authorization", cred)
            .post(json.toRequestBody("application/json".toMediaTypeOrNull()!!))
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
        }
    }

    fun unregister(ctx: Context, token: String) {
        val (user, pass) = PushStore.getCreds(ctx) ?: return
        val cred = okhttp3.Credentials.basic(user.trim(), pass.trim())
        val json = JSONObject().apply { put("token", token); put("remove", true) }.toString()
        val req = Request.Builder()
            .url("https://leafsolar.ng/wp-json/lfx/v1/register-push")
            .header("Authorization", cred)
            .post(json.toRequestBody("application/json".toMediaTypeOrNull()!!))
            .build()
        try { client.newCall(req).execute().close() } catch (_: Exception) {}
    }
}
