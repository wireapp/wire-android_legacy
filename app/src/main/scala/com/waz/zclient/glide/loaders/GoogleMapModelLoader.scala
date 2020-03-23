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
import com.bumptech.glide.load.model.{ModelLoader, ModelLoaderFactory, MultiModelLoaderFactory}
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.waz.api.MessageContent.Location
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.core.images.AssetKey
import com.waz.zclient.{Injectable, Injector, WireContext}
import com.waz.zclient.glide.{GoogleMapRequest, ImageAssetFetcher}
import com.waz.zclient.log.LogUI._

class GoogleMapModelLoader(zms: Signal[ZMessaging]) extends ModelLoader[Location, InputStream]
  with DerivedLogTag {

  override def buildLoadData(model: Location, width: Int, height: Int, options: Options): ModelLoader.LoadData[InputStream] = {
    val request = GoogleMapRequest(model)
    val key = new AssetKey(request.toString, width, height, options)
    verbose(l"key: $key")
    new LoadData[InputStream](key, new ImageAssetFetcher(request, zms))
  }

  override def handles(model: Location): Boolean = true
}

object GoogleMapModelLoader {

  class Factory(context: Context) extends ModelLoaderFactory[Location, InputStream]
    with Injectable {

    private implicit val injector: Injector = context.asInstanceOf[WireContext].injector

    override def build(multiFactory: MultiModelLoaderFactory): ModelLoader[Location, InputStream] = {
      new GoogleMapModelLoader(inject[Signal[ZMessaging]])
    }

    override def teardown(): Unit = {}
  }
}
