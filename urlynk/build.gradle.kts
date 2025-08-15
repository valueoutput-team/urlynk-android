plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("signing")
}

group = "com.valueoutput"
version = "1.2.2"

android {
    namespace = "com.valueoutput.urlynk"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = group.toString()
            artifactId = "urlynk"
            version = version

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("URLynk")
                description.set(
                    "URL shortening and deep links - open the app directly if it's installed, otherwise redirect to the Play Store for download and preserve the link through installation."
                )
                url.set("https://github.com/valueoutput-team/urlynk-android")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("urlynk")
                        name.set("URLynk")
                        email.set("urlynk@valueoutput.com")
                    }
                }
                scm {
                    url.set("https://github.com/valueoutput-team/urlynk-android")
                    connection.set("scm:git:git://github.com/valueoutput-team/urlynk-android.git")
                    developerConnection.set("scm:git:ssh://github.com/valueoutput-team/urlynk-android.git")
                }
            }
        }
    }

    repositories {
        maven {
            name = "stagingRepo"
            url = uri(layout.buildDirectory.dir("maven-artifacts"))
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["release"])
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

