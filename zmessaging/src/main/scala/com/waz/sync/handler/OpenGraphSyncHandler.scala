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

import java.io.File
import com.waz.api.Message
import com.waz.api.Message.Part
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.GenericContent.{Asset, LinkPreview, Text}
import com.waz.model.GenericMessage.TextMessage
import com.waz.model._
import com.waz.model.errors._
import com.waz.service.BackendConfig
import com.waz.service.BackendConfig.FederationSupport
import com.waz.service.assets.{AES_CBC_Encryption, AssetService, Content, ContentForUpload, Asset => Asset2}
import com.waz.service.messages.MessagesService
import com.waz.sync.SyncResult
import com.waz.sync.client.AssetClient.Retention
import com.waz.sync.client.OpenGraphClient.OpenGraphData
import com.waz.sync.client.{ErrorOr, OpenGraphClient}
import com.waz.sync.otr.OtrSyncHandler
import com.wire.signals.{CancellableFuture, Signal}
import com.waz.utils.RichFuture
import com.waz.utils.wrappers.URI
import com.waz.zms.BuildConfig

import scala.concurrent.Future

class OpenGraphSyncHandler(backend:         Signal[BackendConfig],
                           convs:           ConversationStorage,
                           messages:        MessagesStorage,
                           assets:          AssetService,
                           otrSync:         OtrSyncHandler,
                           client:          OpenGraphClient,
                           messagesService: MessagesService) extends DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  private def federationSupported: Boolean = backend.currentValue.exists { b => b.federationSupport.isSupported }

  def postMessageMeta(convId: ConvId, msgId: MessageId, editTime: RemoteInstant): Future[SyncResult] = messages.getMessage(msgId) flatMap {
    case None => Future successful SyncResult(internalError(s"No message found with id: $msgId"))
    case Some(msg) if msg.msgType != Message.Type.RICH_MEDIA =>
      debug(l"postMessageMeta, message is not RICH_MEDIA: $msg")
      Future successful SyncResult.Success
    case Some(msg) if msg.content.forall(_.tpe != Part.Type.WEB_LINK) =>
      verbose(l"postMessageMeta, no WEB_LINK found in msg: $msg")
      Future successful SyncResult.Success
    case Some(msg) if msg.editTime != editTime =>
      verbose(l"postMessageMeta, message has already been edited: $msg")
      Future successful SyncResult.Success
    case Some(msg) =>
      convs.get(convId) flatMap {
        case None => Future successful SyncResult(internalError(s"No conversation found with id: $convId"))
        case Some(conv) =>
          messagesService.retentionPolicy2(conv).flatMap { retention =>
            updateOpenGraphData(msg, retention) flatMap {
              case Left(errors) => Future successful SyncResult(errors.head)
              case Right(links) =>
                updateLinkPreviews(msg, links, retention) flatMap {
                  case Left(errors) => Future successful SyncResult(errors.head)
                  case Right(TextMessage(_, _, Seq(), _, _)) =>
                    verbose(l"didn't find any previews in message links: $msg")
                    Future successful SyncResult.Success
                  case Right(proto) =>
                    verbose(l"updated link previews: $proto")
                    val m = proto.unpack._2.proto
                    val postMsg = if (federationSupported) {
                      otrSync.postQualifiedOtrMessage(conv.id, proto, isHidden = false)
                    } else {
                      otrSync.postOtrMessage(conv.id, proto, isHidden = false)
                    }
                    postMsg.map {
                      case Left(err) => SyncResult(err)
                      case Right(_)  => SyncResult.Success
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

      verbose(l"loaded open graph data: ${res.collect { case Right(p) => p}}")
      if (errors.nonEmpty) error(l"open graph loading failed: $errors")

      updateIfNotEdited(msg, _.copy(content = parts)) map { _ =>
        if (errors.isEmpty) Right(parts.filter(_.tpe == Part.Type.WEB_LINK))
        else Left(errors)
      }
    }
  }

  //TODO Refactor. Be explicit about intentions in situation when we have few links in one message
  def updateLinkPreviews(msg: MessageData, links: Seq[MessageContent], retention: Retention): Future[Either[Seq[ErrorResponse], GenericMessage]] = {

    def createEmptyPreviews(content: String) = {
      var offset = -1
      links map { l =>
        offset = content.indexOf(l.content, offset + 1)
        assert(offset >= 0) // XXX: link has to be present in original content, parts are taken directly from it
        LinkPreview(URI.parse(l.content), offset)
      }
    }

    verbose(l"Updating Link preview...")
    msg.genericMsgs.lastOption match {
      case Some(TextMessage(content, mentions, ps, quote, rr)) =>

        val previews = if (ps.isEmpty) createEmptyPreviews(content).headOption else ps
        verbose(l"Updating Link preview for message ${content}: previews ${previews}")
        RichFuture.traverseSequential(links zip previews)
          { case (link, preview) => generatePreview(msg.id, link.openGraph.get, preview, retention) } flatMap { res =>
            val errors = res collect { case Left(err) => err }
            val updated = (res zip previews) collect {
              case (Right(p), _) => p._2
              case (_, p) => p
            }

            val gm = GenericMessage(msg.id.uid, msg.ephemeral, Text(content, mentions, updated.headOption, quote, rr, msg.protoLegalHoldStatus))
            verbose(l"Update generic message with previews: ${updated}")
            updateIfNotEdited(msg, _.copy(genericMsgs = Seq(gm))) map { _ => if (errors.isEmpty) Right(gm) else Left(errors) }
          }

      case _ =>
        Future successful Left(Seq(ErrorResponse.internalError(s"Unexpected message protos in: $msg")))
    }
  }

  def generatePreview(messageId: MessageId, meta: OpenGraphData, prev: LinkPreview, retention: Retention): ErrorOr[(Option[AssetId], LinkPreview)] = {

    def downloadImageFile: CancellableFuture[Option[File]] = meta.image match {
      case None => CancellableFuture.successful(None)
      case Some(image) => client.downloadImage(image).map(Option.apply).recover { case err => None }
    }

    def uploadImage(imageFile: File): CancellableFuture[Asset2] = {
      val content = ContentForUpload(s"open_graph_image_${prev.proto.getPermanentUrl}", Content.File(Mime.Image.Jpg, imageFile))
      val encryption = AES_CBC_Encryption.random
      for {
        rawAsset <- assets.createAndSaveUploadAsset(content, encryption, public = false, retention, Some(messageId)).lift
        asset <- assets.uploadAsset(rawAsset.id)
      } yield asset
    }

    if (prev.proto.hasArticle) Future successful Right(None -> prev)
    else for {
      imageFile <- downloadImageFile
      imageAsset <- imageFile match {
        case None => CancellableFuture.successful(Right(None))
        case Some(file) =>
          uploadImage(file)
            .map(asset => Right(Option(asset)))
            .recover { case err => Left(ErrorResponse.internalError(s"Error while uploading open graph image $err")) }
      }
      result = imageAsset.right.map { asset =>
        val assetId = asset.map(_.id)
        val messagesAsset = asset.map(Asset.apply(_, None, expectsReadConfirmation = false, Messages.LegalHoldStatus.UNKNOWN))
        val permanentUri = meta.permanentUrl.map(url => URI.parse(url.toString))
        val preview = LinkPreview(
          uri = URI.parse(prev.proto.getUrl),
          offset = prev.proto.getUrlOffset,
          title = meta.title,
          summary = meta.description,
          image = messagesAsset,
          permanentUrl = permanentUri
        )

        assetId -> preview
      }
    } yield result
  }
}
