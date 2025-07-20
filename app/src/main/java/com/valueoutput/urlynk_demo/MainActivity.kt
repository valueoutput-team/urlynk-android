package com.valueoutput.urlynk_demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.valueoutput.urlynk.URLynk
import com.valueoutput.urlynk_demo.ui.theme.URLynkDemoTheme
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Handle initial deep link [Call it before configure()]
        URLynk.handleDeepLink(intent)

        // 2. Configure URLynk service
        URLynk.configure(this, "YOUR_APP_ID", "YOUR_API_KEY")

        // 3. Listen for link data
        URLynk.onLinkData.observe(this) { data ->
            Log.d("MainActivity", "Received deep link data: $data")
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
    Button(
        onClick = {
            val json = JSONObject().apply {
                put("referredBy", "12345")
                put("referralCode", "WELCOME50")
            }

            // 5. Create a deep link
            URLynk.createDeepLink(json.toString()) { link ->
                Log.d("[URLynk]", "Generated Link: $link")
            }
        }
    ) { Text("Create Link") }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Button(
        onClick = {
            val json = JSONObject().apply {
                put("referredBy", "12345")
                put("referralCode", "WELCOME50")
            }

            // 5. Create a deep link
            URLynk.createDeepLink(json.toString()) { link ->
                Log.d("[URLynk]", "Generated Link: $link")
            }
        }
    ) {Text("Create Link")}
}