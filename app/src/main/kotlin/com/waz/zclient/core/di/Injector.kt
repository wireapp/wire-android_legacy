package com.waz.zclient.core.di

import android.content.Context
import com.waz.zclient.BuildConfig
import com.waz.zclient.core.config.configModule
import com.waz.zclient.devices.di.clientsModule
import com.waz.zclient.settings.about.di.settingsAboutModule
import com.waz.zclient.settings.account.di.settingsAccountModule
import com.waz.zclient.settings.devices.di.settingsDeviceModule
import com.waz.zclient.settings.support.di.settingsSupportModule
import com.waz.zclient.storage.di.storageModule
import com.waz.zclient.user.di.usersModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
object Injector {

    @JvmStatic
    fun start(context: Context) {
        startKoin {
            androidContext(context)
            if (BuildConfig.KOTLIN_CORE) {
                modules(listOf(productionModules(), developmentModules()).flatten())
            } else {
                modules(productionModules())
            }
        }
    }

    private fun developmentModules() =
        listOf(
            settingsAccountModule,
            settingsDeviceModule,
            clientsModule
        )

    private fun productionModules() =
        listOf(
            settingsAboutModule,
            settingsSupportModule,
            usersModule,
            storageModule,
            networkModule,
            configModule
        )
}
