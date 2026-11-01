plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

// Иконка приложения создаётся прямо при сборке
fun writeRes(path: String, text: String) {
    val f = File(projectDir, "src/main/res/" + path)
    f.parentFile.mkdirs()
    f.writeText(text)
}

writeRes("mipmap-anydpi/ic_launcher.xml", """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_bg" />
    <foreground android:drawable="@drawable/ic_fg" />
</adaptive-icon>
""")

writeRes("drawable/ic_bg.xml", """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear"
                android:startX="0" android:startY="0" android:endX="108" android:endY="108">
                <item android:offset="0" android:color="#FF1F4234" />
                <item android:offset="1" android:color="#FF0B110E" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
""")

writeRes("drawable/ic_fg.xml", """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M54,54m-26,0a26,26 0 1,0 52,0a26,26 0 1,0 -52,0">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial"
                android:centerX="46" android:centerY="44" android:gradientRadius="40">
                <item android:offset="0" android:color="#FFD4EFDF" />
                <item android:offset="0.5" android:color="#FF8FC4A4" />
                <item android:offset="1" android:color="#FF3F7A5B" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:fillColor="#FF0C1A13" android:pathData="M42,49h5v10h-5z" />
    <path android:fillColor="#FF0C1A13" android:pathData="M51.5,42h5v24h-5z" />
    <path android:fillColor="#FF0C1A13" android:pathData="M61,47h5v14h-5z" />
</vector>
""")

android {
    namespace = "com.example.wave"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.wave"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Постоянная подпись: новые версии ставятся поверх старых без удаления
    if (File(projectDir, "debug.keystore").exists()) {
        signingConfigs {
            getByName("debug") {
                storeFile = File(projectDir, "debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
}
