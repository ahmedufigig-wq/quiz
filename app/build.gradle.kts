plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.friends.quiz"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.friends.quiz"
        // 21 = Android 5.0 وما فوق (يغطي تقريباً كل الهواتف المستخدمة)
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // توقيع تجريبي حتى يمكن تثبيت نسخة release مباشرة بدون مفتاح خاص
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

// لا توجد أي مكتبات خارجية عمداً: التطبيق يعتمد على أندرويد الأساسي فقط
// حتى يعمل على كل الإصدارات ويبقى حجمه صغيراً (أقل من 1 ميجا تقريباً).
