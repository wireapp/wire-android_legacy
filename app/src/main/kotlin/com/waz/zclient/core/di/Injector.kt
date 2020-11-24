package com.waz.zclient.core.di

import android.content.Context
import com.waz.zclient.core.backend.di.backendModule
import com.waz.zclient.core.config.configModule
import com.waz.zclient.core.network.di.networkModule
import com.waz.zclient.feature.backup.di.backupModules
import com.waz.zclient.shared.assets.di.assetsModule
import com.waz.zclient.storage.di.storageModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
object Injector {

    private val coreModules: List<Module> = listOf(
        storageModule,
        networkModule,
        configModule,
        backendModule
    )

    /**
     * Shared modules should contain dependencies that can
     * build up multiple features
     */
    private val sharedModules: List<Module> = listOf(
        assetsModule
    )

    /**
     * Feature modules should contain dependencies that build up specific
     * features and don't tend to live outside of that feature
     */
    private val featureModules: List<Module> = listOf(
        backupModules
    ).flatten()

    @JvmStatic
    fun start(context: Context) {
        startKoin {
            androidContext(context)
            modules(listOf(
                coreModules,
                sharedModules,
                featureModules
            ).flatten())
        }
    }
}
