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

import java.io.{File, FileOutputStream}
import java.net.URI

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.content.{Context, Intent}
import android.net.Uri
import android.os.Environment
import android.support.v7.app.AppCompatDialog
import android.text.TextUtils
import android.util.TypedValue
import android.view.{Gravity, View}
import android.widget.{TextView, Toast}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Message
import com.waz.content.UserPreferences.DownloadImagesAlways
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.service.assets.GlobalRecordAndPlayService
import com.waz.service.assets.GlobalRecordAndPlayService.{AssetMediaKey, Content, UnauthenticatedContent}
import com.waz.service.assets2.Asset.{Audio, General, Image, Video}
import com.waz.service.assets2.{Asset, AssetService, AssetStatus, GeneralAsset}
import com.waz.service.messages.MessagesService
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.wrappers.{AndroidURIUtil, URI => URIWrapper}
import com.waz.utils.{IoUtils, returning}
import com.waz.zclient.controllers.drawing.IDrawingController.DrawingMethod
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.drawing.DrawingFragment.Sketch
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.notifications.controllers.ImageNotificationsController
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, R}
import com.waz.znet2.http.HttpClient.Progress
import org.threeten.bp.Duration

import scala.collection.immutable.ListSet
import scala.concurrent.Future
import scala.util.Success

class AssetsController(implicit context: Context, inj: Injector, ec: EventContext) extends Injectable { controller =>
  import AssetsController._
  import Threading.Implicits.Ui

  val zms: Signal[ZMessaging] = inject[Signal[ZMessaging]]
  val assets: Signal[AssetService] = zms.map(_.assetService)
  val permissions: Signal[PermissionsService] = zms.map(_.permissions)
  val messages: Signal[MessagesService] = zms.map(_.messages)

  lazy val messageActionsController: MessageActionsController = inject[MessageActionsController]
  lazy val singleImage: ISingleImageController = inject[ISingleImageController]
  lazy val screenController: ScreenController = inject[ScreenController]
  lazy val imageNotifications: ImageNotificationsController = inject[ImageNotificationsController]

  //TODO make a preference controller for handling UI preferences in conjunction with SE preferences
  val downloadsAlwaysEnabled =
    zms.flatMap(_.userPrefs.preference(DownloadImagesAlways).signal).disableAutowiring()

  val onFileOpened = EventStream[AssetData]()
  val onFileSaved = EventStream[AssetData]()
  val onVideoPlayed = EventStream[AssetData]()
  val onAudioPlayed = EventStream[AssetData]()

  messageActionsController.onMessageAction
    .collect { case (MessageAction.OpenFile, msg) => msg.assetId } {
      case Some(id) => openFile(id)
      case _ =>
    }

  def assetSignal(assetId: Signal[AssetIdGeneral]): Signal[GeneralAsset] =
    for {
      a <- assets
      id <- assetId
      status <- a.assetSignal(id)
    } yield status

  def assetStatusSignal(assetId: Signal[AssetIdGeneral]): Signal[(AssetStatus, Option[Progress])] =
    for {
      a <- assets
      id <- assetId
      status <- a.assetStatusSignal(id)
    } yield status

  def assetStatusSignal(assetId: AssetIdGeneral): Signal[(AssetStatus, Option[Progress])] =
    assetStatusSignal(Signal.const(assetId))

  def downloadProgress(idGeneral: AssetIdGeneral): Signal[Progress] = idGeneral match {
    case id: InProgressAssetId => assets.flatMap(_.downloadProgress(id))
    case _ => Signal.empty
  }

  def uploadProgress(idGeneral: AssetIdGeneral): Signal[Progress] = idGeneral match {
    case id: RawAssetId => assets.flatMap(_.uploadProgress(id))
    case _ => Signal.empty
  }

  def cancelUpload(idGeneral: AssetIdGeneral): Unit = idGeneral match {
    case id: RawAssetId => assets.currentValue.foreach(_.cancelUpload(id))
    case _ => ()
  }

  def cancelDownload(idGeneral: AssetIdGeneral): Unit = idGeneral match {
    case id: InProgressAssetId => assets.currentValue.foreach(_.cancelDownload(id))
    case _ => ()
  }

  def retry(m: MessageData) =
    if (m.state == Message.Status.FAILED || m.state == Message.Status.FAILED_READ) messages.currentValue.foreach(_.retryMessageSending(m.convId, m.id))

  def getPlaybackControls(asset: Signal[GeneralAsset]): Signal[PlaybackControls] = asset.flatMap { a =>
    (a.details, a.id) match {
      case (_: Audio, id: AssetId) => Signal.const(new PlaybackControls(id, controller))
      case _ => Signal.empty[PlaybackControls]
    }
  }

  // display full screen image for given message
  def showSingleImage(msg: MessageData, container: View) =
    if (!(msg.isEphemeral && msg.expired)) {
      verbose(s"message loaded, opening single image for ${msg.id}")
      singleImage.setViewReferences(container)
      singleImage.showSingleImage(msg.id.str)
    }

  //FIXME: don't use java api
  def openDrawingFragment(id: AssetId, drawingMethod: DrawingMethod): Unit =
    screenController.showSketch ! Sketch.asset(id, drawingMethod)

  def openFile(idGeneral: AssetIdGeneral): Unit = idGeneral match {
    case id: AssetId =>
      assets.head.flatMap(_.getAsset(id)).foreach { asset =>
        asset.details match {
          case _: Video =>
            //onVideoPlayed ! asset
            context.startActivity(getOpenFileIntent(createAndroidAssetUri(id), asset.mime.orDefault.str))
          case _ =>
            showOpenFileDialog(createAndroidAssetUri(id), asset)
        }
      }
    case _ =>
    // TODO: display error
  }

  def showOpenFileDialog(uri: Uri, asset: Asset[General]): Unit = {
    val intent = getOpenFileIntent(uri, asset.mime.orDefault.str)
    val fileCanBeOpened = fileTypeCanBeOpened(context.getPackageManager, intent)

    //TODO tidy up
    //TODO there is also a weird flash or double-dialog issue when you click outside of the dialog
    val dialog = new AppCompatDialog(context)
    dialog.setTitle(asset.name)
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
//          onFileOpened ! asset
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
//        onFileSaved ! asset
        dialog.dismiss()
        saveToDownloads(asset)
      }
    })

    dialog.show()
  }

  private def saveAssetContentToFile(asset: Asset[General], targetDir: File): Future[File] = {
    for {
      p <- permissions.head
      _ <- p.ensurePermissions(ListSet(WRITE_EXTERNAL_STORAGE))
      a <- assets.head
      is <- a.loadContent(asset).future
      targetFile = getTargetFile(asset, targetDir)
      _ = IoUtils.copy(is, new FileOutputStream(targetFile))
    } yield targetFile
  }

  def saveImageToGallery(asset: Asset[Image]): Unit = {
    val targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    saveAssetContentToFile(asset, targetDir).onComplete {
      case Success(file) =>
        val uri = URIWrapper.fromFile(file)
        imageNotifications.showImageSavedNotification(asset.id, uri)
        Toast.makeText(context, R.string.message_bottom_menu_action_save_ok, Toast.LENGTH_SHORT).show()
      case _ =>
        Toast.makeText(context, R.string.content__file__action__save_error, Toast.LENGTH_SHORT).show()
    }
  }

  def saveToDownloads(asset: Asset[General]): Unit = {
    val targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    saveAssetContentToFile(asset, targetDir).onComplete {
      case Success(file) =>
        val uri = URIWrapper.fromFile(file)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE).asInstanceOf[DownloadManager]
        downloadManager.addCompletedDownload(
          asset.name,
          asset.name,
          false,
          asset.mime.orDefault.str,
          uri.getPath,
          asset.size,
          true)
        Toast.makeText(context, R.string.content__file__action__save_completed, Toast.LENGTH_SHORT).show()
        context.sendBroadcast(returning(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE))(_.setData(URIWrapper.unwrap(uri))))
      case _ =>
    }(Threading.Ui)
  }

}

object AssetsController {

  val UriAssetAuthority = "WireAsset"

  def createAssetUri(assetId: AssetId): URI = URI.create(createAndroidAssetUri(assetId).toString)

  def createAndroidAssetUri(assetId: AssetId): Uri = {
    new Uri.Builder()
      .authority(UriAssetAuthority)
      .appendPath(assetId.str)
      .build()
  }

  def getTargetFile(asset: Asset[General], directory: File): File = {
    def file(prefix: String = "") = new File(
      directory,
      s"${if (prefix.isEmpty) "" else prefix + "_"}${asset.name}.${asset.mime.extension}"
    )

    val baseFile = file()
    if (!baseFile.exists()) baseFile
    else {
      (1 to 20).map(i => file(i.toString)).find(!_.exists())
        .getOrElse(file(AESKey.random.str))
    }
  }

  class PlaybackControls(assetId: AssetId, controller: AssetsController) {
    val rAndP = controller.zms.map(_.global.recordingAndPlayback)

    val isPlaying = rAndP.flatMap(rP => rP.isPlaying(AssetMediaKey(assetId)))
    val playHead = rAndP.flatMap(rP => rP.playhead(AssetMediaKey(assetId)))

    private def rPAction(f: (GlobalRecordAndPlayService, AssetMediaKey, Content, Boolean) => Unit): Unit = {
      for {
        rP <- rAndP.currentValue
        isPlaying <- isPlaying.currentValue
      } {
        f(rP, AssetMediaKey(assetId), UnauthenticatedContent(AndroidURIUtil.parse(createAssetUri(assetId).toString)), isPlaying)
      }
    }

    def playOrPause() = rPAction { case (rP, key, content, playing) => if (playing) rP.pause(key) else rP.play(key, content) }

    def setPlayHead(duration: Duration) = rPAction { case (rP, key, content, playing) => rP.setPlayhead(key, content, duration) }
  }

  def getOpenFileIntent(uri: Uri, mimeType: String): Intent = {
    returning(new Intent) { i =>
      i.setAction(Intent.ACTION_VIEW)
      i.setDataAndType(uri, mimeType)
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  def fileTypeCanBeOpened(manager: PackageManager, intent: Intent): Boolean =
    manager.queryIntentActivities(intent, 0).size > 0
}
