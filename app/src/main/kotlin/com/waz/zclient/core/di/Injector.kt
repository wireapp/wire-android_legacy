package com.waz.zclient.core.di

import android.content.Context
import com.waz.zclient.accounts.di.accountsModule
import com.waz.zclient.core.config.configModule
import com.waz.zclient.devices.di.clientsModule
import com.waz.zclient.settings.account.di.settingsAccountModule
import com.waz.zclient.settings.devices.di.settingsDeviceModule
import com.waz.zclient.settings.di.settingsMainModule
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
            modules(listOf(
                settingsMainModule,
                settingsAccountModule,
                settingsDeviceModule,
                usersModule,
                accountsModule,
                clientsModule,
                storageModule,
                networkModule,
                configModule
            ))
        }
    }
}
