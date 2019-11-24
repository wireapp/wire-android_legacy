package com.waz.zclient.utilities.config

import com.waz.zclient.BuildConfig


class ConfigHelper {
    fun isDeveloperSettingsEnabled(): Boolean = BuildConfig.DEVELOPER_FEATURES_ENABLED
    fun isAppLockForced(): Boolean = BuildConfig.FORCE_APP_LOCK
    fun isHideScreenContentForced(): Boolean = BuildConfig.FORCE_HIDE_SCREEN_CONTENT
    fun getVersionName(): String = BuildConfig.VERSION_NAME
}
