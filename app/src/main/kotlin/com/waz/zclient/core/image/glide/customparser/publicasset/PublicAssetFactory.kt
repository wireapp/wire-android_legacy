package com.waz.zclient.core.image.glide.customparser.publicasset

import com.bumptech.glide.load.Key
import com.bumptech.glide.load.Options
import com.waz.zclient.assets.usecase.GetPublicAssetUseCase
import com.waz.zclient.assets.usecase.PublicAsset
import com.waz.zclient.core.image.glide.customparser.AssetKey
import com.waz.zclient.core.image.glide.customparser.GlideStreamParserFactory
import com.waz.zclient.core.usecase.UseCase
import org.koin.core.KoinComponent
import org.koin.core.get
import java.io.InputStream

class PublicAssetFactory : GlideStreamParserFactory<PublicAsset>(), KoinComponent {
    override fun useCase(): UseCase<InputStream, PublicAsset> = get<GetPublicAssetUseCase>()

    override fun key(model: PublicAsset, width: Int, height: Int, options: Options): Key =
        AssetKey(model.assetId, width, height, options)
}
