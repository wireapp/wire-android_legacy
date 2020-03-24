package com.waz.zclient.assets.di

import com.waz.zclient.assets.AssetsApi
import com.waz.zclient.assets.AssetsRepository
import com.waz.zclient.assets.datasources.AssetsDataSource
import com.waz.zclient.assets.datasources.AssetsRemoteDataSource
import com.waz.zclient.assets.mapper.AssetMapper
import com.waz.zclient.assets.usecase.GetPublicAssetUseCase
import com.waz.zclient.core.network.NetworkClient
import org.koin.core.module.Module
import org.koin.dsl.module

val assetsModule: Module = module {
    factory { AssetMapper() }
    single { AssetsDataSource(get(), get()) as AssetsRepository }
    factory { AssetsRemoteDataSource(get(), get()) }
    factory { get<NetworkClient>().create(AssetsApi::class.java) }
    factory { GetPublicAssetUseCase(get()) }
}
