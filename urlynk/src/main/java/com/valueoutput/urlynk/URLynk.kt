package com.valueoutput.urlynk

import okhttp3.Call
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
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import okhttp3.MediaType.Companion.toMediaType
import androidx.core.content.pm.PackageInfoCompat
import okhttp3.RequestBody.Companion.toRequestBody

data class LinkData(
    val link: String? = null,
    val data: String? = null,
    val error: String? = null
)

object URLynk {
    private var screenWidth: Int? = null
    private var screenHeight: Int? = null
    private var userAgent: String? = null
    private var initialLink: String? = null
    private var maxClickSearch: Int? = null
    private var devicePixelRatio: Int? = null
    @Volatile private var apiKey: String? = null
    private var baseURL = "https://api-xn4bb66p3a-uc.a.run.app/v4"

    private const val VERSION = "1.3.1"
    private const val PREF_NAME = "urlynk_pref"
    private const val VERSION_KEY = "last_app_version"
    private const val COUNT_KEY = "click_search_count"
    private const val CONFIG_ERR = "Service not configured. Call configure() before using this method."

    private val client = OkHttpClient()
    private val linkData = MutableLiveData<LinkData>()
    val onLinkData: LiveData<LinkData> = linkData

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
     * Configure the URLynk service with your API KEY.
     * @param context Application context
     * @param apiKey Your API key from URLynk
     */
    fun configure(context: Context, apiKey: String) {
        synchronized(this) {
            this.apiKey = apiKey
        }
        userAgent = "Android; ${context.packageName}; ${getHashedDeviceId(context)}; $VERSION"
        fetchBaseURL {
            initialLink?.let { getLinkData(it) } ?: searchClick(context)
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
        if (apiKey == null) return onRes(Result.failure(IllegalStateException(CONFIG_ERR)))

        val err = data.validate()
        if(err != null) return onRes(Result.failure(IllegalArgumentException(err)))

        val payload = JSONObject(data.toJson())

        sendRequest(
            "$baseURL/links",
            payload,
            onSuccess = { d ->
                val urlOb = d.getJSONObject("link").getJSONObject("url")
                val shortUrl = urlOb.optString("custom").takeIf { it.isNotBlank() && it != "null" }
                    ?: urlOb.optString("default")
                onRes(Result.success(shortUrl))
            },
            onFailure = { msg, _ -> onRes(Result.failure(Exception(msg))) }
        )
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
        if (apiKey == null) return onRes(Result.failure(IllegalStateException(CONFIG_ERR)))
        if (data.isBlank()) return onRes(Result.failure(IllegalArgumentException("Data cannot be empty")))

        val payload = JSONObject().apply {
            put("appId", "")
            put("data", data.trim())
        }

        sendRequest(
            "$baseURL/links",
            payload,
            onSuccess = { d ->
                val urlOb = d.getJSONObject("link").getJSONObject("url")
                val link = urlOb.optString("custom").takeIf { it.isNotBlank() && it != "null" }
                    ?: urlOb.optString("default")
                onRes(Result.success(link))
            },
            onFailure = { err, _ -> onRes(Result.failure(Exception(err))) }
        )
    }

    /** -----------------------
     *  Private Helpers
     *  ----------------------- */

    private fun fetchBaseURL(onComplete: () -> Unit) {
        sendRequest(
            "$baseURL/urls",
            null,
            onSuccess = { data ->
                baseURL = data.optString("baseURL", baseURL)
                maxClickSearch = data.optInt("maxClickSearch")
                onComplete()
            },
            onFailure = { _, _ -> onComplete() }
        )
    }

    private fun getLinkData(link: String) {
        if (apiKey == null) {
            initialLink = link
            return
        }

        val params = runCatching { Uri.parse(link).pathSegments }.getOrNull()
        if (params?.size != 2) return

        sendRequest(
            "$baseURL/links/${params[0]}/${params[1]}",
            null,
            onSuccess = { data ->
                val d = data.getString("linkData")
                emitLinkData(link, d)
                initialLink = null
            },
            onFailure = { err, _ -> emitLinkData(error = err) }
        )
    }

    private fun searchClick(context: Context) {
        if (apiKey == null) throw IllegalStateException(CONFIG_ERR)

        val prefs = getPrefs(context)
        val isLive = fromStore(context)
        val version = getVersionCode(context)
        val count = prefs.getInt(COUNT_KEY, 0)
        val lastVersion = prefs.getLong(VERSION_KEY, -1L)
        val reset = version != lastVersion
        if(isLive && !reset && maxClickSearch != null && count >= maxClickSearch!!) return

        setScreenMetrics(context)
        val payload = JSONObject().apply {
            put("isLive", isLive)
            put("versionCode", version)
            put("screenWidth", screenWidth)
            put("screenHeight", screenHeight)
            put("devicePixelRatio", devicePixelRatio)
            put("osVersion", Build.VERSION.RELEASE ?: "")
        }

        sendRequest(
            "$baseURL/clicks/find",
            payload,
            onSuccess = { data ->
                emitLinkData(data.getString("link"), data.getString("data"))
                onSearched(prefs, version, if (reset) 1 else count + 1)
            },
            onFailure = { err, code ->
                emitLinkData(error = err)
                if(code == 404) onSearched(prefs, version, if (reset) 1 else count + 1)
            }
        )
    }

    private fun logError(e: Exception) {
        val log = JSONObject().apply {
            put("level", 1)
            put("time", System.currentTimeMillis())
            put("stackTrace", e.stackTraceToString())
            put("message", e.message ?: "Unknown error")
        }

        sendRequest(
            "$baseURL/logs",
            JSONObject().put("data", JSONArray().put(log)),
            {},
            {_, _ ->},
            true
        )
    }

    private fun sendRequest(
        url: String,
        jsonPayload: JSONObject?,
        onSuccess: (JSONObject) -> Unit,
        onFailure: (String?, Int?) -> Unit = { _, _ -> },
        fromLogs: Boolean = false,
    ) {
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey ?: "")
            .addHeader("user-agent", userAgent ?: "")

        if (jsonPayload == null) {
            requestBuilder.get()
        } else {
            requestBuilder.post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
        }

        client.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if(!fromLogs) logError(e)
                onFailure(e.message, null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val bodyStr = it.body?.string()
                        if(bodyStr.isNullOrEmpty()) {
                            onFailure(null, it.code)
                            return
                        }

                        val json = JSONObject(bodyStr)
                        if (!it.isSuccessful) {
                            val msg  = json.optString("message")
                            onFailure(msg, it.code)
                            return
                        }

                        val ob = json.getJSONObject("data")
                        onSuccess(ob)
                    } catch (e: Exception) {
                        if(!fromLogs) logError(e)
                        onFailure(e.message, it.code)
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
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            devicePixelRatio = metrics.densityDpi
        }
    }

    private fun emitLinkData(link: String? = null, data: String? = null, error: String? = null) {
        linkData.postValue(LinkData(link, data, error))
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

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun getVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(pInfo)
        } catch (e: Exception) {
            logError(e)
            -1L
        }
    }

    private fun fromStore(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName == "com.android.vending"
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName) == "com.android.vending"
            }
        } catch (e: Exception) {
            logError(e)
            false
        }
    }

    private fun onSearched(prefs: SharedPreferences, versionCode: Long, count: Int) {
        prefs.edit().putLong(VERSION_KEY, versionCode).putInt(COUNT_KEY, count).apply()
    }
}
