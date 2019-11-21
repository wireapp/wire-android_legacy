package com.waz.zclient.utilities.config

import com.waz.zclient.BuildConfig

class ConfigHelper {

    fun isDeveloperSettingsEnabled(): Boolean = BuildConfig.DEVELOPER_FEATURES_ENABLED
}
