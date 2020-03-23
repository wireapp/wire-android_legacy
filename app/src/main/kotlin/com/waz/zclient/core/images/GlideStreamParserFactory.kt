package com.waz.zclient.core.images

import com.bumptech.glide.load.Key
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.waz.zclient.core.usecase.UseCase
import java.io.InputStream

abstract class GlideStreamParserFactory<T> {
    abstract fun key(model: T, width: Int, height: Int, options: Options): Key

    abstract fun useCase(): UseCase<InputStream, T>

    fun modelLoader(): GlideInputStreamLoader<T> = GlideInputStreamLoader(::key, useCase())

    fun modelLoaderFactory(): ModelLoaderFactory<T, InputStream> = object : ModelLoaderFactory<T, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<T, InputStream> = modelLoader()
        override fun teardown() {}
    }
}
