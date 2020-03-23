package com.waz.zclient.core.di

import android.content.Context
import com.waz.zclient.accounts.di.accountsModule
import com.waz.zclient.assets.di.assetsModule
import com.waz.zclient.clients.di.clientsModule
import com.waz.zclient.core.config.configModule
import com.waz.zclient.core.network.di.networkModule
import com.waz.zclient.features.auth.registration.di.registrationModules
import com.waz.zclient.features.settings.di.settingsModules
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
        accountsModule,
        configModule,
        assetsModule
    )

    @JvmStatic
    fun start(context: Context) {
        startKoin {
            androidContext(context)
            modules(listOf(
                registrationModules,
                settingsModules,
                coreModules
            ).flatten())
        }
    }
}
