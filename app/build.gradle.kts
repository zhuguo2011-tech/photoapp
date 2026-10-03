plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nanjing.photoapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nanjing.photoapp"
        minSdk = 24
        targetSdk = 34
        // 新版：版本号从 1 / 1.0 升到 2 / 2.0（安卓靠 versionCode 判断是不是新版本）
        versionCode = 2
        versionName = "2.0"
    }

    // 新版：固定签名文件。
    // 以前每次在GitHub编译都会随机生成一个新的调试签名，结果每个新版APK的签名都不一样，
    // 手机上装新版时会提示“签名不一致/安装失败”，只能先卸载旧版（APP里保存的服务器地址、登录状态都会丢）。
    // 现在所有版本都用 app 文件夹里这个 photoapp-debug.keystore 签名，以后更新直接覆盖安装即可。
    // （第一次从旧版升级到这个版本时，因为旧版是随机签名，还是需要先卸载一次旧版）
    signingConfigs {
        getByName("debug") {
            storeFile = file("photoapp-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // 网络请求
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 图片加载
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // PhotoView：成熟的图片缩放库，双指以两指中点缩放、双击、拖动，体验和微信/系统相册一致
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.1")
}
