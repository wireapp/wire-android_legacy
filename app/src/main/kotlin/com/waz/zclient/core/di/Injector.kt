package com.waz.zclient.core.di

import android.content.Context
import com.waz.zclient.core.config.configModule
import com.waz.zclient.devices.di.clientsModule
import com.waz.zclient.settings.di.settingsModules
import com.waz.zclient.storage.di.storageModule
import com.waz.zclient.user.di.usersModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
object Injector {

    private val coreModules: List<Module> = listOf(
        usersModule,
        clientsModule,
        storageModule,
        networkModule,
        configModule
    )

    @JvmStatic
    fun start(context: Context) {
        startKoin {
            androidContext(context)
            modules(listOf(
                settingsModules,
                coreModules
            ).flatten())
        }
    }
}
