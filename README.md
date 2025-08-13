# URLynk Android SDK

A lightweight Android library for URL shortening and deep links - open the app directly if it's installed, otherwise redirect to the Play Store for download and preserve the link through installation.

## Setup Instructions

1. Visit [URLynk](https://app.urlynk.in)
2. Create a free account
3. Register your app and receive your **App ID**
4. Generate your **API Key**

## Features

- URL shortener with smart routing
- Capture both initial and subsequent deep links
- Direct new users to download the app while preserving the link through installation
- Use your own domain to create branded links

## Requirements

- minSdk >= 21

> ## 🔈 Important Notice
>
> We no longer release updates to **JitPack** (older versions remain available).
>
> If you are upgrading from a version **prior to v1.2.0**, please make the following changes:
>
> 1. **Remove** the JitPack repository from your Gradle configuration (if it was only added for URLynk):
>
>    ```kotlin
>    // Remove this:
>    maven(url = "https://jitpack.io")
>    ```
>
> 2. **Update** your dependency declaration:
>
>    ```kotlin
>    // Old [Remove this]:
>    implementation("com.github.valueoutput-team:urlynk-android:1.0.2")
>
>    // New [Add this]:
>    implementation("com.valueoutput:urlynk:1.2.0")
>    ```
>
> **Maven Central coordinates:**  
> [![Maven Central](https://img.shields.io/maven-central/v/com.valueoutput/urlynk)](https://central.sonatype.com/artifact/com.valueoutput/urlynk)

## Installation

```kotlin
dependencies {
    // Add this dependency
    implementation("com.valueoutput:urlynk:1.2.0")
}
```

## AndroidManifest Setup

Add Internet permission

```xml
<uses-permission android:name="android.permission.INTERNET"/>
```

Add the following `intent-filter` inside **.MainActivity** `<activity>` tag.

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
URLynk.createDeepLink("any data in string format") { res ->
    res.onSuccess { link -> Log.d("[URLynk]", "Generated Deep Link: $link") }
    res.onFailure { e -> Log.e("[URLynk]", e.message ?: "Deep Link error") }
}
```

### 4. Create a short link

```kotlin
URLynk.createShortLink(LinkModel(
    url = "https://www.google.com/search?q=urlynk&sca_esv=609c72437aa85e53&sxsrf=AE3TifPffGhN1WGe74VkK0U1vDQRC9ff9A%3A1754933677002",
)) { res ->
    res.onSuccess { link -> Log.d("[URLynk]", "Generated Short Link: $link") }
    res.onFailure { e -> Log.e("[URLynk]", e.message ?: "Short Link error") }
}
```

> **Note:** Check out a complete example [here](./app/src/main/java/com/valueoutput/urlynk_demo/MainActivity.kt).

## Testing Deep Link

1. **Run** the app on an emulator or device.
2. **Generate** a deep link from the app.
3. **Execute** the following command in your terminal **while the app is running**:

```bash
adb shell am start -a android.intent.action.VIEW -d "GENERATED_DEEP_LINK" YOUR_APPLICATION_ID_[NOT_APP_ID]
```

If successful:

- The app should launch (if it was in the background).
- **URLynk.onLinkData** should receive the payload within a few seconds (depending on network stability).

## Testing Deferred Deep Link

1. **Show** a toast or display data on the UI when **URLynk.onLinkData** receives the data.
2. **Install** the app on a **real** Android device (not reliable on emulators).
3. **Generate** a deep link from the app.
4. **Unistall** the app.
5. **Open** the generated link in a browser of the same or a different Android device.
6. **Ignore** the redirect and manually install the app on the device where the link was opened (Step 5).
   > This serves as the development substitute for a Play Store installation.
7. **Open** the app. **URLynk.onLinkData** should receive the payload within a few seconds (depending on network stability), and display it according to your chosen method (toast or UI output).

> **NOTE:** Deep links will not open the app directly during development, even if it’s installed. An app version must be live on the Play Store for direct opening to work.

> ## Important
>
> After publishing your app to the Play Store, add the release SHA-256 certificate fingerprint from the Play Console to your URLynk app settings.
> This is essential for enabling direct app openings via deep links.
