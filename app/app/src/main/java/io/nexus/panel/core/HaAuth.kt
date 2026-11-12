package io.nexus.panel.core

import android.net.Uri
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * Home Assistant login the proper way — the same OAuth2 flow the official
 * Companion app uses. No trusted networks, no IP allow-lists, no stored
 * passwords: the user approves once in-app, the panel keeps a refresh token
 * (revocable per-device from the HA UI) and mints its own access tokens.
 */
object HaAuth {
    const val CLIENT_ID = "https://nspanel-nexus.local/panel"
    const val REDIRECT_URI = "nspanel-nexus://auth"

    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    fun authorizeUrl(haBase: String): String {
        val base = haBase.trim().trimEnd('/')
        return base + "/auth/authorize?response_type=code" +
            "&client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8") +
            "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")
    }

    /** Origin (scheme://host:port) of a dashboard URL — token calls stay same-origin. */
    fun originOf(dashboardUrl: String): String {
        val u = Uri.parse(dashboardUrl)
        val authority = u.authority ?: dashboardUrl.trim().trimEnd('/')
        val scheme = u.scheme ?: "http"
        return if (u.authority != null) "$scheme://$authority" else authority
    }

    fun exchangeCode(origin: String, code: String, cb: (JSONObject?) -> Unit) {
        postToken(
            origin,
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", CLIENT_ID)
                .add("redirect_uri", REDIRECT_URI)
                .build(),
            cb,
        )
    }

    fun refresh(origin: String, refreshToken: String, cb: (JSONObject?) -> Unit) {
        postToken(
            origin,
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", CLIENT_ID)
                .build(),
            cb,
        )
    }

    private fun postToken(origin: String, body: RequestBody, cb: (JSONObject?) -> Unit) {
        val req = Request.Builder()
            .url(origin.trimEnd('/') + "/auth/token")
            .post(body)
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cb(null)
            override fun onResponse(call: Call, resp: Response) {
                resp.use {
                    if (!it.isSuccessful) {
                        cb(null)
                        return
                    }
                    try {
                        cb(JSONObject(it.body!!.string()))
                    } catch (_: Exception) {
                        cb(null)
                    }
                }
            }
        })
    }

    /** JS that installs a token pair into the HA frontend session (key verified on-device HA). */
    fun injectJs(tokens: JSONObject): String {
        val raw = tokens.toString().replace("\\", "\\\\").replace("'", "\\'")
        return "try{localStorage.setItem('hassTokens','$raw');}catch(e){}"
    }

    fun isLoggedIn(): Boolean = Settings.get("ha_refresh_token", "").isNotBlank()

    fun logout() {
        Settings.set("ha_refresh_token", "")
    }
}
