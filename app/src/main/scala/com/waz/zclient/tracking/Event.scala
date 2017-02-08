/**
  * Wire
  * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.tracking

import com.waz.api._
import com.waz.model.ConversationData._
import com.waz.model.Mime
import com.waz.zclient.core.controllers.tracking.attributes.Attribute._
import com.waz.zclient.core.controllers.tracking.attributes.RangedAttribute._
import com.waz.zclient.core.controllers.tracking.attributes.{Attribute, RangedAttribute}
import com.waz.zclient.pages.main.conversation.views.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.utils.AssetUtils
import org.threeten.bp.Duration

sealed abstract class Event(val name: String) {
  val attributes       = Map.empty[Attribute, String]
  val rangedAttributes = Map.empty[RangedAttribute, Int]
}

case class OpenedMessageActionEvent(action: MessageAction, messageType: String) extends Event("conversation.opened_message_action") {
  import MessageAction._
  override val attributes = Map(
    ACTION -> (action match {
      case DELETE_GLOBAL => "delete_for_everyone"
      case DELETE_LOCAL  => "delete_for_me"
      case COPY          => "copy"
      case EDIT          => "edit"
      case FORWARD       => "forward"
      case _             => "other"
    }),
    TYPE -> messageType
  )
}

case class CopiedMessageEvent(messageType: String) extends Event("conversation.copied_message") { override val attributes = Map(TYPE -> messageType) }
case class ForwardedMessageEvent(messageType: String) extends Event("conversation.forwarded_message") { override val attributes = Map(TYPE -> messageType) }

case class ReactedToMessageEvent(liked: Boolean,
                                 withOtto: Boolean,
                                 fromMe: Boolean,
                                 msgType: Message.Type,
                                 convType: ConversationType,
                                 lastMessage: Boolean,
                                 method: String) extends Event("conversation.reacted_to_message") {
  override val attributes = Map (
    ACTION            -> (if (liked) "like" else "unlike"),
    TYPE              -> msgType.name,
    METHOD            -> method,
    USER              -> (if (!fromMe) "receiver" else "sender"),
    IS_LAST_MESSAGE   -> String.valueOf(lastMessage),
    CONVERSATION_TYPE -> convType.name,
    WITH_BOT          -> String.valueOf(withOtto)
  )
}

case class DeletedMessageEvent(messageType: String, forEveryone: Boolean) extends Event("conversation.deleted_message") {
  override val attributes = Map(
    TYPE -> messageType,
    METHOD -> (if (forEveryone) "for_everyone" else "for_me")
  )
}

abstract class FileEvent(name: String, fileMimeType: String, fileSizeInBytes: Int) extends Event(name) {
  override val attributes = Map(
    TYPE            -> AssetUtils.assetMimeTypeToExtension(fileMimeType),
    FILE_SIZE_BYTES -> String.valueOf(fileSizeInBytes)
  )
  override val rangedAttributes = Map(FILE_SIZE_MB -> AssetUtils.assetSizeToMB(fileSizeInBytes))
}

case class SavedFileEvent(fileMimeType: String, fileSizeInBytes: Int) extends FileEvent("file.saved_file", fileMimeType, fileSizeInBytes)
case class OpenedFileEvent(fileMimeType: String, fileSizeInBytes: Int) extends FileEvent("file.opened_file", fileMimeType, fileSizeInBytes)


abstract class MessageEvent(name: String, playedByReceiver: Boolean, withOtto: Boolean, convType: ConversationType) extends Event(name) {
  protected val baseAttributes = Map(
    Attribute.USER              -> (if (playedByReceiver) "receiver" else "sender"),
    Attribute.WITH_BOT          -> String.valueOf(withOtto),
    Attribute.CONVERSATION_TYPE -> convType.name
  )
  override val attributes = baseAttributes
}

abstract class DurationMessageEvent(name: String, durationSec: Int, playedByReceiver: Boolean, withOtto: Boolean, convType: ConversationType)
  extends MessageEvent(name, playedByReceiver, withOtto, convType) {
  override val rangedAttributes = Map(VIDEO_AND_AUDIO_MESSAGE_DURATION -> durationSec)
}

case class PlayedVideoMessageEvent(duration: Int, playedByReceiver: Boolean, isOtto: Boolean, convType: ConversationType)
  extends DurationMessageEvent("media.played_video_message", duration, playedByReceiver, isOtto, convType)

case class PlayedAudioMessageEvent(fileMimeType: String, durationSec: Int, playedByReceiver: Boolean, isOtto: Boolean, convType: ConversationType)
  extends DurationMessageEvent("media.played_audio_message", durationSec, playedByReceiver, isOtto, convType) {
  override val attributes = baseAttributes + (TYPE -> AssetUtils.assetMimeTypeToExtension(fileMimeType))
}

case class PlayedYouTubeMessageEvent(playedByReceiver: Boolean, isOtto: Boolean, convType: ConversationType)
  extends MessageEvent("media.played_youtube_message", playedByReceiver, isOtto, convType)

abstract class CollectionsEvent(name: String, conversationType: ConversationType, withBot: Boolean) extends Event(name){
  protected val baseAttributes = Map(
    WITH_BOT          -> String.valueOf(withBot),
    CONVERSATION_TYPE -> conversationType.name
  )

  protected def trackingType(messageType: Message.Type): String = {
    messageType match {
      case Message.Type.ASSET => "image"
      case Message.Type.RICH_MEDIA => "link"
      case Message.Type.ANY_ASSET => "file"
      case m => m.name()
    }
  }
}
case class OpenedCollectionsEvent(isEmpty: Boolean, withSearchResult: Boolean, conversationType: ConversationType, withBot: Boolean) extends CollectionsEvent("collections.opened_collections", conversationType, withBot){
  override val attributes = baseAttributes + (IS_EMPTY -> String.valueOf(isEmpty), WITH_SEARCH_RESULT -> String.valueOf(withSearchResult))
}
case class OpenedItemCollectionsEvent(messageType: Message.Type, conversationType: ConversationType, withBot: Boolean) extends CollectionsEvent("collections.opened_item", conversationType, withBot){
  override val attributes = baseAttributes + (TYPE -> trackingType(messageType))
}
case class OpenedItemMenuCollectionsEvent(messageType: Message.Type, conversationType: ConversationType, withBot: Boolean) extends CollectionsEvent("collections.opened_item_menu", conversationType, withBot){
  override val attributes = baseAttributes + (TYPE -> trackingType(messageType))
}
case class DidItemActionCollectionsEvent(messageAction: MessageAction, messageType: Message.Type, conversationType: ConversationType, withBot: Boolean) extends CollectionsEvent("collections.did_item_action", conversationType, withBot){
  import MessageAction._
  override val attributes = baseAttributes ++ Map(
    TYPE   -> trackingType(messageType),
    ACTION -> (messageAction match {
      case DELETE_GLOBAL => "delete_for_everyone"
      case DELETE_LOCAL  => "delete_for_me"
      case COPY          => "copy"
      case EDIT          => "edit"
      case FORWARD       => "forward"
      case _             => "other"
    })
  )
}
case class EnteredSearchCollectionsEvent(conversationType: ConversationType, withBot: Boolean) extends CollectionsEvent("collections.entered_search", conversationType, withBot){
  override val attributes = baseAttributes
}
case class SelectedSearchResultCollectionsEvent(conversationType: ConversationType, withBot: Boolean) extends CollectionsEvent("collections.selected_search_result", conversationType, withBot){
  override val attributes = baseAttributes
}

class CallEvent(name: String, isV3: Boolean, isGroupCall: Boolean, withOtto: Boolean) extends Event(name) {
  val baseAttributes = Map(
    CALLING_VERSION   -> (if (isV3) "C3" else "C2"),
    CONVERSATION_TYPE -> (if (isGroupCall) "GROUP" else "ONE_TO_ONE"),
    WITH_OTTO         -> String.valueOf(withOtto)
  )
  override val attributes = baseAttributes
}

object ReceivedCallEvent {
  def apply(isV3: Boolean, isVideo: Boolean, isGroupCall: Boolean, wasUiActiveOnCallStart: Boolean, withOtto: Boolean) =
    new CallEvent(if (isVideo) "calling.received_video_call" else "calling.received_call", isV3, isGroupCall, withOtto) {
      override val attributes = baseAttributes + (CALLING_APP_IS_ACTIVE -> String.valueOf(wasUiActiveOnCallStart))
  }
}

object StartedCallEvent {
  def apply(isV3: Boolean, isVideo: Boolean, isGroupCall: Boolean, withOtto: Boolean) =
    new CallEvent(if (isVideo) "calling.initiated_video_call" else "calling.initiated_call", isV3, isGroupCall, withOtto)
}

class JoinedCallEvent(name: String, isV3: Boolean, isGroupCall: Boolean, convParticipants: Int, incoming: Boolean, wasUiActiveOnCallStart: Boolean, withOtto: Boolean) extends Event(name) {
  val baseAttributes = Map(
    CALLING_VERSION                   -> (if (isV3) "C3" else "C2"),
    CONVERSATION_TYPE                 -> (if (isGroupCall) "GROUP" else "ONE_TO_ONE"),
    WITH_OTTO                         -> String.valueOf(withOtto),
    CALLING_CONVERSATION_PARTICIPANTS -> String.valueOf(convParticipants),
    CALLING_DIRECTION                 -> (if (incoming) "INCOMING" else "OUTGOING")
  ) ++ (if (incoming) Map(CALLING_APP_IS_ACTIVE -> String.valueOf(wasUiActiveOnCallStart)) else Map.empty)

  override val attributes = baseAttributes
}

object JoinedCallEvent {

  def apply(isV3: Boolean, isVideo: Boolean, isGroupCall: Boolean, convParticipants: Int, incoming: Boolean, wasUiActiveOnCallStart: Boolean, withOtto: Boolean) =
    new JoinedCallEvent(if (isVideo) "calling.joined_video_call" else "calling.joined_call", isV3, isGroupCall, convParticipants, incoming, wasUiActiveOnCallStart, withOtto)
}

object EstablishedCallEvent {

  def apply(isV3: Boolean, isVideo: Boolean, isGroupCall: Boolean, convParticipants: Int, incoming: Boolean, wasUiActiveOnCallStart: Boolean, withOtto: Boolean, setupDuration: Duration) =
    new JoinedCallEvent(if (isVideo) "calling.established_video_call" else "calling.established_call", isV3, isGroupCall, convParticipants, incoming, wasUiActiveOnCallStart, withOtto) {
      override val rangedAttributes = Map (CALLING_SETUP_TIME -> setupDuration.getSeconds.toInt)
    }
}

object EndedCallEvent {

  def apply(isV3: Boolean, isVideo: Boolean, cause: String, isGroupCall: Boolean, convParticipants: Int, maxCallParticipants: Int, incoming: Boolean, wasUiActiveOnCallStart: Boolean, withOtto: Boolean, callDuration: Duration) =
    new JoinedCallEvent(if (isVideo) "calling.ended_video_call" else "calling.ended_call", isV3, isGroupCall, convParticipants, incoming, wasUiActiveOnCallStart, withOtto) {
      override val attributes = baseAttributes ++ Map(
        CALLING_END_REASON            -> cause,
        CALLING_MAX_CALL_PARTICIPANTS -> String.valueOf(maxCallParticipants)
      )
      override val rangedAttributes = Map (VOICE_CALL_DURATION -> callDuration.getSeconds.toInt)
    }
}

object AssetEvent {
  def apply(name: String,
            mime: Mime,
            size: Option[Long] = None,
            conversationType: Option[ConversationType] = None,
            isEphemeral: Option[Boolean] = None,
            exp: Option[EphemeralExpiration] = None,
            uploadDuration: Option[Duration] = None): Event = {
    new Event(name) {
      private var map = Map.empty[Attribute, String]
      private var rMap = Map.empty[RangedAttribute, Int]

      map += (TYPE -> AssetUtils.assetMimeTypeToExtension(mime.str))
      size.foreach { v =>
        map += (FILE_SIZE_BYTES -> v.toString)
        rMap += (FILE_SIZE_MB -> AssetUtils.assetSizeToMB(v))
      }

      conversationType.foreach(v => map += (CONVERSATION_TYPE -> v.toString))
      isEphemeral.foreach { v =>
        map += (IS_EPHEMERAL -> v.toString)
        if (v) exp.foreach(v => map += (EPHEMERAL_EXPIRATION -> v.duration.toSeconds.toString))
      }
      uploadDuration.foreach(v => rMap += (FILE_UPLOAD_DURATION -> (v.toMillis / 1000).toInt))


      override val attributes = map
      override val rangedAttributes = rMap
    }
  }

  def expToString(isEphemeral: Boolean, exp: EphemeralExpiration) = if (isEphemeral) exp.duration().toSeconds.toString else ""

  def initiatedFileUploadEvent(mime: Mime, size: Long, convType: ConversationType, isEphemeral: Boolean, exp: EphemeralExpiration) =
    apply("file.initiated_file_upload", mime, Some(size), Some(convType), Some(isEphemeral), Some(exp))

  def initiatedFileDownloadEvent(mime: Mime, size: Long) =
    apply("file.initiated_file_download", mime, Some(size))

  def successfullyDownloadedFileEvent(mime: Mime, size: Long) =
    apply("file.successfully_downloaded_file", mime, Some(size))

  def failedFileDownloadEvent(mime: Mime) =
    apply("file.failed_file_download", mime)

  def cancelledFileUploadEvent(mime: Mime) =
    apply("file.cancelled_file_upload", mime)

  def successfullyUploadedFileEvent(mime: Mime, size: Long, uploadDuration: Duration) =
    apply("file.successfully_uploaded_file", mime, Some(size), uploadDuration = Some(uploadDuration))

}

case class FailedFileUploadEvent(error: ErrorResponse) extends Event("file.failed_file_upload") {
  override val attributes = Map(
    EXCEPTION_TYPE    -> String.valueOf(error.getCode),
    EXCEPTION_DETAILS -> s"${error.getLabel}, ${error.getMessage}"
  )
}

