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
package com.waz.sync.handler

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.Message
import com.waz.api.Message.Part
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse._
import com.waz.content._
import com.waz.model.AssetMetaData.Image.Tag.Medium
import com.waz.model.GenericContent.{Asset, LinkPreview, Text}
import com.waz.model.GenericMessage.TextMessage
import com.waz.model._
import com.waz.service.assets.AssetService
import com.waz.service.images.{ImageAssetGenerator, ImageLoader}
import com.waz.service.messages.MessagesService
import com.waz.service.otr.OtrServiceImpl
import com.waz.sync.SyncResult
import com.waz.sync.client.AssetClient.Retention
import com.waz.sync.client.OpenGraphClient.OpenGraphData
import com.waz.sync.client.{AssetClient, OpenGraphClient}
import com.waz.sync.otr.OtrSyncHandler
import com.waz.utils.RichFuture
import com.waz.utils.wrappers.URI
import org.threeten.bp.Instant

import scala.concurrent.Future

class OpenGraphSyncHandler(convs:           ConversationStorage,
                           messages:        MessagesStorage,
                           otrService:      OtrServiceImpl,
                           assetSync:       AssetSyncHandler,
                           assetsStorage:   AssetsStorage,
                           assets:          AssetService,
                           otrSync:         OtrSyncHandler,
                           client:          OpenGraphClient,
                           imageGenerator:  ImageAssetGenerator,
                           imageLoader:     ImageLoader,
                           messagesService: MessagesService,
                           assetClient:     AssetClient) { //TODO assetClient not used
  import com.waz.threading.Threading.Implicits.Background

  def postMessageMeta(convId: ConvId, msgId: MessageId, editTime: RemoteInstant): Future[SyncResult] = messages.getMessage(msgId) flatMap {
    case None => Future successful SyncResult(internalError(s"No message found with id: $msgId"))
    case Some(msg) if msg.msgType != Message.Type.RICH_MEDIA =>
      debug(s"postMessageMeta, message is not RICH_MEDIA: $msg")
      Future successful SyncResult.Success
    case Some(msg) if msg.content.forall(_.tpe != Part.Type.WEB_LINK) =>
      verbose(s"postMessageMeta, no WEB_LINK found in msg: $msg")
      Future successful SyncResult.Success
    case Some(msg) if msg.editTime != editTime =>
      verbose(s"postMessageMeta, message has already been edited: $msg")
      Future successful SyncResult.Success
    case Some(msg) =>
      convs.get(convId) flatMap {
        case None => Future successful SyncResult(internalError(s"No conversation found with id: $convId"))
        case Some(conv) =>
          messagesService.retentionPolicy(conv).future.flatMap { retention =>
            updateOpenGraphData(msg, retention) flatMap {
              case Left(errors) => Future successful SyncResult(errors.head)
              case Right(links) =>
                updateLinkPreviews(msg, links, retention) flatMap {
                  case Left(errors) => Future successful SyncResult(errors.head)
                  case Right(TextMessage(_, _, Seq())) =>
                    verbose(s"didn't find any previews in message links: $msg")
                    Future successful SyncResult.Success
                  case Right(proto) =>
                    verbose(s"updated link previews: $proto")
                    otrSync.postOtrMessage(conv, proto) map {
                      case Left(err) => SyncResult(err)
                      case Right(_) => SyncResult.Success
                    }
                }
            }
          }
      }
  }

  private def updateIfNotEdited(msg: MessageData, updater: MessageData => MessageData) =
    messages.update(msg.id, {
      case m if msg.editTime == m.editTime => updater(m)
      case m => m
    })

  def updateOpenGraphData(msg: MessageData, retention: Retention): Future[Either[Iterable[ErrorResponse], Seq[MessageContent]]] = {

    def updateOpenGraphData(part: MessageContent) =
      if (part.openGraph.isDefined || part.tpe != Part.Type.WEB_LINK) Future successful Right(part)
      else client.loadMetadata(part.contentAsUri).future map {
        case Right(None) => Right(part.copy(tpe = Part.Type.TEXT)) // no open graph data is available
        case Right(Some(data)) => Right(part.copy(openGraph = Some(data)))
        case Left(err) => Left(err)
      }

    Future.traverse(msg.content)(updateOpenGraphData) flatMap { res =>
      val errors = res.collect { case Left(err) => err }
      val parts = res.zip(msg.content) map {
        case (Right(p), _) => p
        case (_, p) => p
      }

      verbose(s"loaded open graph data: $res")
      if (errors.nonEmpty) error(s"open graph loading failed: $errors")

      updateIfNotEdited(msg, _.copy(content = parts)) map { _ =>
        if (errors.isEmpty) Right(parts.filter(_.tpe == Part.Type.WEB_LINK))
        else Left(errors)
      }
    }
  }

  def updateLinkPreviews(msg: MessageData, links: Seq[MessageContent], retention: Retention) = {

    def createEmptyPreviews(content: String) = {
      var offset = -1
      links map { l =>
        offset = content.indexOf(l.content, offset + 1)
        assert(offset >= 0) // XXX: link has to be present in original content, parts are taken directly from it
        LinkPreview(URI.parse(l.content), offset)
      }
    }

    msg.protos.lastOption match {
      case Some(TextMessage(content, mentions, ps)) =>
        val previews = if (ps.isEmpty) createEmptyPreviews(content) else ps

        RichFuture.traverseSequential(links zip previews) { case (link, preview) => generatePreview(msg.assetId, link.openGraph.get, preview, retention) } flatMap { res =>
          val errors = res collect { case Left(err) => err }
          val updated = (res zip previews) collect {
            case (Right(p), _) => p
            case (_, p) => p
          }

          val proto = GenericMessage(msg.id.uid, msg.ephemeral, Text(content, mentions, updated))

          updateIfNotEdited(msg, _.copy(protos = Seq(proto))) map { _ => if (errors.isEmpty) Right(proto) else Left(errors) }
        }

      case _ =>
        Future successful Left(Seq(ErrorResponse.internalError(s"Unexpected message protos in: $msg")))
    }
  }

  def generatePreview(assetId: AssetId, meta: OpenGraphData, prev: LinkPreview, retention: Retention) = {

    /**
      * Generates and uploads link preview image for given open graph metadata.
      * @return
      *         Left(error) if upload fails
      *         Right(None) if metadata doesn't include a link or if source image could not be fetched (XXX: in some cases this could be due to network issues or image download setting preventing us from downloading on metered network)
      *         Right(Asset) if upload was successful
      */
    def uploadImage: Future[Either[ErrorResponse, Option[AssetData]]] = meta.image match {
      case None => Future successful Right(None)
      case Some(uri) =>
        for {
          Some(asset) <- imageGenerator.generateWireAsset(AssetData.newImageAsset(assetId, Medium).copy(source = Some(uri)), profilePicture = false).map(Some(_)).recover { case _: Throwable => None }.future
          _           <- assetsStorage.mergeOrCreateAsset(asset) //must be in storage for assetsync
          resp        <- assetSync.uploadAssetData(asset.id, retention = retention).future
        } yield resp
    }
    if (prev.hasArticle) Future successful Right(prev)
    else
      uploadImage map {
        case Left(error) => Left(error)
        case Right(imageAsset) =>
          Right(LinkPreview(URI.parse(prev.url), prev.urlOffset, meta.title, meta.description, imageAsset.map(Asset(_)), meta.permanentUrl))
      }
  }
}
