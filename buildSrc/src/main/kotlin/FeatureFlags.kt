// Kotlin Feature Flags
object FeatureFlags {
    const val REGISTRATION = false
    const val SETTINGS = false
    val CORE = REGISTRATION || SETTINGS
}