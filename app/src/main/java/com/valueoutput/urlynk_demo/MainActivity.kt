package com.valueoutput.urlynk_demo

import android.util.Log
import android.os.Bundle
import org.json.JSONObject
import androidx.compose.ui.*
import android.content.Intent
import com.valueoutput.urlynk.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import com.valueoutput.urlynk_demo.ui.theme.URLynkDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Handle initial deep link [Call it before configure()]
        URLynk.handleDeepLink(intent)

        // 2. Configure URLynk service
        URLynk.configure(this, "YOUR_API_KEY")

        // 3. Listen for link data
        URLynk.onLinkData.observe(this) { res ->
            if(res.error != null) {
                Log.e("[URLynk]", "Error: ${res.error}")
            } else {
                Log.d("[URLynk]", "Received Data: [${res.link}] ${res.data}")
            }
        }

        enableEdgeToEdge()
        setContent {
            URLynkDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        // 4. Handle incoming deep link
        URLynk.handleDeepLink(intent)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                val json = JSONObject().apply {
                put("referredBy", "12345")
                put("referralCode", "WELCOME50")
                }

                // 5. Create a deep link
                URLynk.createDeepLink(json.toString()) { res ->
                    res.onSuccess { link -> Log.d("[URLynk]", "Generated Deep Link: $link") }
                    res.onFailure { e -> Log.e("[URLynk]", e.message ?: "Deep Link error") }
                }
            }
        ) {
            Text("Create Deep Link")
        }

        Spacer(modifier = Modifier.height(16.dp)) // Space between buttons

        Button(
            onClick = {
                // 6. Create a short link
                URLynk.createShortLink(LinkModel(
                    url = "https://www.google.com/search?q=urlynk&sca_esv=609c72437aa85e53&sxsrf=AE3TifPffGhN1WGe74VkK0U1vDQRC9ff9A%3A1754933677002&source=hp&ei=rCmaaJv2Ov2TseMP-JHguQg&iflsig=AOw8s4IAAAAAaJo3vcAU22l8xFjhNiWuWZvAR9S7ZoqY&ved=0ahUKEwib_K2zpYOPAxX9SWwGHfgIOIcQ4dUDCBo&uact=5&oq=urlynk&gs_lp=Egdnd3Mtd2l6IgZ1cmx5bmsyCBAAGIAEGKIEMggQABiABBiiBDIIEAAYgAQYogRI5Q9QsARYsAxwAXgAkAEAmAGfAaAB4QaqAQMwLja4AQPIAQD4AQGYAgegAoQHqAIKwgIHECMYJxjqAsICChAjGIAEGCcYigXCAgUQABiABMICCxAAGIAEGLEDGIMBwgIFEC4YgATCAggQABiABBixA8ICCBAuGIAEGLEDwgINEAAYgAQYsQMYRhj7AcICBxAuGIAEGArCAg0QABiABBixAxiDARgKwgIKEAAYgAQYsQMYCsICBxAAGIAEGArCAhMQLhiABBixAxjRAxiDARjHARgKwgIEEAAYHpgDC_EFkpjjVWzpaBOSBwMxLjagB9gpsgcDMC42uAf5BsIHBzAuNC4yLjHIBxw&sclient=gws-wiz",
//                    id = "Lowx",
//                    domain = "urlynk.in",
//                    password = "Test@123",
//                    startTime = System.currentTimeMillis() + 60 * 60 * 1000,
//                    webhookURL = "https://your.domain.com/webhook",
//                    expiry = listOf(
//                        ExpiryModel(type = ExpiryType.CLICK_BASED, value = 100),
//                        ExpiryModel(type = ExpiryType.TIME_BASED, value = System.currentTimeMillis() + 25 * 60 * 60 * 1000)
//                    ),
//                    restrictions = RestrictionModel(
//                        clicksPerDevice = 5,
//                        workingHrs = listOf(WorkingHrModel(0, 12), WorkingHrModel(12, 23)),
//                        os = listOf(OSType.ANDROID, OSType.IOS, OSType.MACOS),
//                        devices = listOf(DeviceType.MOBILE, DeviceType.DESKTOP),
//                        inclLoc = listOf(LocModel("India", listOf(6.5531169, 35.6745457, 67.9544415, 97.395561))),
//                        exclLoc = listOf(LocModel("India", listOf(6.5531169, 35.6745457, 67.9544415, 97.395561))),
//                    ),
//                    smartRouting = SmartRoutingModel(
//                        osBased = listOf(RoutingModel(url="https://os.com", targets = listOf(OSType.ANDROID, OSType.IOS))),
//                        deviceBased = listOf(RoutingModel(url="https://device.com", targets = listOf(DeviceType.MOBILE))),
//                        locBased = listOf(RoutingModel(url="https://loc.com", targets = listOf(LocModel("India", listOf(6.5531169, 35.6745457, 67.9544415, 97.395561))))),
//                        timeBased = listOf(RoutingModel(url="https://time.com", targets = listOf(WorkingHrModel(0, 12)))),
//                    )
                )) { res ->
                    res.onSuccess { link -> Log.d("[URLynk]", "Generated Short Link: $link") }
                    res.onFailure { e -> Log.e("[URLynk]", e.message ?: "Short Link error") }
                }
            }
        ) {
            Text("Create Short Link")
        }
    }
}