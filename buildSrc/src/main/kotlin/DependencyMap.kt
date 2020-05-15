//TODO: Remove and refactor when gradle version > 5
//This is a solution to make more friendly managing dependencies
//There is a bug with the version of Kotlin DSL we are using:
//https://github.com/gradle/gradle/issues/9251
//It is not possible at the moment to upgrade gradle due to breaking the Gradle Scala plugin
//Once we are up to date, we can nest objects in the dependencies.kt file and remove all these maps.

//Build Dependencies
class KotlinDependencyMap(map: Map<String, String>) {
    val standardLibrary: String by map
    val coroutinesCore: String by map
    val coroutinesAndroid: String by map
}

class WireDependencyMap(map: Map<String, String>) {
    val audioNotifications: String by map
    val translations: String by map
    val avs: String by map
}

class AndroidXDependencyMap(map: Map<String, String>) {
    val material: String by map
    val multidex: String by map
    val appCompat: String by map
    val recyclerView: String by map
    val preference: String by map
    val cardView: String by map
    val gridLayout: String by map
    val constraintLayout: String by map
    val paging: String by map
    val exifInterface: String by map
    val media: String by map
    val lifecycleRuntime: String by map
    val lifecycleLiveData: String by map
    val lifecycleViewModel: String by map
    val lifecycleExtensions: String by map
    val coreKtx: String by map
    val roomRuntime: String by map
    val roomKtx: String by map
    val roomCompiler: String by map
    val biometric: String by map
    val workManager: String by map
    val annotation: String by map
}

class PlayServicesDependencyMap(map: Map<String, String>) {
    val base: String by map
    val maps: String by map
    val location: String by map
    val gcm: String by map
}

class GlideDependencyMap(map: Map<String, String>) {
    val core: String by map
    val compiler: String by map
}

class RetrofitDependencyMap(map: Map<String, String>) {
    val core: String by map
    val protoBufConverter: String by map
    val gsonConverter: String by map
}

class KoinDependencyMap(map: Map<String, String>) {
    val androidCore: String by map
    val androidScope: String by map
    val androidViewModel: String by map
}

//Test Dependencies
class KotlinTestDependencyMap(map: Map<String, String>) {
    val coroutinesTest: String by map
}

class MockitoDependencyMap(map: Map<String, String>) {
    val core: String by map
    val inline: String by map
}

class RxJavaDependencyMap(map: Map<String, String>) {
    val rxKotlin: String by map
    val rxAndroid: String by map
}

class AndroidXTestDependencyMap(map: Map<String, String>) {
    val testCore: String by map
    val testJunit: String by map
    val testRules: String by map
    val testWorkManager: String by map
    val testRoom: String by map
}
