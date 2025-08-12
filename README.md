# URLynk Android SDK

A lightweight Android library for URL shortening and deep links - open the app directly if it's installed, otherwise redirect to the Play Store for download and preserve the link through installation.

---

## Setup Instructions

1. Visit [URLynk](https://app.urlynk.in)
2. Create a free account
3. Register your app and receive your **App ID**
4. Generate your **API Key**

---

## Features

- URL shortener with smart routing
- Capture both initial and subsequent deep links
- Direct new users to download the app while preserving links across installs
- Use your own domain to create branded links

---

## Installation (via Maven Central)

```kotlin
dependencies {
    // Add this dependency
    implementation("com.valueoutput:urlynk:1.2.0")
}
```

---

## AndroidManifest Setup

Add the following `intent-filter` inside your **.MainActivity** `<activity>` tag.

```xml
<activity
    android:name=".MainActivity"
    android:launchMode="singleTop"
    android:exported="true">

    <!-- Add this intent-filter -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="http" android:host="urlynk.in" android:pathPrefix="/<YOUR_APP_ID>/" />
        <data android:scheme="https" android:host="urlynk.in" android:pathPrefix="/<YOUR_APP_ID>/" />

        <!-- Optional: Branded domain support -->
        <!-- <data android:scheme="https" android:host="your.domain.com" android:pathPrefix="/<YOUR_APP_ID>/" /> -->
    </intent-filter>
</activity>
```

> **Note:** `YOUR_APP_ID` refers to the App ID generated in your URLynk account and **not** the Android `applicationId`.

---

## Usage

### 1. Initialize the SDK

```kotlin
// Call early (before configure) to capture any incoming link
URLynk.handleDeepLink(intent)

// Then configure with your credentials
URLynk.configure(this, appId = "YOUR_APP_ID", apiKey = "YOUR_API_KEY")
```

### 2. Observe link data

```kotlin
URLynk.onLinkData.observe(this) { data ->
    Log.d("[URLynk]", "Received deep link: \"${data.first}\" with data: \"${data.second}\"")
}
```

### 3. Create a deep link

```kotlin
URLynk.createDeepLink(json.toString()) { res ->
    res.onSuccess { link -> Log.d("[URLynk]", "Generated Deep Link: $link") }
    res.onFailure { e -> Log.e("[URLynk]", e.message ?: "Deep Link error") }
}
```

### 4. Create a short link

```kotlin
URLynk.createShortLink(LinkModel(
    url = "https://www.google.com/search?q=urlynk&sca_esv=609c72437aa85e53&sxsrf=AE3TifPffGhN1WGe74VkK0U1vDQRC9ff9A%3A1754933677002",
)) { res ->
    res.onSuccess { link -> Log.d("MainActivity", "Generated Short Link: $link") }
    res.onFailure { e -> Log.e("MainActivity", e.message ?: "Short Link error") }
}
```

> **Note:** Check out a complete Jetpack Compose example [here](./app/src/main/java/com/valueoutput/urlynk_demo/MainActivity.kt).

---

## Emulator Testing

To test deep links on the emulator:

```bash
adb shell am start -a android.intent.action.VIEW -d "GENERATED_DEEP_LINK" YOUR_APPLICATION_ID_[NOT_APP_ID]
```

You should see the app launch and `URLynk.onLinkData` receive the payload.
