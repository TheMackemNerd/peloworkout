package com.digitalelysium.peloworkout.strava

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.digitalelysium.peloworkout.BuildConfig

class StravaClient(private val activity: Activity) {

    private val clientId = BuildConfig.STRAVA_CLIENT_ID
    private val clientSecret = BuildConfig.STRAVA_CLIENT_SECRET
    private val redirectUri = "peloworkout://strava-callback"
    private val scope = "activity:write,read"

    private data class StravaToken(val access: String, val refresh: String, val expiresAt: Long)

    private fun prefs() = activity.getSharedPreferences("strava", Context.MODE_PRIVATE)

    fun hasToken(): Boolean = loadToken() != null

    fun startAuth() {
        val url = "https://www.strava.com/oauth/mobile/authorize" +
                "?client_id=$clientId" +
                "&redirect_uri=$redirectUri" +
                "&response_type=code" +
                "&approval_prompt=auto" +
                "&scope=$scope"
        activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    fun handleRedirect(uri: Uri) {
        if (uri.scheme != "peloworkout" || uri.host != "strava-callback") return
        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        if (error != null) {
            Toast.makeText(activity, "Strava auth error: $error", Toast.LENGTH_SHORT).show()
            return
        }
        if (code == null) {
            Toast.makeText(activity, "No code in Strava callback", Toast.LENGTH_SHORT).show()
            return
        }
        exchangeCodeForToken(code)
    }

    fun ensureToken(onReady: (String) -> Unit, onNeedLogin: () -> Unit) {
        val tok = loadToken() ?: return onNeedLogin()
        val now = System.currentTimeMillis() / 1000
        if (tok.expiresAt - 60 > now) { onReady(tok.access); return }

        // refresh
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("grant_type", "refresh_token")
            .add("refresh_token", tok.refresh)
            .build()
        val req = Request.Builder().url("https://www.strava.com/oauth/token").post(body).build()
        Thread {
            try {
                OkHttpClient().newCall(req).execute().use { resp ->
                    val json = resp.body?.string() ?: return@use
                    val access = "\"access_token\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(json)?.groupValues?.get(1) ?: return@use
                    val refresh = "\"refresh_token\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(json)?.groupValues?.get(1) ?: return@use
                    val expiresAt = "\"expires_at\"\\s*:\\s*(\\d+)".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 0L
                    saveToken(StravaToken(access, refresh, expiresAt))
                    activity.runOnUiThread { onReady(access) }
                }
            } catch (_: Exception) { }
        }.start()
    }

    fun uploadTcx(accessToken: String, tcx: ByteArray, name: String) {
        val fileReqBody =
            tcx.toRequestBody("application/vnd.garmin.tcx+xml".toMediaTypeOrNull(), 0, tcx.size)
        val multi = MultipartBody.Builder().setType(MultipartBody.Companion.FORM)
            .addFormDataPart("data_type", "tcx")
            .addFormDataPart("trainer", "true")
            .addFormDataPart("name", name)
            .addFormDataPart("file", "workout.tcx", fileReqBody)
            .build()

        val req = Request.Builder()
            .url("https://www.strava.com/api/v3/uploads")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(multi)
            .build()

        Thread {
            try {
                OkHttpClient().newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        activity.runOnUiThread {
                            Toast.makeText(activity, "Upload complete", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        activity.runOnUiThread {
                            Toast.makeText(activity, "Upload failed: ${resp.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Upload error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // -------------------- private helpers --------------------

    private fun exchangeCodeForToken(code: String) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", redirectUri)
            .build()
        val req = Request.Builder().url("https://www.strava.com/oauth/token").post(body).build()
        Thread {
            try {
                OkHttpClient().newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        activity.runOnUiThread {
                            Toast.makeText(activity, "Strava token failed ${resp.code}", Toast.LENGTH_SHORT).show()
                        }
                        return@use
                    }
                    val access = "\"access_token\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(bodyStr)?.groupValues?.get(1)
                    val refresh = "\"refresh_token\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(bodyStr)?.groupValues?.get(1)
                    val expiresAt = "\"expires_at\"\\s*:\\s*(\\d+)".toRegex().find(bodyStr)?.groupValues?.get(1)?.toLong() ?: 0L
                    if (access == null || refresh == null || expiresAt == 0L) {
                        activity.runOnUiThread { Toast.makeText(activity, "Strava parse error", Toast.LENGTH_SHORT).show() }
                        return@use
                    }
                    saveToken(StravaToken(access, refresh, expiresAt))
                    activity.runOnUiThread { Toast.makeText(activity, "Strava connected", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                activity.runOnUiThread { Toast.makeText(activity, "Strava error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun loadToken(): StravaToken? {
        val p = prefs()
        val a = p.getString("access", null) ?: return null
        val r = p.getString("refresh", null) ?: return null
        val e = p.getLong("expires", 0L)
        if (e == 0L) return null
        return StravaToken(a, r, e)
    }

    private fun saveToken(t: StravaToken) {
        prefs().edit {
                putString("access", t.access)
                    .putString("refresh", t.refresh)
                    .putLong("expires", t.expiresAt)
        }
    }
}