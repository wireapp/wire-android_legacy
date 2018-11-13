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
package com.waz.zclient.glide

import java.net.URL

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import com.bumptech.glide.{Glide, RequestBuilder, RequestManager}
import com.waz.model.{AssetData, AssetId}
import com.waz.utils.wrappers.{AndroidURIUtil, URI}

object WireGlide {
  def apply()(implicit context: Context): RequestManager = Glide.`with`(context)
}

object GlideBuilder {
  def apply(drawable: Drawable)(implicit context: Context): RequestBuilder[Drawable] = WireGlide().load(drawable)
  def forAsset(assetId: AssetId)(implicit context: Context): RequestBuilder[Drawable] = WireGlide().load(Asset2Request(assetId))
  def apply(assetId: AssetId)(implicit context: Context): RequestBuilder[Drawable] = WireGlide().load(AssetIdRequest(assetId))
  def apply(assetData: AssetData)(implicit context: Context): RequestBuilder[Drawable] = WireGlide().load(AssetDataRequest(assetData))
  def apply(assetRequest: AssetRequest)(implicit context: Context): RequestBuilder[Drawable] = WireGlide().load(assetRequest)
  def apply(uri: Uri)(implicit context: Context): RequestBuilder[Drawable] = WireGlide().load(uri)
  def apply(uri: URI)(implicit context: Context): RequestBuilder[Drawable] = WireGlide().load(AndroidURIUtil.unwrap(uri))
  def apply(url: URL)(implicit context: Context): RequestBuilder[Drawable] = WireGlide().load(Uri.parse(url.toString))
}
