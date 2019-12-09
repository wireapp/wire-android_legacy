package com.waz.zclient.core.config

import com.waz.zclient.BuildConfig


object Config {
    fun isDeveloperSettingsEnabled(): Boolean = BuildConfig.DEVELOPER_FEATURES_ENABLED
    fun isAppLockForced(): Boolean = BuildConfig.FORCE_APP_LOCK
    fun isHideScreenContentForced(): Boolean = BuildConfig.FORCE_HIDE_SCREEN_CONTENT
    fun versionName(): String = BuildConfig.VERSION_NAME
    fun websiteUrl(): String = BuildConfig.WEBSITE_URL
    fun accountsUrl(): String = BuildConfig.ACCOUNTS_URL
}
