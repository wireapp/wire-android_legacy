/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service.media

import com.waz.log.LogSE._
import com.waz.api.Message
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.messages.media.MediaAssetData
import com.waz.model.{MessageContent, MessageData}
import com.waz.service.assets.AssetService
import com.waz.sync.client.SoundCloudClient
import com.waz.threading.Threading
import com.waz.utils.wrappers.URI
import com.waz.sync.client.ErrorOr

import scala.concurrent.Future

class SoundCloudMediaService(client: SoundCloudClient, assets: AssetService) extends DerivedLogTag {

  import Threading.Implicits.Background

  def updateMedia(msg: MessageData, content: MessageContent): ErrorOr[MessageContent] =
    client.resolve(content.content) map {
      case Right(media) =>
        assets.updateAssets(media.images.to[Vector])
        Right(content.copy(tpe = Message.Part.Type.SOUNDCLOUD, richMedia = Some(media.media)))

      case Left(error) if error.isFatal =>
        warn(l"soundcloud media loading for $content failed fatally: $error, switching back to text")
        Right(content.copy(tpe = Message.Part.Type.TEXT, richMedia = None))

      case Left(error) =>
        warn(l"unable to resolve SoundCloud link $content: $error")
        Left(error)
    }

  def prepareStreaming(media: MediaAssetData): ErrorOr[Vector[URI]] = Future.traverse(media.tracks flatMap (_.streamUrl)) { client.streamingLocation } map { ids =>
    if (ids.nonEmpty && ids.forall(_.isLeft)) Left(ids.head.left.get) else Right(ids collect { case Right(uri) => uri })
  }
}
