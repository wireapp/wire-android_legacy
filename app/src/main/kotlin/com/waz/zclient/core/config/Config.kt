package com.waz.zclient.core.config
import com.waz.zclient.BuildConfig

object Config {
    fun applicationId() = BuildConfig.APPLICATION_ID
    fun developerSettingsEnabled() = BuildConfig.DEVELOPER_FEATURES_ENABLED
    fun appLockForced() = BuildConfig.FORCE_APP_LOCK
    fun hideScreenContentForced() = BuildConfig.FORCE_HIDE_SCREEN_CONTENT
    fun versionName() = BuildConfig.VERSION_NAME
    fun websiteUrl() = BuildConfig.WEBSITE_URL
    fun accountsUrl() = BuildConfig.ACCOUNTS_URL
    fun allowSso() = BuildConfig.ALLOW_SSO
}
