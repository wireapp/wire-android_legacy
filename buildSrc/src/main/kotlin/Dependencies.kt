@file:Suppress("MemberVisibilityCanBePrivate", "SpellCheckingInspection")

object Versions {
    //wire android client
    const val ANDROID_CLIENT_MAJOR_VERSION = "3.45."

    //core
    const val KOTLIN = "1.3.60"
    const val WIRE_TRANSLATIONS = "1.+"
    val AVS = System.getenv("AVS_VERSION") ?: "5.3.191@aar"
    val WIRE_AUDIO = System.getenv("AUDIO_VERSION") ?: "1.209.0@aar"

    //plugins
    const val DETEKT = "1.2.2"

    //build
    const val COROUTINES = "1.3.2"
    const val WORK_MANAGER = "2.0.1"
    const val ANDROIDX_MATERIAL = "1.0.0"
    const val ANDROIDX_MULTIDEX = "2.0.0"
    const val ANDROIDX_APP_COMPAT = "1.0.0"
    const val ANDROIDX_RECYCLER_VIEW = "1.0.0"
    const val ANDROIDX_PREFERENCE = "1.1.0"
    const val ANDROIDX_CARD_VIEW = "1.0.0"
    const val ANDROIDX_GRID_LAYOUT = "1.0.0"
    const val ANDROIDX_CONSTRAINT_LAYOUT = "1.1.3"
    const val ANDROIDX_PAGING_RUNTIME = "2.0.0"
    const val ANDROIDX_EXIF_INTERFACE = "1.0.0"
    const val ANDROIDX_MEDIA = "1.0.0"
    const val ANDROIDX_LIFECYCLE = "2.2.0-rc03"
    const val ANDROIDX_LIFECYCLE_EXTENSIONS = "2.1.0"
    const val ANDROIDX_CORE_KTX = "1.1.0"
    const val ANDROIDX_ROOM = "2.2.2"
    const val ANDROIDX_BIOMETRIC = "1.0.1"
    const val ANDROIDX_ANNOTATION = "1.0.0"
    const val PLAY_SERVICES = "17.0.0"
    const val FIREBASE_MESSAGING = "19.0.0"
    const val GLIDE = "4.10.0"
    const val RETROFIT = "2.6.2"
    const val OKHTTP = "3.12.0"
    const val KOIN = "2.0.1"
    const val RX_KOTLIN = "2.3.0"
    const val RX_ANDROID = "2.1.1"
    const val ANDROID_JOB = "1.2.6"
    const val THREE_TEN_BP_ANDROID = "1.1.0"
    const val THREE_TEN_BP_JAVA = "1.3.8"
    const val REBOUND = "0.3.8"
    const val COMMON_MARK = "0.11.0"
    const val JNA = "4.4.0@aar"
    const val LIB_PHONE_NUMBER = "7.1.1" // 7.2.x breaks protobuf

    //testing
    const val JUNIT = "4.12"
    const val MOCKITO = "3.1.0"
    const val KLUENT = "1.14"
    const val ANDROIDX_TEST_CORE = "2.1.0"
    const val ANDROIDX_TEST_JUNIT = "1.1.1"
    const val ROBOLECTRIC = "5.0.0_r2-robolectric-1"

    //dev
    const val STETHO = "1.5.0"
}

object BuildDependencies {
    val kotlin = KotlinDependencyMap(mapOf(
        "standardLibrary" to "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Versions.KOTLIN}",
        "coroutinesCore" to "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES}",
        "coroutinesAndroid" to "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.COROUTINES}"
    ))
    val wire = WireDependencyMap(mapOf(
        "audioNotifications" to "com.wire:audio-notifications:${Versions.WIRE_AUDIO}",
        "translations" to "com.wire:wiretranslations:${Versions.WIRE_TRANSLATIONS}",
        "avs" to "com.wire:${System.getenv("AVS_NAME") ?: "avs"}:${Versions.AVS}"
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
        "annotation" to "androidx.annotation:annotation:${Versions.ANDROIDX_ANNOTATION}"
    ))
    val playServices = PlayServicesDependencyMap(mapOf(
        "base" to "com.google.android.gms:play-services-base:${Versions.PLAY_SERVICES}",
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
        "protoBufConverter" to "com.squareup.retrofit2:converter-protobuf:${Versions.RETROFIT}",
        "gsonConverter" to "com.squareup.retrofit2:converter-gson:${Versions.RETROFIT}"
    ))
    val okHttpLoggingInterceptor = "com.squareup.okhttp3:logging-interceptor:${Versions.OKHTTP}"
    val koin = KoinDependencyMap(mapOf(
        "androidCore" to "org.koin:koin-android:${Versions.KOIN}",
        "androidScope" to "org.koin:koin-android-scope:${Versions.KOIN}",
        "androidViewModel" to "org.koin:koin-android-viewmodel:${Versions.KOIN}"
    ))
    val rxJava = RxJavaDependencyMap(mapOf(
        "rxKotlin" to "io.reactivex.rxjava2:rxkotlin:${Versions.RX_KOTLIN}",
        "rxAndroid" to "io.reactivex.rxjava2:rxandroid:${Versions.RX_ANDROID}"
    ))
    val androidJob = "com.evernote:android-job:${Versions.ANDROID_JOB}"
    val threetenbpAndroid = "com.jakewharton.threetenabp:threetenabp:${Versions.THREE_TEN_BP_ANDROID}"
    val threetenbpJava = "org.threeten:threetenbp:${Versions.THREE_TEN_BP_JAVA}"
    val rebound = "com.facebook.rebound:rebound:${Versions.REBOUND}"
    val commonMark = "com.atlassian.commonmark:commonmark:${Versions.COMMON_MARK}"
    val jna = "net.java.dev.jna:jna:${Versions.JNA}"
    val libPhoneNumber = "com.googlecode.libphonenumber:libphonenumber:${Versions.LIB_PHONE_NUMBER}"
}

object ModuleDependencies {
    val storage = ":storage"
    val syncEngine = ":wire-android-sync-engine:zmessaging"
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
        "testWorkManager" to "androidx.work:work-testing:${Versions.WORK_MANAGER}"
    ))
    val okHttpMockWebServer = "com.squareup.okhttp3:mockwebserver:${Versions.OKHTTP}"
    val robolectric = "org.robolectric:android-all:${Versions.ROBOLECTRIC}"
}

object DevDependencies {
    val stetho = "com.facebook.stetho:stetho:${Versions.STETHO}"
}

object LegacyDependencies {
    const val SCALA_MAJOR_VERSION = "2.11"
    const val SCALA_VERSION = SCALA_MAJOR_VERSION.plus(".12")

    //build
    val scalaLibrary = "org.scala-lang:scala-library:$SCALA_VERSION"
    val scalaReflect = "org.scala-lang:scala-reflect:$SCALA_VERSION"
    val scalaShapeless = "com.chuusai:shapeless_$SCALA_MAJOR_VERSION:2.3.3"

    //test
    val scalaTest = "org.scalatest:scalatest_${SCALA_MAJOR_VERSION}:3.0.5"
}
