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
import com.waz.ZLog.ImplicitTag._
import com.waz.model.ConvId
import com.waz.service.assets.AssetService.RawAssetInput.UriInput
import com.waz.service.conversation.ConversationsUiService
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.wrappers.URI
import com.waz.zclient.Intents._
import com.waz.zclient.common.controllers.SharingController._
import com.waz.zclient.utils.ContextUtils.showWifiWarningDialog
import com.waz.zclient.{Injectable, Injector, WireContext}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class SharingController(implicit injector: Injector, wContext: WireContext, eventContext: EventContext) extends Injectable{

  private implicit val dispatcher = new SerialDispatchQueue(name = "SharingController")

  val sharableContent     = Signal(Option.empty[SharableContent])
  val targetConvs         = Signal(Seq.empty[ConvId])
  val ephemeralExpiration = Signal(Option.empty[FiniteDuration])

  val sendEvent = EventStream[(SharableContent, Seq[ConvId], Option[FiniteDuration])]()

  def onContentShared(activity: Activity, convs: Seq[ConvId]): Unit = {
    targetConvs ! convs
    Option(activity).foreach(_.startActivity(SharingIntent(wContext)))
  }

  def sendContent(activity: Activity): Future[Seq[ConvId]] = {
    def send(content: SharableContent, convs: Seq[ConvId], expiration: Option[FiniteDuration]) = {
      sendEvent ! (content, convs, expiration)
      inject[Signal[ConversationsUiService]].head.flatMap { convsUi =>
        content match {
          case TextContent(t) =>
            convsUi.sendTextMessages(convs, t, Nil, expiration)
          case uriContent =>
            convsUi.sendAssetMessages(convs, uriContent.uris.map(UriInput), (s: Long) => showWifiWarningDialog(s)(activity), expiration)
        }
      }
    }

    for {
      Some(content) <- sharableContent.head
      convs         <- targetConvs.head
      expiration    <- ephemeralExpiration.head
      _             <- send(content, convs, expiration)
      _             = resetContent()
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

  def publishImageContent(uris: java.util.List[URI]): Unit =
    this.sharableContent ! Some(ImageContent(uris.asScala))

  def publishFileContent(uris: java.util.List[URI]): Unit =
    this.sharableContent ! Some(FileContent(uris.asScala))
}

object SharingController {
  sealed trait SharableContent {
    val uris: Seq[URI]
  }

  case class TextContent(text: String) extends SharableContent { override val uris = Seq.empty }

  case class FileContent(uris: Seq[URI]) extends SharableContent

  case class ImageContent(uris: Seq[URI]) extends SharableContent
}
