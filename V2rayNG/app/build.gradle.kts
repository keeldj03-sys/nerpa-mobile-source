// Read as `java.net.URI` in the build script: the project-level `java` accessor shadows the package.
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.jaredsburrows.license")
}

// An ABI is buildable only when both hev tunnel libraries are staged in libs/<abi>. Without them
// the APK still assembles, installs and starts, and only loses tun mode where it is switched on,
// so a hand-maintained ABI list is the trap recorded in FORK.md section 1. The list is therefore
// read from the artifacts themselves: tool/fetch-native.sh stages the ABIs pinned in libs.lock,
// compile-hevtun.sh stages the four ABIs it compiles, and CI copies that output into app/libs.
val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
val tunnelLibraries = listOf("libhev-socks5-tunnel.so", "libhevsockstun.so")
val stagedAbis = knownAbis.filter { abi ->
    val staged = tunnelLibraries.count { file("libs/$abi/$it").isFile }
    require(staged == 0 || staged == tunnelLibraries.size) {
        "libs/$abi holds only part of the tunnel libraries; run tool/fetch-native.sh or " +
            "compile-hevtun.sh again."
    }
    staged == tunnelLibraries.size
}
require(stagedAbis.isNotEmpty()) {
    "No ABI under ${file("libs")} carries both tunnel libraries; run tool/fetch-native.sh before " +
        "building, as the Native artifacts section of README.md describes."
}

// Release signing is operator-side: the keystore is not in this tree and not in the public mirror,
// and a passphrase passed with -P would survive in shell history and in .build-logs/. The only
// input is the environment of one build, set by tool/build-release.sh after an interactive prompt.
val missingSigningEnv = listOf(
    "NERPA_KEYSTORE",
    "NERPA_KEYSTORE_PASSWORD",
    "NERPA_KEY_ALIAS",
    "NERPA_KEY_PASSWORD",
).filter { System.getenv(it).isNullOrBlank() }
val keystoreFile = System.getenv("NERPA_KEYSTORE")?.let { file(it) }
val releaseSigningProblem: String? = when {
    missingSigningEnv.isNotEmpty() -> "not set: ${missingSigningEnv.joinToString(", ")}"
    keystoreFile?.isFile != true -> "NERPA_KEYSTORE is not a file: ${keystoreFile?.path ?: "unset"}"
    else -> null
}
val releaseSigningReady = releaseSigningProblem == null

// A configuration-cache entry stores the realized signing config, and storePassword is a plain
// String in it — enabling the cache would put the passphrase on disk, which is what this avoids.
if (releaseSigningReady && gradle.startParameter.isConfigurationCacheRequested) {
    error(
        "Release signing comes from the environment, so a configuration-cache entry would serialize " +
            "the keystore password to disk. Re-run with --no-configuration-cache, which " +
            "tool/build-release.sh does."
    )
}

android {
    namespace = "com.v2ray.ang"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.nerpa.mobile"
        minSdk = 24
        targetSdk = 37
        versionCode = 755
        versionName = "2.3.15"

        // The operator's free-access endpoint. Only the address is compiled in; the answer is either a
        // subscription link in the body or the node package this very address serves, in which case the
        // address becomes the group's subscription url. Empty means the build offers no button.
        val freeEndpoint = (properties["NERPA_FREE_ENDPOINT"] as? String)?.trim() ?: ""
        require(freeEndpoint.none { it == '"' || it == '\\' }) { "NERPA_FREE_ENDPOINT must not contain quotes" }
        if (freeEndpoint.isNotEmpty()) {
            val url = runCatching { URI(freeEndpoint) }.getOrNull()
            val host = url?.host.orEmpty()
            val cleartextStand = url?.scheme == "http" && (host == "127.0.0.1" || host == "localhost")
            require(host.isNotEmpty() && (url?.scheme == "https" || cleartextStand)) {
                "NERPA_FREE_ENDPOINT must be an https:// URL with a host; http:// is accepted only for " +
                    "127.0.0.1 or localhost, which is how the issuance stand reaches the phone over " +
                    "`adb reverse`. The answer this endpoint serves is a subscription link with " +
                    "credentials, so any other address would be fetched in the clear: $freeEndpoint"
            }
        }
        buildConfigField("String", "FREE_SUBSCRIPTION_ENDPOINT", "\"$freeEndpoint\"")

        val abiFilterList = (properties["ABI_FILTERS"] as? String)?.split(';')
        splits {
            abi {
                isEnable = true
                reset()
                val requested = if (abiFilterList.isNullOrEmpty()) stagedAbis else abiFilterList
                val unstaged = requested.filter { it !in stagedAbis }
                require(unstaged.isEmpty()) {
                    "ABI_FILTERS asks for $unstaged, which have no tunnel libraries staged; " +
                        "buildable ABIs: $stagedAbis"
                }
                include(*requested.toTypedArray())
                // A universal APK spanning part of the ABI set would carry tunnel libraries for
                // only some of the devices it claims to support.
                isUniversalApk = abiFilterList.isNullOrEmpty() && stagedAbis.containsAll(knownAbis)
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("nerpaRelease") {
                val keyStore = checkNotNull(keystoreFile)
                storeFile = keyStore
                storePassword = System.getenv("NERPA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NERPA_KEY_ALIAS")
                keyPassword = System.getenv("NERPA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("nerpaRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions.add("distribution")
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroid"
            buildConfigField("String", "DISTRIBUTION", "\"F-Droid\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"Play Store\"")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    applicationVariants.all {
        val variant = this
        val isFdroid = variant.productFlavors.any { it.name == "fdroid" }
        if (isFdroid) {
            val versionCodes =
                mapOf(
                    "armeabi-v7a" to 2, "arm64-v8a" to 1, "x86" to 4, "x86_64" to 3, "universal" to 0
                )

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = output.getFilter("ABI") ?: "universal"
                    output.outputFileName = "NerpaMobile_${variant.versionName}-fdroid_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (100 * variant.versionCode + versionCodes[abi]!!).plus(5000000)
                    } else {
                        return@forEach
                    }
                }
        } else {
            // One multiplier for every playstore ABI. It is 6, not upstream's 4, because codes
            // 4000755 (multiplier 4) and 5000755 (multiplier 5) are already shipped to users and
            // version codes must strictly increase; defaultConfig.versionCode must stay untouched
            // or a future upstream merge would collide with the shipped code.
            val versionCodes =
                mapOf("armeabi-v7a" to 6, "arm64-v8a" to 6, "x86" to 6, "x86_64" to 6, "universal" to 6)

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = if (output.getFilter("ABI") != null)
                        output.getFilter("ABI")
                    else
                        "universal"

                    output.outputFileName = "NerpaMobile_${variant.versionName}_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (1000000 * versionCodes[abi]!!).plus(variant.versionCode)
                    } else {
                        return@forEach
                    }
                }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf(
            "en",
            "zh-rCN",
            "zh-rTW",
            "vi",
            "ru",
            "fa",
            "ar",
            "az",
            "bn",
            "bqi-rIR"
        )
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            // Treat Jupiter discovery warnings as failures: JUnit 4 failed on an invalid @Test
            // method, such as one that returns a value, while Jupiter only warns and skips it.
            // The key is @API(status = EXPERIMENTAL) in JUnit 6; after a JUnit upgrade, check
            // that a temporary `@Test fun probe() = 1` still fails the unit test task.
            it.systemProperty("junit.platform.discovery.issue.severity.critical", "WARNING")
        }
    }

    // The fork's own strings (nerpa_*) live in en and ru only, by the decision recorded in
    // FORK.md section 5: no machine translations, no narrowing of localeFilters. Lint counts that
    // as 34 MissingTranslation errors, and a release build has to be able to run lint, so the
    // accepted findings are listed in app/lint-baseline.xml — those exact findings, and no other:
    // every other lint error still fails the build.
    lint {
        baseline = file("lint-baseline.xml")
    }

}

// AGP happily packages an unsigned release APK: it installs nowhere, yet looks exactly like a
// deliverable — the silent-degradation trap recorded for ABIs in FORK.md section 1. The check is a
// doFirst on the packaging tasks rather than a configuration-time require(), because AGP realizes
// them only when a release artifact was requested, leaving debug builds and unit tests untouched.
if (!releaseSigningReady) {
    val signingFailure = releaseSigningProblem
    tasks.configureEach {
        if (name.matches(Regex("^(package|bundle).*Release$"))) {
            doFirst {
                error(
                    "A release APK is signed with the operator's key, and this tree does not carry " +
                        "it; refusing to write an unsigned artifact. Signing environment: $signingFailure. " +
                        "Build releases through tool/build-release.sh, or assemble the debug variant."
                )
            }
        }
    }
}

dependencies {
    // Core Libraries
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // AndroidX Core Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Compose Libraries
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Data and Storage Libraries
    implementation(libs.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Reactive and Utility Libraries
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // QR Code: CameraX + ZXing
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)
    implementation(libs.core) // zxing core

    // AndroidX Lifecycle and Architecture Components
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Background Task Libraries
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.multiprocess)

    // Reorderable list
    implementation(libs.reorderable)

    // Testing Libraries
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
