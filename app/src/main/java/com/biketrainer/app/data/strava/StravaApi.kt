package com.biketrainer.app.data.strava

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class StravaApi(
    private val context: Context
) {
    companion object {
        private const val TAG = "StravaApi"
        private const val CLIENT_ID = "185812"
        private const val CLIENT_SECRET = "7ab3120b3567faf5eb6785f268773fecb3004c09"
        private const val REDIRECT_URI = "http://localhost/exchange_token"

        private const val AUTH_URL = "https://www.strava.com/oauth/authorize"
        private const val TOKEN_URL = "https://www.strava.com/oauth/token"
        private const val UPLOAD_URL = "https://www.strava.com/api/v3/uploads"

        private const val PREFS_NAME = "strava_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
    }

    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAuthenticated(): Boolean {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
        return accessToken != null && System.currentTimeMillis() / 1000 < expiresAt
    }

    fun startOAuthFlow() {
        val authUrl = "$AUTH_URL?" +
                "client_id=$CLIENT_ID&" +
                "redirect_uri=$REDIRECT_URI&" +
                "response_type=code&" +
                "scope=activity:write&" +
                "approval_prompt=auto"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    suspend fun handleOAuthCallback(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("code", code)
                .add("grant_type", "authorization_code")
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val accessToken = json.getString("access_token")
                val refreshToken = json.getString("refresh_token")
                val expiresAt = json.getLong("expires_at")

                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, accessToken)
                    .putString(KEY_REFRESH_TOKEN, refreshToken)
                    .putLong(KEY_EXPIRES_AT, expiresAt)
                    .apply()

                Log.d(TAG, "OAuth successful")
                true
            } else {
                Log.e(TAG, "OAuth failed: ${response.code}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "OAuth error", e)
            false
        }
    }

    suspend fun uploadWorkout(tcxFile: File): UploadResult = withContext(Dispatchers.IO) {
        try {
            if (!isAuthenticated()) {
                return@withContext UploadResult.NotAuthenticated
            }

            val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
                ?: return@withContext UploadResult.NotAuthenticated

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    tcxFile.name,
                    tcxFile.asRequestBody("application/xml".toMediaTypeOrNull())
                )
                .addFormDataPart("data_type", "tcx")
                .addFormDataPart("name", "Indoor Cycling")
                .addFormDataPart("description", "Uploaded from Bike Trainer App")
                .build()

            val request = Request.Builder()
                .url(UPLOAD_URL)
                .header("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val uploadId = json.getLong("id")
                Log.d(TAG, "Upload successful: $uploadId")
                UploadResult.Success(uploadId)
            } else if (response.code == 401 || response.code == 403) {
                // Token revoked or unauthorized - clear local tokens
                Log.w(TAG, "Authentication failed - tokens may have been revoked")
                disconnectStrava()
                UploadResult.NotAuthenticated
            } else {
                Log.e(TAG, "Upload failed: ${response.code} - $responseBody")
                UploadResult.Error("Upload failed: ${response.code}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Upload error", e)
            UploadResult.Error("Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Upload error", e)
            UploadResult.Error("Error: ${e.message}")
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Logged out from Strava")
    }

    fun disconnectStrava() {
        // Clear all stored tokens
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
        Log.d(TAG, "Disconnected from Strava")
    }
}

sealed class UploadResult {
    data class Success(val uploadId: Long) : UploadResult()
    data class Error(val message: String) : UploadResult()
    object NotAuthenticated : UploadResult()
}
