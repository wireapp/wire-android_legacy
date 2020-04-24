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
    fun passwordMinimumLength() = BuildConfig.NEW_PASSWORD_MINIMUM_LENGTH
    fun passwordMaximumLength() = BuildConfig.NEW_PASSWORD_MAXIMUM_LENGTH
}

val configModule: Module = module {
    factory { HostUrlConfig(Config.websiteUrl()) }
    factory { AppVersionNameConfig(Config.versionName()) }
    factory { AppDetailsConfig("${Config.versionCode()} ${Config.versionName()}") }
    factory { AccountUrlConfig(Config.accountsUrl()) }
    factory { DeveloperOptionsConfig(Config.developerSettingsEnabled()) }
    factory { PasswordLengthConfig(Config.passwordMinimumLength(), Config.passwordMaximumLength()) }
}

data class DeveloperOptionsConfig(val isDeveloperSettingsEnabled: Boolean) : ConfigItem()
data class AppVersionNameConfig(val versionName: String) : ConfigItem()
data class AppDetailsConfig(val versionDetails: String) : ConfigItem()
data class AccountUrlConfig(val url: String) : ConfigItem()
data class HostUrlConfig(val url: String) : ConfigItem()
data class PasswordLengthConfig(val minLength: Int, val maxLength: Int) : ConfigItem()
sealed class ConfigItem
