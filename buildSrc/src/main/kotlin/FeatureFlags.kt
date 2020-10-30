// Kotlin Feature Flags
object FeatureFlags {
    const val REGITRATION = false
    const val SETTINGS = false
    val CORE = REGITRATION || SETTINGS
}