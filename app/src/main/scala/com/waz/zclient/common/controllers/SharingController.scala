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
package com.waz.zclient.common.controllers

import android.app.Activity
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConvId
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.wrappers.{URI => URIWrapper}
import com.waz.zclient.Intents._
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.{Injectable, Injector, WireContext}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class SharingController(implicit injector: Injector, wContext: WireContext, eventContext: EventContext)
  extends Injectable with DerivedLogTag {

  import SharingController._

  private implicit val dispatcher = new SerialDispatchQueue(name = "SharingController")

  val sharableContent     = Signal(Option.empty[SharableContent])
  val targetConvs         = Signal(Seq.empty[ConvId])
  val ephemeralExpiration = Signal(Option.empty[FiniteDuration])

  def onContentShared(activity: Activity, convs: Seq[ConvId]): Unit = {
    targetConvs ! convs
    Option(activity).foreach(_.startActivity(SharingIntent(wContext)))
  }

  def sendContent(activity: Activity): Future[Seq[ConvId]] = {
    def send(content: SharableContent, convs: Seq[ConvId], expiration: Option[FiniteDuration]) =
      content match {
        case NewContent     =>
          inject[ConversationController].switchConversation(convs.head)
        case TextContent(t) =>
          inject[ConversationController].sendTextMessage(convs, t, Nil, None, Some(expiration))
        case uriContent     =>
          val uris = uriContent.uris.map(URIWrapper.toJava)
          inject[ConversationController].sendAssetMessages(uris, activity, Some(expiration), convs)
      }

    for {
      Some(content) <- sharableContent.head
      convs         <- targetConvs.head
      expiration    <- ephemeralExpiration.head
      _             <- send(content, convs, expiration)
      _             =  resetContent()
    } yield convs
  }

  def getSharedText(convId: ConvId): String = sharableContent.currentValue.flatten match {
    case Some(TextContent(t)) if targetConvs.currentValue.exists(_.contains(convId)) => t
    case _ => null
  }

  private def resetContent() = {
    sharableContent     ! None
    targetConvs         ! Seq.empty
    ephemeralExpiration ! None
  }

  def publishTextContent(text: String): Unit =
    this.sharableContent ! Some(TextContent(text))
}

object SharingController {

  sealed trait SharableContent {
    val uris: Seq[URIWrapper]
  }

  case object NewContent extends SharableContent { override val uris = Seq.empty }

  case class TextContent(text: String) extends SharableContent { override val uris = Seq.empty }

  case class FileContent(uris: Seq[URIWrapper]) extends SharableContent

  case class ImageContent(uris: Seq[URIWrapper]) extends SharableContent
}
