plugins {
    id("com.android.application"); id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose"); id("com.google.devtools.ksp")
}
android {
    namespace = "com.oam.pdftools"; compileSdk = 35
    defaultConfig { applicationId = "com.oam.pdftools"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "1.0" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-ktx:2.6.1"); ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
