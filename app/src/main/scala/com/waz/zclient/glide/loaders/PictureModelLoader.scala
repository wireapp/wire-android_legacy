/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.glide.loaders

import java.io.InputStream

import android.content.Context
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.bumptech.glide.load.model.{ModelLoader, ModelLoaderFactory, MultiModelLoaderFactory}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Picture, PictureNotUploaded, PictureUploaded}
import com.waz.service.ZMessaging
import com.wire.signals.Signal
import com.waz.zclient.core.images.AssetKey
import com.waz.zclient.glide.{AssetRequest, ImageAssetFetcher, PublicAssetIdRequest, UploadAssetIdRequest}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.{Injectable, Injector, WireContext}


final class PictureModelLoader(zms: Signal[ZMessaging]) extends ModelLoader[Picture, InputStream]
  with DerivedLogTag {

  override def buildLoadData(model: Picture, width: Int, height: Int, options: Options): ModelLoader.LoadData[InputStream] = {
    val request = createAssetRequest(model)
    val key = new AssetKey(request.toString, width, height, options)
    verbose(l"key: $key")
    new LoadData[InputStream](key, new ImageAssetFetcher(request, zms))
  }

  override def handles(model: Picture): Boolean = true

  private def createAssetRequest(model: Picture): AssetRequest = model match {
    case PictureUploaded(assetId)    => PublicAssetIdRequest(assetId)
    case PictureNotUploaded(assetId) => UploadAssetIdRequest(assetId)
  }
}

object PictureModelLoader {
  class Factory(context: Context) extends ModelLoaderFactory[Picture, InputStream]
    with Injectable {

    private implicit val injector: Injector = context.asInstanceOf[WireContext].injector

    override def build(multiFactory: MultiModelLoaderFactory): ModelLoader[Picture, InputStream] =
      new PictureModelLoader(inject[Signal[ZMessaging]])

    override def teardown(): Unit = {}
  }
}
