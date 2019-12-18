package com.waz.zclient.di

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin


object KoinStarter {

    fun start(context: Context) {
        startKoin {
            androidContext(context)

            modules(listOf(
                viewModelModule,
                useCaseModule,
                repositoryModule,
                mapperModule,
                dataSourceModule,
                networkModule,
                cacheModule
            ))
        }
    }
}
