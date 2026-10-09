plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

java { toolchain { languageVersion = Versions.JAVA } }

android {
    namespace = "dev.jdtech.jellyfin.player.core"
    compileSdk = Versions.COMPILE_SDK
    buildToolsVersion = Versions.BUILD_TOOLS

    defaultConfig {
        minSdk = Versions.MIN_SDK
        missingDimensionStrategy("variant", "proprietary", "libre")
    }

    buildTypes {
        named("release") { isMinifyEnabled = false }
        register("beta") { initWith(getByName("release")) }
    }
}

dependencies {
    implementation(projects.core)
    implementation(projects.data)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.timber)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.jellyfin.core)
}
