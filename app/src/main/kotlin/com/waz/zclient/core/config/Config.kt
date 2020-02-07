package com.waz.zclient.core.config

import com.waz.zclient.BuildConfig
import org.koin.core.module.Module
import org.koin.dsl.module

object Config {
    fun applicationId() = BuildConfig.APPLICATION_ID
    fun developerSettingsEnabled() = BuildConfig.DEVELOPER_FEATURES_ENABLED
    fun appLockForced() = BuildConfig.FORCE_APP_LOCK
    fun hideScreenContentForced() = BuildConfig.FORCE_HIDE_SCREEN_CONTENT
    fun versionName() = BuildConfig.VERSION_NAME
    fun versionCode() = BuildConfig.VERSION_CODE
    fun websiteUrl() = BuildConfig.WEBSITE_URL
    fun accountsUrl() = BuildConfig.ACCOUNTS_URL
    fun allowSso() = BuildConfig.ALLOW_SSO
}

val configModule: Module = module {
    factory { HostUrlConfig(Config.websiteUrl()) }
    factory { AppDetailsConfig("${Config.versionCode()} ${Config.versionName()}") }
    factory { AccountUrlConfig(Config.accountsUrl()) }
}

data class AppDetailsConfig(val versionDetails: String) : ConfigItem()
data class AccountUrlConfig(val url: String) : ConfigItem()
data class HostUrlConfig(val url: String) : ConfigItem()
sealed class ConfigItem
