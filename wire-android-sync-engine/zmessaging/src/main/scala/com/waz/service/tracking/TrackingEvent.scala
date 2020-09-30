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
package com.waz.service.tracking

import com.waz.model.Mime
import com.waz.service.tracking.TrackingServiceImpl.CountlyEventProperties

trait TrackingEvent {
  val name: String
  val segments: CountlyEventProperties
}

case class ContributionEvent(action: ContributionEvent.Action,
                             segments: CountlyEventProperties) extends TrackingEvent {
  override val name = "contributed"
}

object ContributionEvent {

  case class Action(name: String)

  object Action {
    lazy val Text = Action("text")
    lazy val Ping = Action("ping")
    lazy val AudioCall = Action("audio_call")
    lazy val VideoCall = Action("video_call")
    lazy val Photo = Action("photo")
    lazy val Audio = Action("audio")
    lazy val Video = Action("video")
    lazy val File = Action("file")
    lazy val Location = Action("location")
  }

  def fromMime(mime: Mime) = {
    import Action._
    mime match {
      case Mime.Image() => Photo
      case Mime.Audio() => Audio
      case Mime.Video() => Video
      case _ => File
    }
  }
}

case class CallingEvent(partName: String,
                        segments: CountlyEventProperties) extends TrackingEvent {

  override lazy val name = s"calling.${partName}_call"
}

case class MessageDecryptionFailedEvent(segments: CountlyEventProperties) extends TrackingEvent {
  override val name = "e2ee.failed_message_decryption"
}

case class ScreenShareEvent(segments: CountlyEventProperties) extends TrackingEvent {
  override lazy val name = "calling.screen_share"
}

case class AppOpenEvent(segments: CountlyEventProperties) extends TrackingEvent {
  override lazy val name = "app.open"
}

object GroupConversationEvent {
  case class Method(str: String)
  object ConversationDetails extends Method("conversation_details")
  object StartUi extends Method("start_ui")
}

