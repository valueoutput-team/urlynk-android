# URLynk Android SDK

A lightweight Android library for URL shortening, and to create and handle **deferred deep links** with instant routing. If the app is installed, it opens directly. If not, the user is redirected to the Play Store and the link context is preserved after installation.

---

## 🧰 Setup Instructions

1. Visit [URLynk](https://app.urlynk.in)
2. Create a free account
3. Register your app and receive your **App ID**
4. Generate your **API Key**

---

## 🚀 Features

- Capture initial and subsequent deep links
- Handle deferred deep links across installs
- Create short or branded deep links
- Access deep link data via LiveData
- Simple integration with Jetpack Compose or XML-based apps

---

## 🛠️ Installation (via JitPack)

### Step 1: Add the JitPack repository

In `settings.gradle.kts` (or project-level `build.gradle`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

### Step 2: Add the dependency

```kotlin
dependencies {
    implementation("com.github.valueoutput-team:urlynk-android:1.1.0")
}
```

---

## 📱 AndroidManifest Setup

```xml
<activity
    android:name=".MainActivity"
    android:launchMode="singleTop"
    android:exported="true">

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

---

## 🔧 Usage

### 1. Initialize the SDK

```kotlin
// Call early (before configure) to capture any incoming link
URLynk.handleDeepLink(intent)

// Then configure with your credentials
URLynk.configure(context, appId = "YOUR_APP_ID", apiKey = "YOUR_API_KEY")
```

### 2. Observe link data

```kotlin
URLynk.onLinkData.observe(this) { data ->
    Log.d("[URLynk]", "Received link data: $data")
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

Check out a complete Jetpack Compose example in the [demo app](./app/src/main/java/com/valueoutput/urlynk_demo/MainActivity.kt).

---

## 🧪 Emulator Testing

To test deep links on the emulator:

```bash
adb shell am start -a android.intent.action.VIEW -d "<created_deep_link>" <your.application.id>
```

You should see the app launch and `URLynk.onLinkData` receive the payload.

---

## 📄 License

BSD-3-Clause License — see [`LICENSE`](./LICENSE) for details.
