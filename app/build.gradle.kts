
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
}



kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_17
	}
}

android {
	namespace = "dev.beyman.pixeltaskbarenabler"
	compileSdk = 36

	defaultConfig {
		applicationId = "dev.beyman.pixeltaskbarenabler"
		minSdk = 35
		targetSdk = 36
		versionCode = 2
		versionName = "1.0.1"
		ndk {
			//noinspection ChromeOsAbiSupport
			abiFilters.add("arm64-v8a")
		}
	}

	val keystorePropertiesFile = rootProject.file("ReleaseKey.properties")
	var releaseSigning = signingConfigs.getByName("debug")

	try {
		val keystoreProperties = Properties()
		FileInputStream(keystorePropertiesFile).use { inputStream ->
			keystoreProperties.load(inputStream)
		}

		releaseSigning = signingConfigs.create("release") {
			keyAlias = keystoreProperties.getProperty("keyAlias")
			keyPassword = keystoreProperties.getProperty("keyPassword")
			storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
			storePassword = keystoreProperties.getProperty("storePassword")
		}
	} catch (_: Exception) {
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles("proguard-android-optimize.txt", "proguard.pro", "proguard-rules.pro")
			signingConfig = releaseSigning
		}
		debug {
			isDebuggable = true
			isMinifyEnabled = false
			isShrinkResources = false
			signingConfig = releaseSigning
		}
	}

	buildFeatures{
		viewBinding = true
		buildConfig = true
		aidl = true
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true

		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	sourceSets {
		getByName("main") {
			res.srcDirs(
				"src/main/res",
				"src/main/res-dynamicTemplates"
			)
		}
	}

	packaging {
	}
}


dependencies {
	implementation(project(":annotations"))
	annotationProcessor(project(":annotationProcessor"))
	coreLibraryDesugaring(libs.desugar.jdk.libs)

	compileOnly(files("lib/api-82.jar"))
	compileOnly(files("lib/api-82-sources.jar"))

	implementation (libs.androidx.constraintlayout)
	implementation (libs.androidx.navigation.fragment.ktx)
	implementation (libs.androidx.navigation.ui.ktx)
	implementation (libs.androidx.fragment.ktx)
	implementation (libs.androidx.appcompat)
	implementation (libs.androidx.annotation)
	implementation (libs.androidx.preference.ktx)
	implementation (libs.androidx.recyclerview)
	implementation (libs.android.material)
	implementation (libs.androidx.ui.geometry)
	//noinspection KtxExtensionAvailable
	implementation (libs.androidx.activity)
	implementation (libs.androidx.work.runtime)
	implementation (libs.androidx.concurrent.futures)
	implementation (libs.androidx.transition)

	// The core module that provides APIs to a shell
	implementation (libs.libsuCore)
	// Optional: APIs for creating root services. Depends on ":core"
	implementation (libs.libsuService)
	// Optional: Provides remote file system support
	implementation (libs.libsuNIO)

	implementation (libs.remotepreferences)
	// Remote Preferences for Xposed Module prefs
	implementation(libs.androidx.core.splashscreen)
	implementation(libs.androidx.ui)
	implementation(libs.androidx.localbroadcastmanager)

	compileOnly(libs.lsposed.api)
	implementation(libs.lsposed.service)
}
