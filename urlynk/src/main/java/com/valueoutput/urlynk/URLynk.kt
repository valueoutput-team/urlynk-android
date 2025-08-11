package com.valueoutput.urlynk

import okhttp3.Call
import java.util.Date
import android.net.Uri
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Response
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import okhttp3.OkHttpClient
import android.content.Intent
import android.content.Context
import android.provider.Settings
import android.view.WindowManager
import java.security.MessageDigest
import androidx.lifecycle.LiveData
import android.util.DisplayMetrics
import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object URLynk {
    private var screenWidth: Int? = null
    private var screenHeight: Int? = null
    private var userAgent: String? = null
    private var initialLink: String? = null
    private var devicePixelRatio: Int? = null
    @Volatile private var appId: String? = null
    @Volatile private var apiKey: String? = null
    private var baseURL = "https://api-xn4bb66p3a-uc.a.run.app/v4"

    private const val VERSION = "1.1.0"
    private const val CONFIG_ERR = "Service not configured. Call configure() before using this method."

    private val client = OkHttpClient()
    private val linkData = MutableLiveData<Pair<String, String>>()
    val onLinkData: LiveData<Pair<String, String>> = linkData

    /** -----------------------
     *  Public Methods
     *  ----------------------- */

    /**
     * Handles the initial or incoming deep link that opened the app.
     *
     * Call this method from your activity’s `onCreate()` and `onNewIntent()` methods.
     * It should be called before [configure], especially if you're fetching the API key from your server,
     * as it stores the link temporarily and processes it after configuration is completed.
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
        synchronized(this) {
            this.appId = appId
            this.apiKey = apiKey
        }
        userAgent = "Android; ${context.packageName}; ${getHashedDeviceId(context)}; $VERSION"
        setScreenMetrics(context)
        fetchBaseURL {
            initialLink?.let { getLinkData(it) } ?: searchClick()
        }
    }

    /**
     * Generates a shortened version of a long URL.
     *
     * Use this method when you only need URL shortening. For deep linking capabilities with in-app routing,
     * consider using [createDeepLink] instead.
     *
     * The service must be initialized by calling [configure] before using this method.
     *
     * @param data Link data
     * @param onRes Callback that receives the shortened URL result
     */
    fun createShortLink(data: LinkModel, onRes: (Result<String>) -> Unit) {
        if (appId == null || apiKey == null) return onRes(Result.failure(IllegalStateException(CONFIG_ERR)))

        val err = data.validate()
        if(err != null) return onRes(Result.failure(IllegalArgumentException(err)))

        val payload = JSONObject(data.toJson())

        sendRequest("$baseURL/links", payload, onSuccess = { data ->
            val urlOb = data.getJSONObject("link").getJSONObject("url")
            val shortUrl = urlOb.optString("custom").takeIf { it.isNotBlank() && it != "null" }
                ?: urlOb.optString("default")
            onRes(Result.success(shortUrl))
        }, onFailure = {msg -> onRes(Result.failure(Exception(msg))) })
    }

    /**
     * Create a deep link
     *
     * The service must be initialized by calling [configure] before using this method.
     *
     * @param data Any string payload to encode in the link
     * @param onRes Callback that receives the resulting deep link
     */
    fun createDeepLink(data: String, onRes: (Result<String>) -> Unit) {
        if (appId == null || apiKey == null) return onRes(Result.failure(IllegalStateException(CONFIG_ERR)))
        if (data.isBlank()) return onRes(Result.failure(IllegalArgumentException("Data must not be empty")))

        val payload = JSONObject().apply {
            put("appId", appId)
            put("data", data.trim())
        }

        sendRequest("$baseURL/links", payload, onSuccess = { data ->
            val urlOb = data.getJSONObject("link").getJSONObject("url")
            val link = urlOb.optString("custom").takeIf { it.isNotBlank() && it != "null" }
                ?: urlOb.optString("default")
            onRes(Result.success(link))
        }, onFailure = { err ->
            onRes(Result.failure(Exception(err)))
        })
    }

    /**
     * Looks up for any recent deep link click associated with this device within the last 24 hours.
     * Auto-invoked if a deferred link is detected. Can also be called manually.
     * If a matching click is found, its associated link & data will be posted to [onLinkData].
     * The service must be initialized by calling [configure] before using this method.
     */
    fun searchClick() {
        if (appId == null || apiKey == null) throw IllegalStateException(CONFIG_ERR)

        val payload = JSONObject().apply {
            put("appId", appId)
            put("screenWidth", screenWidth)
            put("screenHeight", screenHeight)
            put("devicePixelRatio", devicePixelRatio)
        }

        sendRequest("$baseURL/clicks/find", payload, onSuccess = { data ->
            linkData.postValue(Pair(data.getString("link"), data.getString("data")))
        })
    }

    /** -----------------------
     *  Private Helpers
     *  ----------------------- */

    private fun fetchBaseURL(onComplete: () -> Unit) {
        sendRequest("$baseURL/urls", null, onSuccess = { data ->
            baseURL = data.optString("baseURL", baseURL)
            onComplete()
        }, onFailure = { onComplete() }, method = "GET")
    }

    private fun getLinkData(link: String) {
        if (appId == null || apiKey == null) {
            initialLink = link
            return
        }
        val params = runCatching { Uri.parse(link).pathSegments }.getOrNull()
        if (params?.size != 2) return

        sendRequest("$baseURL/links/${params[0]}/${params[1]}", null, onSuccess = { data ->
            val d = data.getString("linkData")
            linkData.postValue(Pair(link, d))
            initialLink = null
        })
    }

    private fun logError(e: Exception) {
        val log = JSONObject().apply {
            put("level", 1)
            put("time", Date().time)
            put("stackTrace", e.stackTraceToString())
            put("message", e.message ?: "Unknown error")
        }
        sendRequest("$baseURL/logs", JSONObject().put("data", JSONArray().put(log)), {}, {}, "POST", true)
    }

    private fun sendRequest(
        url: String,
        jsonPayload: JSONObject?,
        onSuccess: (JSONObject) -> Unit,
        onFailure: (String?) -> Unit = {},
        method: String = "POST",
        fromLogs: Boolean = false,
    ) {
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey ?: "")
            .addHeader("user-agent", userAgent ?: "")

        if (method == "POST" && jsonPayload != null) {
            requestBuilder.post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
        } else if (method == "GET") {
            requestBuilder.get()
        }

        client.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if(!fromLogs) logError(e)
                onFailure(e.message)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val bodyStr = it.body?.string()
                        if(bodyStr.isNullOrEmpty()) {
                            onFailure(null)
                            return
                        }

                        val json = JSONObject(bodyStr)
                        if (!it.isSuccessful) {
                            val msg  = json.optString("message")
                            onFailure(msg)
                            return
                        }

                        val ob = json.getJSONObject("data")
                        onSuccess(ob)
                    } catch (e: Exception) {
                        if(!fromLogs) logError(e)
                        onFailure(e.message)
                    }
                }
            }
        })
    }

    private fun setScreenMetrics(context: Context) {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
            screenWidth = windowMetrics.bounds.width()
            screenHeight = windowMetrics.bounds.height()
            devicePixelRatio = context.resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            devicePixelRatio = metrics.densityDpi
        }
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
