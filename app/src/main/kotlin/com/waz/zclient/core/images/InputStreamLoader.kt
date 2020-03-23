package com.waz.zclient.core.images

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.usecase.UseCase
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinComponent
import java.io.InputStream

class InputStreamLoader<T>(
    private val keyFunction: (T, Int, Int, Options) -> Key,
    private val useCase: UseCase<InputStream, T>
) : ModelLoader<T, InputStream>, KoinComponent {

    override fun buildLoadData(
        model: T,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? =
        ModelLoader.LoadData(
            keyFunction(model, width, height, options),
            InputStreamFetcher(model, useCase)
        )

    override fun handles(model: T): Boolean = true
}

class InputStreamFetcher<T>(
    private val item: T,
    private val useCase: UseCase<InputStream, T>
) : DataFetcher<InputStream> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        runBlocking {
            useCase(this, item) {
                it.fold({
                    callback.onLoadFailed(InputStreamFetchException(it))
                }) {
                    callback.onDataReady(it)
                }
            }
        }
    }

    override fun cancel() {}

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun cleanup() {}

    override fun getDataSource(): DataSource = DataSource.REMOTE

    data class InputStreamFetchException(private val failure: Failure) : Exception(failure.toString())
}
