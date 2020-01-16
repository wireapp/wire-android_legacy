package com.waz.zclient.di

import android.content.Context
import com.waz.zclient.core.di.networkModule
import com.waz.zclient.devices.di.clientsModule
import com.waz.zclient.settings.account.di.settingsAccountModule
import com.waz.zclient.settings.devices.di.settingsDeviceModule
import com.waz.zclient.storage.di.storageModule
import com.waz.zclient.user.di.usersModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin


object Injector {

    @JvmStatic
    fun start(context: Context) {
        startKoin {
            androidContext(context)

            modules(listOf(
                settingsAccountModule,
                settingsDeviceModule,
                usersModule,
                clientsModule,
                storageModule,
                networkModule
            ))
        }
    }
}
