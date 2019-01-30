/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
import com.bumptech.glide.load.model.{ModelLoader, ModelLoaderFactory, MultiModelLoaderFactory}
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.glide.{AssetIdRequest, ImageAssetRequest}
import com.waz.zclient.{Injectable, Injector, WireContext}

class ImageAssetRequestModelLoaderFactory(context: Context) extends ModelLoaderFactory[ImageAssetRequest, InputStream] with Injectable {
  private implicit val injector: Injector = context.asInstanceOf[WireContext].injector

  override def build(multiFactory: MultiModelLoaderFactory): ModelLoader[ImageAssetRequest, InputStream] = {
    new ImageAssetRequestModelLoader(inject[Signal[ZMessaging]])
  }

  override def teardown(): Unit = {}
}

class AssetIdRequestModelLoaderFactory(context: Context) extends ModelLoaderFactory[AssetIdRequest, InputStream] with Injectable {
  private implicit val injector: Injector = context.asInstanceOf[WireContext].injector

  override def build(multiFactory: MultiModelLoaderFactory): ModelLoader[AssetIdRequest, InputStream] = {
    new AssetIdRequestModelLoader(inject[Signal[ZMessaging]])
  }

  override def teardown(): Unit = {}
}
