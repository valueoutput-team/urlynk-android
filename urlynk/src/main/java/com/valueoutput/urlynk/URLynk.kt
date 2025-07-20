package com.valueoutput.urlynk

import okhttp3.Call
import java.util.Date
import android.net.Uri
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import okhttp3.OkHttpClient
import android.content.Intent
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import androidx.lifecycle.LiveData
import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object URLynk {
    private var appId: String? = null
    private var apiKey: String? = null
    private var deviceId: String? = null
    private var screenWidth: Float? = null
    private var screenHeight: Float? = null
    private var initialLink: String? = null
    private var devicePixelRatio: Float? = null

    private val client = OkHttpClient()
    private val linkData = MutableLiveData<String>()
    private const val BASE_URL = "https://api-xn4bb66p3a-uc.a.run.app/v4"

    val onLinkData: LiveData<String> = linkData

    /**
     * Handles the initial or incoming deep link that opened the app.
     *
     * Call this method from your activity’s `onCreate()` and `onNewIntent()` methods.
     * It should be called before [configure], especially if you're fetching the API key from your server,
     * as it stores the link temporarily and processes it after initialization is complete.
     *
     * Internally, it extracts the link from the intent and triggers link data retrieval.
     */
    fun handleDeepLink(intent: Intent?) {
        val data = intent?.data?.toString()
        if (!data.isNullOrEmpty()) getLinkData(data)
    }

    /**
     * Configure the URLynk service with appId and apiKey.
     * @param context Application context
     * @param appId Your app ID from URLynk
     * @param apiKey Your API key from URLynk
     */
    fun configure(context: Context, appId: String, apiKey: String) {
        this.appId = appId
        this.apiKey = apiKey
        deviceId = getHashedDeviceId(context)

        val metrics = context.resources.displayMetrics
        devicePixelRatio = metrics.density
        screenWidth = metrics.widthPixels / metrics.density
        screenHeight = metrics.heightPixels / metrics.density


        if (initialLink != null) getLinkData(initialLink!!)
        else searchClick()
    }

    /**
     * Generates a shortened version of a long URL.
     *
     * Use this method when you only need URL shortening. For deep linking capabilities with in-app routing,
     * consider using [createDeepLink] instead.
     *
     * The service must be initialized by calling [configure] before using this method.
     *
     * @param url The original URL to be shortened.
     * @param domain Optional branded domain (must be verified in your URLynk account).
     * @param onRes Callback that receives the shortened URL, or null if the request fails.
     */
    fun createShortLink(url: String, domain: String?, onRes: (String?) -> Unit) {
        if (this.apiKey == null) throw Exception("Service not configured. Call configure() before using this method.")

        // Validate URL format
        val uri = try { Uri.parse(url) } catch (e: Exception) { null }
        if (uri == null || uri.scheme.isNullOrEmpty() || uri.host.isNullOrEmpty()) {
            throw IllegalArgumentException("Invalid URL")
        }

        if (domain != null && !domain.matches(Regex("^[a-zA-Z0-9.-]+\$"))) {
            throw IllegalArgumentException("Invalid domain format")
        }

        val payload = JSONObject().apply {
            put("data", url)
            if (domain != null) put("domain", domain)
        }

        val request = Request.Builder()
            .url("$BASE_URL/links")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", apiKey!!)
            .addHeader("x-device-id", deviceId!!)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                logError(e)
                onRes(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = response.body?.string()
                    if (!response.isSuccessful || bodyStr == null) {
                        onRes(null)
                        return
                    }
                    try {
                        val json = JSONObject(bodyStr)
                        val urlOb = json.getJSONObject("data")
                            .getJSONObject("link")
                            .getJSONObject("url")

                        var url = urlOb.optString("custom")
                        if(url.isNullOrEmpty() || url == "null") url = urlOb.getString("default")

                        onRes(url)
                    } catch (e: Exception) {
                        logError(e)
                        onRes(null)
                    }
                }
            }
        })
    }

    /**
     * Create a deep link
     *
     * The service must be initialized by calling [configure] before using this method.
     *
     * @param data Any string payload to encode in the link
     * @param onRes Callback that receives the resulting deep link or null on failure
     */
    fun createDeepLink(data: String, onRes: (String?) -> Unit) {
        if (this.appId == null || this.apiKey == null) {
            throw Exception("Service not configured. Call configure() before using this method.")
        }
        if (data.trim().isEmpty()) {
            throw IllegalArgumentException("Data is required")
        }

        val payload = JSONObject().apply {
            put("appId", appId)
            put("data", data.trim())
        }

        val request = Request.Builder()
            .url("$BASE_URL/links")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", apiKey!!)
            .addHeader("x-device-id", deviceId!!)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                logError(e)
                onRes(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = response.body?.string()
                    if (!response.isSuccessful || bodyStr == null) {
                        onRes(null)
                        return
                    }
                    try {
                        val json = JSONObject(bodyStr)
                        val urlOb = json.getJSONObject("data")
                            .getJSONObject("link")
                            .getJSONObject("url")

                        var url = urlOb.optString("custom")
                        if(url.isNullOrEmpty() || url == "null") url = urlOb.getString("default")

                        onRes(url)
                    } catch (e: Exception) {
                        logError(e)
                        onRes(null)
                    }
                }
            }
        })
    }

    /**
     * Looks up for any recent deep link click associated with this device within the last 24 hours.
     * Auto-invoked if a deferred link is detected. Can also be called manually.
     * If a matching click is found, its associated data will be posted to [onLinkData].
     * The service must be initialized by calling [configure] before using this method.
     */
    fun searchClick() {
        if (this.appId == null || this.apiKey == null) {
            throw Exception("Service not configured. Call configure() before using this method.")
        }

        val payload = JSONObject().apply {
            put("appId", appId)
            put("screenWidth", screenWidth)
            put("screenHeight", screenHeight)
            put("devicePixelRatio", devicePixelRatio)
        }

        val request = Request.Builder()
            .url("$BASE_URL/clicks/find")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", apiKey!!)
            .addHeader("x-device-id", deviceId!!)
            .addHeader("user-agent", "android")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                logError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = response.body?.string()
                    if (!response.isSuccessful || bodyStr == null) {
                        return
                    }
                    try {
                        val json = JSONObject(bodyStr)
                        val data = json.getJSONObject("data").getString("data")
                        linkData.postValue(data)
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            }
        })
    }

    private fun getLinkData(link: String) {
        if (this.appId == null || this.apiKey == null) {
            initialLink = link
            return
        }

        val params = try { Uri.parse(link).pathSegments } catch (e: Exception) { null }
        if (params == null || params.size != 2) return

        val request = Request.Builder()
            .url("$BASE_URL/links/${params[0]}/${params[1]}")
            .get()
            .addHeader("x-api-key", apiKey!!)
            .addHeader("x-device-id", deviceId!!)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = logError(e)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = response.body?.string()
                    if (!response.isSuccessful || bodyStr == null) return

                    try {
                        val json = JSONObject(bodyStr)
                        val data = json.getJSONObject("data").getString("linkData")
                        linkData.postValue(data)
                        initialLink = null
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            }
        })
    }

    private fun logError(e: Exception) {
        val log = JSONObject().apply {
            put("level", 1)
            put("version", "1.0.0")
            put("time", Date().time)
            put("message", e.message)
            put("stackTrace", e.stackTraceToString())
        }
        val logs = JSONArray().put(log)
        val data = JSONObject().put("data", logs)
        val body = data.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/logs")
            .post(body)
            .addHeader("x-api-key", apiKey!!)
            .addHeader("x-device-id", deviceId!!)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }

    @SuppressLint("HardwareIds")
    private fun getHashedDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return sha256(androidId)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
