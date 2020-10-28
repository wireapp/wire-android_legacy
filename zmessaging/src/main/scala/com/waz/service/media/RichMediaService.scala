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
import com.waz.api.impl.ErrorResponse
import com.waz.api.{MediaProvider, Message}
import com.waz.content.MessagesStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.model.messages.media.MediaAssetData
import com.waz.service.messages.MessagesContentUpdater
import com.waz.sync.SyncServiceHandle
import com.waz.threading.Threading
import com.wire.signals.EventContext
import com.waz.utils.wrappers.URI
import com.waz.sync.client.ErrorOr

import scala.concurrent.Future

class RichMediaService(msgsStorage: MessagesStorage,
                       messages:    MessagesContentUpdater,
                       sync:        SyncServiceHandle,
                       youTube:     YouTubeMediaService) extends DerivedLogTag {
  import com.waz.api.Message.Part.Type._
  import Threading.Implicits.Background

  private def isSyncableMsg(msg: MessageData) = msg.msgType == Message.Type.RICH_MEDIA && msg.content.exists(isSyncable)

  private def isSyncable(c: MessageContent) = c.tpe match {
    case YOUTUBE | GOOGLE_MAPS => true
    case _ => false
  }

  private def syncableContentChanged(prev: MessageData, updated: MessageData) = {
    prev.content.size != updated.content.size || prev.content.zip(updated.content).exists { case (c1, c2) => c1.content != c2.content && isSyncable(c2) }
  }

  msgsStorage.onAdded(msgs => scheduleSyncFor(msgs.filter(isSyncableMsg)))

  msgsStorage.onUpdated { _ foreach {
    case (prev, updated) =>
      if (isSyncableMsg(updated) && syncableContentChanged(prev, updated)) {
        verbose(l"Updated rich media message: $updated, scheduling sync")
        sync.syncRichMedia(updated.id)
      }
  } }

  private def scheduleSyncFor(ms: Seq[MessageData]) = if (ms.nonEmpty) {
    verbose(l"Scheduling sync for added rich media messages: $ms")

    msgsStorage.updateAll2(ms.map(_.id), m => m.copy(content = m.content map (c => c.copy(syncNeeded = isSyncable(c))))) map { _ =>
      Future.traverse(ms) { m => sync.syncRichMedia(m.id) }
    }
  }

  def updateRichMedia(id: MessageId): Future[Seq[ErrorResponse]] = {
    msgsStorage.getMessage(id) flatMap {
      case Some(data) =>
        for {
          results <- Future.traverse(data.content) { updateRichMediaContent(data, _) }
          newContent = data.content.zip(results) map {
            case (_, Right(updated)) => updated.copy(syncNeeded = false)
            case (prev, Left(_))     => prev.copy(syncNeeded = false)
          }
          _ <- messages.updateMessage(id)(_.copy(content = newContent))
        } yield {
          results.collect { case Left(error) => error }
        }
      case None =>
        error(l"No message data found with id: $id")
        Future.successful(Seq(ErrorResponse.InternalError))
    }
  }

  private def updateRichMediaContent(msg: MessageData, content: MessageContent): Future[Either[ErrorResponse, MessageContent]] = content.tpe match {
    case YOUTUBE     => youTube.updateMedia(msg, content)
    case _           => Future.successful(Right(content))
  }

  def prepareStreaming(media: MediaAssetData): ErrorOr[Vector[URI]] = media.provider match {
    case MediaProvider.YOUTUBE    => youTube.prepareStreaming(media)
    case other =>
      warn(l"Unable to prepare streaming for rich media from $other.")
      Future.successful(Right(Vector.empty))
  }
}
