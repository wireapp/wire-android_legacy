@file:Suppress("MemberVisibilityCanBePrivate", "SpellCheckingInspection")

import org.gradle.api.JavaVersion


object Versions {
    //wire android client
    const val ANDROID_CLIENT_MAJOR_VERSION = "3.77."
    const val COMPILE_SDK_VERSION = 30
    const val TARGET_SDK_VERSION = 30
    const val MIN_SDK_VERSION = 24
    const val BUILD_TOOLS_VERSION = "30.0.2"
    val SOURCE_COMPATIBILITY_VERSION = JavaVersion.VERSION_1_8
    val TARGET_COMPATIBILITY_VERSION = JavaVersion.VERSION_1_8

    //core
    const val KOTLIN = "1.3.72"

    const val WIRE_TRANSLATIONS = "1.+"

    //plugins
    const val ANDROID_GRADLE_PLUGIN = "3.2.1"
    const val SCALA_BUILD_PLUGIN = "2.0.3"
    const val GMS = "3.1.1"
    const val DETEKT = "1.2.2"
    const val JACOCO = "0.8.5"
    const val DEX_INFO = "0.1.2"
    const val GRGIT = "4.1.0"

    //build
    const val COROUTINES = "1.3.7"
    const val WORK_MANAGER = "2.0.1"
    const val ANDROIDX_MATERIAL = "1.3.0"
    const val ANDROIDX_MULTIDEX = "2.0.0"
    const val ANDROIDX_APP_COMPAT = "1.0.0"
    const val ANDROIDX_RECYCLER_VIEW = "1.0.0"
    const val ANDROIDX_PREFERENCE = "1.1.0"
    const val ANDROIDX_CARD_VIEW = "1.0.0"
    const val ANDROIDX_GRID_LAYOUT = "1.0.0"
    const val ANDROIDX_CONSTRAINT_LAYOUT = "1.1.3"
    const val ANDROIDX_PAGING_RUNTIME = "2.1.2"
    const val ANDROIDX_EXIF_INTERFACE = "1.0.0"
    const val ANDROIDX_MEDIA = "1.0.0"
    const val ANDROIDX_LIFECYCLE = "2.2.0-rc03"
    const val ANDROIDX_LIFECYCLE_EXTENSIONS = "2.1.0"
    const val ANDROIDX_CORE_KTX = "1.1.0"
    const val ANDROIDX_ROOM = "2.2.2"
    const val ANDROIDX_BIOMETRIC = "1.0.1"
    const val ANDROIDX_ANNOTATION = "1.0.0"
    const val ANDROIDX_VIEWPAGER_2 = "1.0.0"
    const val PLAY_SERVICES = "17.0.0"
    const val PLAY_SERVICES_BASE = "17.1.0"
    const val FIREBASE_MESSAGING = "20.1.0"
    const val GLIDE = "4.11.0"
    const val RETROFIT = "2.9.0"
    const val OKHTTP = "3.14.9"
    const val KOIN = "2.0.1"
    const val RX_KOTLIN = "2.3.0"
    const val RX_ANDROID = "2.1.1"
    const val KOTLINX_SERIALIZATION = "0.20.0"
    const val ANDROID_JOB = "1.2.6"
    const val THREE_TEN_BP_ANDROID = "1.1.0"
    const val THREE_TEN_BP_JAVA = "1.3.8"
    const val REBOUND = "0.3.8"
    const val COMMON_MARK = "0.11.0"
    const val JNA = "4.4.0@aar"
    const val LIB_PHONE_NUMBER = "7.1.1" // 7.2.x breaks protobuf
    const val LIB_SODIUM = "2.0.2"
    const val COUNTLY = "20.04.2"
    const val ZOOMING = "1.1.0"

    //osm
    const val OSMDROID = "6.1.11"
    const val OSMBONUSPACK = "6.8.0"

    //testing
    const val JUNIT = "4.12"
    const val MOCKITO = "3.1.0"
    const val KLUENT = "1.59"
    const val ANDROIDX_TEST_CORE = "2.1.0"
    const val ANDROIDX_TEST_JUNIT = "1.1.1"
    const val ROBOLECTRIC = "5.0.0_r2-robolectric-1"

}

object BuildDependencies {
    val kotlin = KotlinDependencyMap(mapOf(
        "standardLibrary" to "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Versions.KOTLIN}",
        "coroutinesCore" to "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES}",
        "coroutinesAndroid" to "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.COROUTINES}"
    ))
    val wire = WireDependencyMap(mapOf(
        "translations" to "com.wire:wiretranslations:${Versions.WIRE_TRANSLATIONS}"
    ))
    val androidX = AndroidXDependencyMap(mapOf(
        "material" to "com.google.android.material:material:${Versions.ANDROIDX_MATERIAL}",
        "multidex" to "androidx.multidex:multidex:${Versions.ANDROIDX_MULTIDEX}",
        "appCompat" to "androidx.appcompat:appcompat:${Versions.ANDROIDX_APP_COMPAT}",
        "recyclerView" to "androidx.recyclerview:recyclerview:${Versions.ANDROIDX_RECYCLER_VIEW}",
        "preference" to "androidx.preference:preference:${Versions.ANDROIDX_PREFERENCE}",
        "cardView" to "androidx.cardview:cardview:${Versions.ANDROIDX_CARD_VIEW}",
        "gridLayout" to "androidx.gridlayout:gridlayout:${Versions.ANDROIDX_GRID_LAYOUT}",
        "constraintLayout" to "androidx.constraintlayout:constraintlayout:${Versions.ANDROIDX_CONSTRAINT_LAYOUT}",
        "paging" to "androidx.paging:paging-runtime:${Versions.ANDROIDX_PAGING_RUNTIME}",
        "exifInterface" to "androidx.exifinterface:exifinterface:${Versions.ANDROIDX_EXIF_INTERFACE}",
        "media" to "androidx.media:media:${Versions.ANDROIDX_MEDIA}",
        "lifecycleRuntime" to "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.ANDROIDX_LIFECYCLE}",
        "lifecycleLiveData" to "androidx.lifecycle:lifecycle-livedata-ktx:${Versions.ANDROIDX_LIFECYCLE}",
        "lifecycleViewModel" to "androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.ANDROIDX_LIFECYCLE}",
        "lifecycleExtensions" to "androidx.lifecycle:lifecycle-extensions:${Versions.ANDROIDX_LIFECYCLE_EXTENSIONS}",
        "coreKtx" to "androidx.core:core-ktx:${Versions.ANDROIDX_CORE_KTX}",
        "roomRuntime" to "androidx.room:room-runtime:${Versions.ANDROIDX_ROOM}",
        "roomKtx" to "androidx.room:room-ktx:${Versions.ANDROIDX_ROOM}",
        "roomCompiler" to "androidx.room:room-compiler:${Versions.ANDROIDX_ROOM}",
        "biometric" to "androidx.biometric:biometric:${Versions.ANDROIDX_BIOMETRIC}",
        "workManager" to "androidx.work:work-runtime:${Versions.WORK_MANAGER}",
        "annotation" to "androidx.annotation:annotation:${Versions.ANDROIDX_ANNOTATION}",
        "viewPager2" to "androidx.viewpager2:viewpager2:${Versions.ANDROIDX_VIEWPAGER_2}"
    ))
    val playServices = PlayServicesDependencyMap(mapOf(
        "base" to "com.google.android.gms:play-services-base:${Versions.PLAY_SERVICES_BASE}",
        "maps" to "com.google.android.gms:play-services-maps:${Versions.PLAY_SERVICES}",
        "location" to "com.google.android.gms:play-services-location:${Versions.PLAY_SERVICES}",
        "gcm" to "com.google.android.gms:play-services-gcm:${Versions.PLAY_SERVICES}"
    ))
    val fireBaseMessaging = "com.google.firebase:firebase-messaging:${Versions.FIREBASE_MESSAGING}"
    val glide = GlideDependencyMap(mapOf(
        "core" to "com.github.bumptech.glide:glide:${Versions.GLIDE}",
        "compiler" to "com.github.bumptech.glide:compiler:${Versions.GLIDE}"
    ))
    val retrofit = RetrofitDependencyMap(mapOf(
        "core" to "com.squareup.retrofit2:retrofit:${Versions.RETROFIT}",
        "gsonConverter" to "com.squareup.retrofit2:converter-gson:${Versions.RETROFIT}"
    ))
    val okHttpLoggingInterceptor = "com.squareup.okhttp3:logging-interceptor:${Versions.OKHTTP}"
    val koin = KoinDependencyMap(mapOf(
        "androidCore" to "io.insert-koin:koin-android:${Versions.KOIN}",
        "androidScope" to "io.insert-koin:koin-android-scope:${Versions.KOIN}",
        "androidViewModel" to "io.insert-koin:koin-android-viewmodel:${Versions.KOIN}"
    ))
    val rxJava = RxJavaDependencyMap(mapOf(
        "rxKotlin" to "io.reactivex.rxjava2:rxkotlin:${Versions.RX_KOTLIN}",
        "rxAndroid" to "io.reactivex.rxjava2:rxandroid:${Versions.RX_ANDROID}"
    ))
    val kotlinXSerialization = "org.jetbrains.kotlinx:kotlinx-serialization-runtime:${Versions.KOTLINX_SERIALIZATION}"
    val androidJob = "com.evernote:android-job:${Versions.ANDROID_JOB}"
    val threetenbpAndroid = "com.jakewharton.threetenabp:threetenabp:${Versions.THREE_TEN_BP_ANDROID}"
    val threetenbpJava = "org.threeten:threetenbp:${Versions.THREE_TEN_BP_JAVA}"
    val rebound = "com.facebook.rebound:rebound:${Versions.REBOUND}"
    val commonMark = "com.atlassian.commonmark:commonmark:${Versions.COMMON_MARK}"
    val jna = "net.java.dev.jna:jna:${Versions.JNA}"
    val libPhoneNumber = "com.googlecode.libphonenumber:libphonenumber:${Versions.LIB_PHONE_NUMBER}"
    val libSodium = "com.github.joshjdevl.libsodiumjni:libsodium-jni-aar:${Versions.LIB_SODIUM}"
    val countly = "ly.count.android:sdk:${Versions.COUNTLY}"
    val zooming = "com.wire.zoom:zoomlayout:${Versions.ZOOMING}"
    val wireSignals = "com.wire:wire-signals_${LegacyDependencies.SCALA_MAJOR_VERSION}:${LegacyDependencies.WIRE_SIGNALS}"
    val wireSignalsExtensions = "com.wire:wire-signals-extensions_${LegacyDependencies.SCALA_MAJOR_VERSION}:${LegacyDependencies.WIRE_SIGNALS_EXTENSIONS}"
    val osmdroid = "org.osmdroid:osmdroid-android:${Versions.OSMDROID}"
    val osmbonuspack = "com.github.MKergall:osmbonuspack:${Versions.OSMBONUSPACK}"
}

object ModuleDependencies {
    val storage = ":storage"
    val syncEngine = ":zmessaging"
    val commonTest = ":common-test"
}

object TestDependencies {
    val jUnit = "junit:junit:${Versions.JUNIT}"
    val kluent = "org.amshove.kluent:kluent:${Versions.KLUENT}"
    val kotlin = KotlinTestDependencyMap(mapOf(
        "coroutinesTest" to "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINES}"
    ))
    val mockito = MockitoDependencyMap(mapOf(
        "core" to "org.mockito:mockito-core:${Versions.MOCKITO}",
        "inline" to "org.mockito:mockito-inline:${Versions.MOCKITO}"
    ))
    val androidX = AndroidXTestDependencyMap(mapOf(
        "testCore" to "androidx.arch.core:core-testing:${Versions.ANDROIDX_TEST_CORE}",
        "testJunit" to "androidx.test.ext:junit:${Versions.ANDROIDX_TEST_JUNIT}",
        "testRules" to "androidx.test:rules:${Versions.ANDROIDX_TEST_JUNIT}",
        "testWorkManager" to "androidx.work:work-testing:${Versions.WORK_MANAGER}",
        "testRoom" to "androidx.room:room-testing:${Versions.ANDROIDX_ROOM}"
    ))
    val okHttpMockWebServer = "com.squareup.okhttp3:mockwebserver:${Versions.OKHTTP}"
    val robolectric = "org.robolectric:android-all:${Versions.ROBOLECTRIC}"
}


object LegacyDependencies {
    const val SCALA_MAJOR_VERSION = "2.11"
    const val SCALA_VERSION = SCALA_MAJOR_VERSION.plus(".12")
    // signals
    const val WIRE_SIGNALS = "1.0.0"
    const val WIRE_SIGNALS_EXTENSIONS = "1.0.0"

    //build
    val scalaLibrary = "org.scala-lang:scala-library:$SCALA_VERSION"
    val scalaReflect = "org.scala-lang:scala-reflect:$SCALA_VERSION"
    val scalaShapeless = "com.chuusai:shapeless_$SCALA_MAJOR_VERSION:2.3.3"

    //test
    val scalaTest = "org.scalatest:scalatest_${SCALA_MAJOR_VERSION}:3.0.5"
}
