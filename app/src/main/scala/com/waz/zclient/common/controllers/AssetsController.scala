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

import android.app.DownloadManager
import android.content.pm.PackageManager
import android.content.{Context, Intent}
import android.support.v7.app.AppCompatDialog
import android.text.TextUtils
import android.util.TypedValue
import android.view.{Gravity, View}
import android.widget.{TextView, Toast}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Message
import com.waz.content.UserPreferences.DownloadImagesAlways
import com.waz.model.{AssetData, AssetId, MessageData, Mime}
import com.waz.service.ZMessaging
import com.waz.service.assets.GlobalRecordAndPlayService
import com.waz.service.assets.GlobalRecordAndPlayService.{AssetMediaKey, Content, UnauthenticatedContent}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.returning
import com.waz.utils.wrappers.{AndroidURIUtil, URI}
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.controllers.drawing.IDrawingController.DrawingMethod
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, R}
import org.threeten.bp.Duration

import scala.PartialFunction._
import scala.util.Success

class AssetsController(implicit context: Context, inj: Injector, ec: EventContext) extends Injectable { controller =>
  import AssetsController._
  import Threading.Implicits.Ui

  val zms = inject[Signal[ZMessaging]]
  val assets = zms.map(_.assets)

  val messages = zms.map(_.messages)

  lazy val messageActionsController = inject[MessageActionsController]
  lazy val singleImage              = inject[ISingleImageController]
  lazy val drawingController        = inject[IDrawingController]

  //TODO make a preference controller for handling UI preferences in conjunction with SE preferences
  val downloadsAlwaysEnabled =
    zms.flatMap(_.userPrefs.preference(DownloadImagesAlways).signal).disableAutowiring()

  val onFileOpened = EventStream[AssetData]()
  val onFileSaved = EventStream[AssetData]()
  val onVideoPlayed = EventStream[AssetData]()
  val onAudioPlayed = EventStream[AssetData]()

  messageActionsController.onMessageAction {
    case (MessageAction.OpenFile, msg) =>
      zms.head.flatMap(_.assetsStorage.get(msg.assetId)) foreach {
        case Some(asset) => openFile(asset)
        case None => // TODO: show error
      }
    case _ => // ignore
  }

  def assetSignal(mes: Signal[MessageData]) = mes.flatMap(m => assets.flatMap(_.assetSignal(m.assetId)))

  def assetSignal(assetId: AssetId) = assets.flatMap(_.assetSignal(assetId))

  def downloadProgress(id: AssetId) = assets.flatMap(_.downloadProgress(id))

  def uploadProgress(id: AssetId) = assets.flatMap(_.uploadProgress(id))

  def cancelUpload(m: MessageData) = assets.currentValue.foreach(_.cancelUpload(m.assetId, m.id))

  def cancelDownload(m: MessageData) = assets.currentValue.foreach(_.cancelDownload(m.assetId))

  def retry(m: MessageData) = if (m.state == Message.Status.FAILED || m.state == Message.Status.FAILED_READ) messages.currentValue.foreach(_.retryMessageSending(m.convId, m.id))

  def getPlaybackControls(asset: Signal[AssetData]): Signal[PlaybackControls] = asset.flatMap { a =>
    if (cond(a.mime.orDefault) { case Mime.Audio() => true }) Signal.const(new PlaybackControls(a.id, controller))
    else Signal.empty[PlaybackControls]
  }

  // display full screen image for given message
  def showSingleImage(msg: MessageData, container: View) =
    if (!(msg.isEphemeral && msg.expired)) {
      verbose(s"message loaded, opening single image for ${msg.id}")
      singleImage.setViewReferences(container)
      singleImage.showSingleImage(msg.id.str)
    }

  //FIXME: don't use java api
  def openDrawingFragment(msg: MessageData, drawingMethod: DrawingMethod) =
    drawingController.showDrawing(ZMessaging.currentUi.images.getImageAsset(msg.assetId), IDrawingController.DrawingDestination.SINGLE_IMAGE_VIEW, drawingMethod)

  def openFile(asset: AssetData) =
    assets.head.flatMap(_.getContentUri(asset.id)) foreach {
      case Some(uri) =>
        asset match {
         case AssetData.IsVideo() =>
           onVideoPlayed ! asset
           context.startActivity(getOpenFileIntent(uri, asset.mime.orDefault.str))
         case _ =>
           showOpenFileDialog(uri, asset)
        }
      case None =>
      // TODO: display error
    }

  def showOpenFileDialog(uri: URI, asset: AssetData) = {
    val intent = getOpenFileIntent(uri, asset.mime.orDefault.str)
    val fileCanBeOpened = fileTypeCanBeOpened(context.getPackageManager, intent)

    //TODO tidy up
    //TODO there is also a weird flash or double-dialog issue when you click outside of the dialog
    val dialog = new AppCompatDialog(context)
    asset.name.foreach(dialog.setTitle)
    dialog.setContentView(R.layout.file_action_sheet_dialog)

    val title = dialog.findViewById(R.id.title).asInstanceOf[TextView]
    title.setEllipsize(TextUtils.TruncateAt.MIDDLE)
    title.setTypeface(TypefaceUtils.getTypeface(getString(R.string.wire__typeface__medium)))
    title.setTextSize(TypedValue.COMPLEX_UNIT_PX, getDimenPx(R.dimen.wire__text_size__regular))
    title.setGravity(Gravity.CENTER)

    val openButton = dialog.findViewById(R.id.ttv__file_action_dialog__open).asInstanceOf[TextView]
    val noAppFoundLabel = dialog.findViewById(R.id.ttv__file_action_dialog__open__no_app_found).asInstanceOf[View]
    val saveButton = dialog.findViewById(R.id.ttv__file_action_dialog__save).asInstanceOf[View]

    if (fileCanBeOpened) {
      noAppFoundLabel.setVisibility(View.GONE)
      openButton.setAlpha(1f)
      openButton.setOnClickListener(new View.OnClickListener() {
        def onClick(v: View) = {
          onFileOpened ! asset
          context.startActivity(intent)
          dialog.dismiss()
        }
      })
    }
    else {
      noAppFoundLabel.setVisibility(View.VISIBLE)
      val disabledAlpha = getResourceFloat(R.dimen.button__disabled_state__alpha)
      openButton.setAlpha(disabledAlpha)
    }

    saveButton.setOnClickListener(new View.OnClickListener() {
      def onClick(v: View) = {
        onFileSaved ! asset
        dialog.dismiss()
        saveToDownloads(asset)
      }
    })

    dialog.show()
  }

  def saveToDownloads(asset: AssetData) =
    assets.head.flatMap(_.saveAssetToDownloads(asset)).onComplete {
      case Success(Some(file)) =>
        val uri = URI.fromFile(file)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE).asInstanceOf[DownloadManager]
        downloadManager.addCompletedDownload(asset.name.get, asset.name.get, false, asset.mime.orDefault.str, uri.getPath, asset.sizeInBytes, true)
        Toast.makeText(context, R.string.content__file__action__save_completed, Toast.LENGTH_SHORT).show()
        context.sendBroadcast(returning(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE))(_.setData(URI.unwrap(uri))))
      case _ =>
    }(Threading.Ui)
}

object AssetsController {

  class PlaybackControls(assetId: AssetId, controller: AssetsController) {
    val rAndP = controller.zms.map(_.global.recordingAndPlayback)

    val isPlaying = rAndP.flatMap(rP => rP.isPlaying(AssetMediaKey(assetId)))
    val playHead = rAndP.flatMap(rP => rP.playhead(AssetMediaKey(assetId)))

    private def rPAction(f: (GlobalRecordAndPlayService, AssetMediaKey, Content, Boolean) => Unit): Unit = {
      for {
        as <- controller.assets.currentValue
        rP <- rAndP.currentValue
        isPlaying <- isPlaying.currentValue
      } {
        as.getContentUri(assetId).foreach {
          case Some(uri) => f(rP, AssetMediaKey(assetId), UnauthenticatedContent(uri), isPlaying)
          case None =>
        }(Threading.Background)
      }
    }

    def playOrPause() = rPAction { case (rP, key, content, playing) => if (playing) rP.pause(key) else rP.play(key, content) }

    def setPlayHead(duration: Duration) = rPAction { case (rP, key, content, playing) => rP.setPlayhead(key, content, duration) }
  }

  def getOpenFileIntent(uri: URI, mimeType: String): Intent = {
    returning(new Intent) { i =>
      i.setAction(Intent.ACTION_VIEW)
      i.setDataAndType(AndroidURIUtil.unwrap(uri), mimeType)
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  def fileTypeCanBeOpened(manager: PackageManager, intent: Intent): Boolean =
    manager.queryIntentActivities(intent, 0).size > 0
}
