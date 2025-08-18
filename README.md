# URLynk Android SDK

A lightweight Android library for URL shortening and deep links - open the app directly if it's installed, otherwise redirect to the Play Store for download and preserve the link through installation.

## Setup Instructions

1. Visit [URLynk](https://app.urlynk.in)
2. Create a free account
3. Register your app and receive unique **App ID**
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
>    implementation("com.valueoutput:urlynk:1.2.2")
>    ```
>
> **Maven Central coordinates:**  
> [![Maven Central](https://img.shields.io/maven-central/v/com.valueoutput/urlynk)](https://central.sonatype.com/artifact/com.valueoutput/urlynk)

## Installation

```kotlin
dependencies {
    // Add this dependency
    implementation("com.valueoutput:urlynk:1.2.2")
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
URLynk.configure(this, apiKey = "YOUR_API_KEY")
```

### 2. Observe link data

```kotlin
URLynk.onLinkData.observe(this) { res ->
    if(res.error != null) {
        Log.e("[URLynk]", "Error: ${res.error}")
    } else {
        Log.d("[URLynk]", "Received Data: [${res.link}] ${res.data}")
    }
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

> **Note:** Short links are for URL shortening only. They do not open the app, redirect to app stores, or capture clicks on app launch like deep links do.

```kotlin
URLynk.createShortLink(LinkModel(
    url = "https://www.google.com/search?q=urlynk&sca_esv=609c72437aa85e53",
)) { res ->
    res.onSuccess { link -> Log.d("[URLynk]", "Generated Short Link: $link") }
    res.onFailure { e -> Log.e("[URLynk]", e.message ?: "Short Link error") }
}
```

> **Note:** Check out a complete example [here](https://github.com/valueoutput-team/urlynk-android/blob/main/app/src/main/java/com/valueoutput/urlynk_demo/MainActivity.kt).

## Testing

### Deep Link Testing

1. **Run** the app on an emulator or physical device.
2. **Generate** a deep link from the app.
3. **Execute** the following command in your terminal while the app is running:

```bash
adb shell am start -a android.intent.action.VIEW -d "GENERATED_DEEP_LINK" YOUR_APPLICATION_ID_[NOT_APP_ID]
```

If successful:

- The app should launch (if it was in the background).
- `onLinkData` should receive the payload within a few seconds (depending on network stability).

### Deferred Link Testing

1. **Run** the app on a physical device (not reliable on emulator).
2. **Generate** a deep link from the app.
3. **Open** the generated link in the device’s browser while the app is running.
4. **Ignore** the redirect - in production, app will launch if installed, else users will be redirected to the Play Store; in development, this won’t work.
5. **Restart** the app — `onLinkData` should receive the payload within a few seconds (depending on network stability).

> **NOTE:** For direct app opening from browser, the app must be published on the Play Store or installed via a Play Store testing track.

### Play Store Testing

1. **Publish** a test build to the Play Console testing track like Internal Testing.
2. **Add** App Signing key certificate (SHA-256 fingerprint) in the URLynk app settings. <br>
   `Path: Play Console → Test and release → App Integrity → Play app signing → Settings`
3. **Wait** at least 5 minutes to allow the Digital Asset Links JSON to propagate.
4. **Verify** deep link setup in Play Console: `Grow users → Deep links` <br>
   You should see the status **All links working** and your `intent-filter` data listed under App configuration web links.
5. **Share and accept** the tester opt-in link on the device/account you plan to test with.
6. **Uninstall** any existing build of the app from this device.
7. **Open** a generated deep link in the browser - it should redirect to your Play Store listing for the test build.
8. **Install** the test build from the Play Store.
9. **Open** the app after installation - `onLinkData` should receive the payload attached to the original link within a few seconds (depending on network stability).
10. **Copy** your deep link into a text editor on your device, then tap it directly. This should open the app directly.

> **NOTE:** When testing from the Play Store’s testing track, deep links may sometimes redirect to the Play Store page instead of opening the installed app. <br>
> Use text editor method (step 10) for development. <br>
> _(Rest assured, once your app is live, links will also open directly from the browser.)_

## Important

After publishing your app to the Play Store, add the **App Signing key certificate (SHA-256 fingerprint)** from Play Console to your URLynk app settings.
This is essential for enabling direct app openings via deep links.
