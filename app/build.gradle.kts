plugins { id("com.android.application") }

android {
    namespace = "com.yakboz.braviahelper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yakboz.braviahelper"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
