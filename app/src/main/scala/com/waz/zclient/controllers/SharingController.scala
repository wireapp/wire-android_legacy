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
package com.waz.zclient.controllers

import android.app.Activity
import android.content.DialogInterface
import android.support.v7.app.AlertDialog
import android.text.format.Formatter
import com.waz.ZLog.ImplicitTag._
import com.waz.api._
import com.waz.model.ConvId
import com.waz.service.ZMessaging
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.RichFuture
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.wrappers.URI
import com.waz.zclient.Intents._
import com.waz.zclient.controllers.SharingController.{FileContent, ImageContent, SharableContent, TextContent}
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{Injectable, Injector, R, WireContext}

import scala.collection.JavaConverters._
import scala.concurrent.Future

class SharingController(implicit injector: Injector, wContext: WireContext, eventContext: EventContext) extends Injectable{

  private implicit val dispatcher = new SerialDispatchQueue(name = "SharingController")

  private lazy val zms = inject[Signal[ZMessaging]]

  val sharableContent     = Signal(Option.empty[SharableContent])
  val targetConvs         = Signal(Set.empty[ConvId])
  val ephemeralExpiration = Signal(EphemeralExpiration.NONE)

  val sendEvent = EventStream[(SharableContent, Set[ConvId], EphemeralExpiration)]()

  private def assetErrorHandler(activity: Activity): MessageContent.Asset.ErrorHandler = new MessageContent.Asset.ErrorHandler() {
    def noWifiAndFileIsLarge(sizeInBytes: Long, net: NetworkMode, answer: MessageContent.Asset.Answer): Unit = {
      if (activity == null) {
        answer.ok()
        return
      }
      val dialog: AlertDialog =
        ViewUtils.showAlertDialog(activity,
          R.string.asset_upload_warning__large_file__title,
          R.string.asset_upload_warning__large_file__message_default,
          R.string.asset_upload_warning__large_file__button_accept,
          R.string.asset_upload_warning__large_file__button_cancel,
          new DialogInterface.OnClickListener() {
            def onClick(dialog: DialogInterface, which: Int): Unit = {
              answer.ok()
            }
          }, new DialogInterface.OnClickListener() {
            def onClick(dialog: DialogInterface, which: Int): Unit = {
              answer.cancel()
            }
          })
      dialog.setCancelable(false)
      if (sizeInBytes > 0) {
        val fileSize: String = Formatter.formatFileSize(activity, sizeInBytes)
        dialog.setMessage(activity.getString(R.string.asset_upload_warning__large_file__message, fileSize))
      }
    }
  }

  def onContentShared(activity: Activity, convs: Set[ConvId]): Unit = {
    targetConvs ! convs
    Option(activity).foreach(_.startActivity(SharingIntent(wContext)))
  }

  def sendContent(activity: Activity): Future[Unit] = {
    def send(content: SharableContent, convs: Set[ConvId], expiration: EphemeralExpiration): Future[Boolean] = {
      sendEvent ! (content, convs, expiration)
      content match {
        case TextContent(t) =>
          zms.head.flatMap(z => RichFuture.traverseSequential(convs.toSeq){ convId =>
            z.convsUi.setEphemeral(convId, expiration).flatMap(_ =>
              z.convsUi.sendMessage(convId, new MessageContent.Text(t)))
          }).map(_ => true)

        case uriContent =>
          RichFuture.traverseSequential(convs.toSeq) { conv =>
            RichFuture.traverseSequential(uriContent.uris) { uri =>
              zms.head.flatMap(z =>
                z.convsUi.setEphemeral(conv, expiration).flatMap(_ =>
                  z.convsUi.sendMessage(conv, new MessageContent.Asset(AssetFactory.fromContentUri(uri), assetErrorHandler(activity)))))
            }
          }.map (_ => true)
      }
    }

    for {
      Some(content) <- sharableContent.head
      convs         <- targetConvs.head
      expiration    <- ephemeralExpiration.head
      sent          <- send(content, convs, expiration)
    } yield if (sent) resetContent()
  }

  //For Java
  private def isSharing(convId: String): Boolean = targetConvs.currentValue.exists(_.contains(ConvId(convId)))

  def getSharedText(convId: String): String = sharableContent.currentValue.flatten match {
    case Some(TextContent(t)) if isSharing(convId) => t
    case _ => null
  }

  private def resetContent() = {
    sharableContent.publish(None, dispatcher)
    targetConvs.publish(Set.empty, dispatcher)
    ephemeralExpiration.publish(EphemeralExpiration.NONE, dispatcher)
  }

  def clearSharingFor(conv: IConversation) = if (conv != null) {
    targetConvs.currentValue.foreach { convs =>
      if (convs.contains(ConvId(conv.getId))) resetContent()
    }
  }

  def publishTextContent(text: String): Unit =
    this.sharableContent ! Some(TextContent(text))

  def publishImageContent(uris: java.util.List[URI]): Unit =
    this.sharableContent ! Some(ImageContent(uris.asScala))

  def publishFileContent(uris: java.util.List[URI]): Unit =
    this.sharableContent ! Some(FileContent(uris.asScala))
}

object SharingController {
  trait SharableContent {
    val uris: Seq[URI]
  }

  case class TextContent(text: String) extends SharableContent { override val uris = Seq.empty }

  case class FileContent(uris: Seq[URI]) extends SharableContent

  case class ImageContent(uris: Seq[URI]) extends SharableContent
}
