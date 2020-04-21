package com.waz.zclient.core.images.publicasset

import com.bumptech.glide.load.Key
import com.bumptech.glide.load.Options
import com.waz.zclient.core.images.AssetKey
import com.waz.zclient.core.images.InputStreamParserFactory
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.assets.usecase.GetPublicAssetUseCase
import com.waz.zclient.shared.assets.usecase.PublicAsset
import org.koin.core.KoinComponent
import org.koin.core.get
import java.io.InputStream

class PublicAssetFactory : InputStreamParserFactory<PublicAsset>(), KoinComponent {
    override fun useCase(): UseCase<InputStream, PublicAsset> = get<GetPublicAssetUseCase>()

    override fun key(model: PublicAsset, width: Int, height: Int, options: Options): Key =
        AssetKey(model.assetId, width, height, options)
}
