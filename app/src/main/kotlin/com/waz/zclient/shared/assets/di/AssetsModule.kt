package com.waz.zclient.shared.assets.di

import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.core.ui.backgroundasset.BackgroundAssetViewModel
import com.waz.zclient.shared.assets.AssetsApi
import com.waz.zclient.shared.assets.AssetsRepository
import com.waz.zclient.shared.assets.datasources.AssetsDataSource
import com.waz.zclient.shared.assets.datasources.AssetsRemoteDataSource
import com.waz.zclient.shared.assets.mapper.AssetMapper
import com.waz.zclient.shared.assets.usecase.GetPublicAssetUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

@ExperimentalCoroutinesApi
val assetsModule: Module = module {
    factory { AssetMapper() }
    single { AssetsDataSource(get(), get()) as AssetsRepository }
    factory { AssetsRemoteDataSource(get(), get()) }
    factory { get<NetworkClient>().create(AssetsApi::class.java) }
    factory { GetPublicAssetUseCase(get()) }
    viewModel { BackgroundAssetViewModel(get()) }
}
