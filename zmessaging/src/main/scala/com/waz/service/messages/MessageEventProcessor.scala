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
package com.waz.service.messages


import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{error, verbose, warn}
import com.waz.api.{Message, Verification}
import com.waz.content.MessagesStorage
import com.waz.model.AssetMetaData.Image.Tag.{Medium, Preview}
import com.waz.model.AssetStatus.{UploadCancelled, UploadFailed}
import com.waz.model.GenericContent.{Asset, Calling, Cleared, Ephemeral, ImageAsset, Knock, LastRead, LinkPreview, Location, MsgDeleted, MsgEdit, MsgRecall, Reaction, Receipt, Text}
import com.waz.model._
import com.waz.service.EventScheduler
import com.waz.service.assets.AssetService
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.otr.VerificationStateUpdater.{ClientAdded, ClientUnverified, MemberAdded, VerificationChange}
import com.waz.service.otr.OtrService
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.utils.{RichFuture, _}
import org.threeten.bp.Instant

import scala.concurrent.Future

class MessageEventProcessor(selfUserId:          UserId,
                            storage:             MessagesStorage,
                            content:             MessagesContentUpdater,
                            assets:              AssetService,
                            msgsService:         MessagesService,
                            convs:               ConversationsContentUpdater,
                            otr:                 OtrService) {

  import MessageEventProcessor._
  import Threading.Implicits.Background
  private implicit val ec = EventContext.Global

  val messageEventProcessingStage = EventScheduler.Stage[MessageEvent] { (convId, events) =>
    verbose(s"got events to process: $events")
    convs.processConvWithRemoteId(convId, retryAsync = true) { conv =>
      verbose(s"processing events for conv: $conv, events: $events")
      processEvents(conv, events)
    }
  }


  private[service] def processEvents(conv: ConversationData, events: Seq[MessageEvent]): Future[Set[MessageData]] = {
    val toProcess = events.filter {
      case GenericMessageEvent(_, _, _, msg) if GenericMessage.isBroadcastMessage(msg) => false
      case e => conv.cleared.forall(_.isBefore(e.time.instant))
    }

    val recalls = toProcess collect { case GenericMessageEvent(_, time, from, msg @ GenericMessage(_, MsgRecall(_))) => (msg, from, time.instant) }

    val edits = toProcess collect { case GenericMessageEvent(_, time, from, msg @ GenericMessage(_, MsgEdit(_, _))) => (msg, from, time.instant) }

    for {
      as    <- updateAssets(toProcess)
      msgs  = toProcess map { createMessage(conv, _) } filter (_ != MessageData.Empty)
      _     = verbose(s"messages from events: ${msgs.map(m => m.id -> m.msgType)}")
      res   <- content.addMessages(conv.id, msgs)
      _     <- updateLastReadFromOwnMessages(conv.id, msgs)
      _     <- deleteCancelled(as)
      _     <- Future.traverse(recalls) { case (GenericMessage(id, MsgRecall(ref)), user, time) => msgsService.recallMessage(conv.id, ref, user, MessageId(id.str), time, Message.Status.SENT) }
      _     <- RichFuture.traverseSequential(edits) { case (gm @ GenericMessage(id, MsgEdit(ref, Text(text, mentions, links))), user, time) => msgsService.applyMessageEdit(conv.id, user, time, gm) }
    } yield res
  }

  private def updateAssets(events: Seq[MessageEvent]) = {

    def decryptAssetData(assetData: AssetData, data: Option[Array[Byte]]): Option[Array[Byte]] =
      otr.decryptAssetData(assetData.id, assetData.otrKey, assetData.sha, data, assetData.encryption)

    //ensure we always save the preview to the same id (GenericContent.Asset.unapply always creates new assets and previews))
    def saveAssetAndPreview(asset: AssetData, preview: Option[AssetData]) =
      assets.mergeOrCreateAsset(asset).flatMap {
        case Some(asset) => preview.fold(Future.successful(Seq.empty[AssetData]))(p =>
          assets.mergeOrCreateAsset(p.copy(id = asset.previewId.getOrElse(p.id))).map(_.fold(Seq.empty[AssetData])(Seq(_)))
        )
        case _ => Future.successful(Seq.empty[AssetData])
      }

    //For assets v3, the RAssetId will be contained in the proto content. For v2, it will be passed along with in the GenericAssetEvent
    //A defined convId marks that the asset is a v2 asset.
    def update(id: Uid, convId: Option[RConvId], ct: Any, v2RId: Option[RAssetId], data: Option[Array[Byte]]): Future[Seq[AssetData]] = {
      verbose(s"update asset for event: $id, convId: $convId, ct: $ct, v2RId: $v2RId, data: $data")

      (ct, v2RId) match {
        case (Asset(a@AssetData.WithRemoteId(_), preview), _) =>
          val asset = a.copy(id = AssetId(id.str))
          verbose(s"Received asset v3: $asset with preview: $preview")
          saveAssetAndPreview(asset, preview)
        case (Text(_, _, linkPreviews), _) =>
          Future.sequence(linkPreviews.zipWithIndex.map {
            case (LinkPreview.WithAsset(a@AssetData.WithRemoteId(_)), index) =>
              val asset = a.copy(id = if (index == 0) AssetId(id.str) else AssetId())
              verbose(s"Received link preview asset: $asset")
              saveAssetAndPreview(asset, None)
            case _ => Future successful Seq.empty[AssetData]
          }).map(_.flatten)
        case (Asset(a, p), Some(rId)) =>
          val forPreview = a.otrKey.isEmpty //For assets containing previews, the second GenericMessage contains remote information about the preview, not the asset
          val asset = a.copy(id = AssetId(id.str), remoteId = if (forPreview) None else Some(rId), convId = convId, data = if (forPreview) None else decryptAssetData(a, data))
          val preview = p.map(_.copy(remoteId = if (forPreview) Some(rId) else None, convId = convId, data = if (forPreview) decryptAssetData(a, data) else None))
          verbose(s"Received asset v2 non-image (forPreview?: $forPreview): $asset with preview: $preview")
          saveAssetAndPreview(asset, preview)
        case (ImageAsset(a@AssetData.IsImageWithTag(Preview)), _) =>
          verbose(s"Received image preview for msg: $id. Dropping")
          Future successful Seq.empty[AssetData]
        case (ImageAsset(a@AssetData.IsImageWithTag(Medium)), Some(rId)) =>
          val asset = a.copy(id = AssetId(id.str), remoteId = Some(rId), convId = convId, data = decryptAssetData(a, data))
          verbose(s"Received asset v2 image: $asset")
          assets.mergeOrCreateAsset(asset).map( _.fold(Seq.empty[AssetData])( Seq(_) ))
        case (Asset(a, _), _) if a.status == UploadFailed && a.isImage =>
          verbose(s"Received a message about a failed image upload: $id. Dropping")
          Future successful Seq.empty[AssetData]
        case (Asset(a, _), _) if a.status == UploadCancelled =>
          verbose(s"Uploader cancelled asset: $id")
          assets.updateAsset(AssetId(id.str), _.copy(status = UploadCancelled)).map( _.fold(Seq.empty[AssetData])( Seq(_) ))
        case (Asset(a, preview), _ ) =>
          val asset = a.copy(id = AssetId(id.str))
          verbose(s"Received asset without remote data - we will expect another update: $asset")
          saveAssetAndPreview(asset, preview)
        case (Ephemeral(_, content), _)=>
          update(id, convId, content, v2RId, data)
        case res =>
          Future successful Seq.empty[AssetData]
      }
    }

    Future.sequence(events.collect {
      case GenericMessageEvent(_, time, from, GenericMessage(id, ct)) =>
        update(id, None, ct, None, None)

      case GenericAssetEvent(convId, time, from, msg @ GenericMessage(id, ct), dataId, data) =>
        update(id, Some(convId), ct, Some(dataId), data)
    }) map { _.flatten }
  }

  private def createMessage(conv: ConversationData, event: MessageEvent) = {
    val id = MessageId()
    val convId = conv.id

    //v3 assets go here
    def content(id: MessageId, msgContent: Any, from: UserId, time: Instant, proto: GenericMessage): MessageData = msgContent match {
      case Text(text, mentions, links) =>
        val (tpe, content) = MessageData.messageContent(text, mentions, links)
        MessageData(id, conv.id, tpe, from, content, time = time, localTime = event.localTime.instant, protos = Seq(proto))
      case Knock() =>
        MessageData(id, conv.id, Message.Type.KNOCK, from, time = time, localTime = event.localTime.instant, protos = Seq(proto))
      case Reaction(_, _) => MessageData.Empty
      case Asset(AssetData.WithStatus(UploadCancelled), _) => MessageData.Empty
      case Asset(AssetData.IsVideo(), _) =>
        MessageData(id, convId, Message.Type.VIDEO_ASSET, from, time = time, localTime = event.localTime.instant, protos = Seq(proto))
      case Asset(AssetData.IsAudio(), _) =>
        MessageData(id, convId, Message.Type.AUDIO_ASSET, from, time = time, localTime = event.localTime.instant, protos = Seq(proto))
      case Asset(AssetData.IsImage(), _) | ImageAsset(AssetData.IsImage()) =>
        MessageData(id, convId, Message.Type.ASSET, from, time = time, localTime = event.localTime.instant, protos = Seq(proto))
      case a@Asset(_, _) if a.original == null =>
        MessageData(id, convId, Message.Type.UNKNOWN, from, time = time, localTime = event.localTime.instant, protos = Seq(proto))
      case Asset(_, _) =>
        MessageData(id, convId, Message.Type.ANY_ASSET, from, time = time, localTime = event.localTime.instant, protos = Seq(proto))
      case Location(_, _, _, _) =>
        MessageData(id, convId, Message.Type.LOCATION, from, time = time, localTime = event.localTime.instant, protos = Seq(proto))
      case LastRead(remoteId, timestamp) => MessageData.Empty
      case Cleared(remoteId, timestamp) => MessageData.Empty
      case MsgDeleted(_, _) => MessageData.Empty
      case MsgRecall(_) => MessageData.Empty
      case MsgEdit(_, _) => MessageData.Empty
      case Receipt(_) => MessageData.Empty
      case Calling(_) => MessageData.Empty
      case Ephemeral(expiry, ct) =>
        content(id, ct, from, time, proto).copy(ephemeral = expiry)
      case _ =>
        error(s"unexpected generic message content: $msgContent")
        // TODO: this message should be processed again after app update, maybe future app version will understand it
        MessageData(id, conv.id, Message.Type.UNKNOWN, from, time = time, localTime = event.localTime.instant, protos = Seq(proto))
    }

    //v2 assets go here
    def assetContent(id: MessageId, ct: Any, from: UserId, time: Instant, msg: GenericMessage): MessageData = ct match {
      case Asset(AssetData.IsVideo(), _) =>
        MessageData(id, convId, Message.Type.VIDEO_ASSET, from, time = time, localTime = event.localTime.instant, protos = Seq(msg))
      case Asset(AssetData.IsAudio(), _) =>
        MessageData(id, convId, Message.Type.AUDIO_ASSET, from, time = time, localTime = event.localTime.instant, protos = Seq(msg))
      case ImageAsset(AssetData.IsImageWithTag(Preview)) => //ignore previews
        MessageData.Empty
      case Asset(AssetData.IsImage(), _) | ImageAsset(AssetData.IsImage()) =>
        MessageData(id, convId, Message.Type.ASSET, from, time = time, localTime = event.localTime.instant, protos = Seq(msg))
      case a@Asset(_, _) if a.original == null =>
        MessageData(id, convId, Message.Type.UNKNOWN, from, time = time, localTime = event.localTime.instant, protos = Seq(msg))
      case Asset(_, _) =>
        MessageData(id, convId, Message.Type.ANY_ASSET, from, time = time, localTime = event.localTime.instant, protos = Seq(msg))
      case Ephemeral(expiry, ect) =>
        assetContent(id, ect, from, time, msg).copy(ephemeral = expiry)
      case _ =>
        error(s"unexpected generic asset content: $msg")
        // TODO: this message should be processed again after app update, maybe future app version will understand it
        MessageData(id, conv.id, Message.Type.UNKNOWN, from, time = time, localTime = event.localTime.instant, protos = Seq(msg))
    }

    /**
      * Creates safe version of incoming message.
      * Messages sent by malicious contacts might contain content intended to break the app. One example of that
      * are very long text messages, backend doesn't restrict the size much to allow for assets and group messages,
      * because of encryption it's also not possible to limit text messages there. On client such messages are handled
      * inline, and will cause memory problems.
      * We may need to do more involved checks in future.
      */
    def sanitize(msg: GenericMessage): GenericMessage = msg match {
      case GenericMessage(uid, Text(text, mentions, links)) if text.length > MaxTextContentLength =>
        GenericMessage(uid, Text(text.take(MaxTextContentLength), mentions, links.filter { p => p.url.length + p.urlOffset <= MaxTextContentLength }))
      case _ =>
        msg
    }

    event match {
      case ConnectRequestEvent(_, time, from, text, recipient, name, email) =>
        MessageData(id, convId, Message.Type.CONNECT_REQUEST, from, MessageData.textContent(text), recipient = Some(recipient), email = email, name = Some(name), time = time.instant, localTime = event.localTime.instant)
      case RenameConversationEvent(_, time, from, name) =>
        MessageData(id, convId, Message.Type.RENAME, from, name = Some(name), time = time.instant, localTime = event.localTime.instant)
      case MemberJoinEvent(_, time, from, userIds, firstEvent) =>
        MessageData(id, convId, Message.Type.MEMBER_JOIN, from, members = userIds.toSet, time = time.instant, localTime = event.localTime.instant, firstMessage = firstEvent)
      case MemberLeaveEvent(_, time, from, userIds) =>
        MessageData(id, convId, Message.Type.MEMBER_LEAVE, from, members = userIds.toSet, time = time.instant, localTime = event.localTime.instant)
      case OtrErrorEvent(_, time, from, IdentityChangedError(_, _)) =>
        MessageData (id, conv.id, Message.Type.OTR_IDENTITY_CHANGED, from, time = time.instant, localTime = event.localTime.instant)
      case OtrErrorEvent(_, time, from, otrError) =>
        MessageData (id, conv.id, Message.Type.OTR_ERROR, from, time = time.instant, localTime = event.localTime.instant)
      case GenericMessageEvent(_, time, from, proto) =>
        val sanitized @ GenericMessage(uid, msgContent) = sanitize(proto)
        content(MessageId(uid.str), msgContent, from, time.instant, sanitized)
      case GenericAssetEvent(_, time, from, proto @ GenericMessage(uid, msgContent), dataId, data) =>
        assetContent(MessageId(uid.str), msgContent, from, time.instant, proto)
      case _:CallMessageEvent =>
        MessageData.Empty
      case _ =>
        warn(s"Unexpected event for addMessage: $event")
        MessageData.Empty
    }
  }

  private def deleteCancelled(as: Seq[AssetData]) = {
    val toRemove = as collect {
      case a@AssetData.WithStatus(UploadCancelled) => a.id
    }
    if (toRemove.isEmpty) Future.successful(())
    else for {
      _ <- Future.traverse(toRemove)(id => storage.remove(MessageId(id.str)))
      _ <- assets.removeAssets(toRemove)
    } yield ()
  }

  private def updateLastReadFromOwnMessages(convId: ConvId, msgs: Seq[MessageData]) =
    msgs.reverseIterator.find(_.userId == selfUserId).fold2(Future.successful(None), msg => convs.updateConversationLastRead(convId, msg.time))

  def addMessagesAfterVerificationUpdate(updates: Seq[(ConversationData, ConversationData)], convUsers: Map[ConvId, Seq[UserData]], changes: Map[UserId, VerificationChange]) =
    Future.traverse(updates) {
      case (prev, up) if up.verified == Verification.VERIFIED => msgsService.addOtrVerifiedMessage(up.id)
      case (prev, up) if prev.verified == Verification.VERIFIED =>
        verbose(s"addMessagesAfterVerificationUpdate with prev=${prev.verified} and up=${up.verified}")
        val convId = up.id
        val changedUsers = convUsers(convId).filter(!_.isVerified).flatMap { u => changes.get(u.id).map(u.id -> _) }
        val (users, change) =
          if (changedUsers.forall(c => c._2 == ClientAdded)) (changedUsers map (_._1), ClientAdded)
          else if (changedUsers.forall(c => c._2 == MemberAdded)) (changedUsers map (_._1), MemberAdded)
          else (changedUsers collect { case (user, ClientUnverified) => user }, ClientUnverified)

        val (self, other) = users.partition(_ == selfUserId)
        for {
          _ <- if (self.nonEmpty) msgsService.addOtrUnverifiedMessage(convId, Seq(selfUserId), change) else Future.successful(())
          _ <- if (other.nonEmpty) msgsService.addOtrUnverifiedMessage(convId, other, change) else Future.successful(())
        } yield ()
      case _ =>
        Future.successful(())
    }

}

object MessageEventProcessor {
  val MaxTextContentLength = 8192
}
