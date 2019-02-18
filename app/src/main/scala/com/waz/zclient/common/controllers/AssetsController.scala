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
import com.waz.content.MessagesStorage
import com.waz.content.UserPreferences.DownloadImagesAlways
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.service.assets.GlobalRecordAndPlayService
import com.waz.service.assets.GlobalRecordAndPlayService.{AssetMediaKey, Content, MediaKey, UnauthenticatedContent}
import com.waz.service.assets2.Asset.{Audio, General, Image, Video}
import com.waz.service.assets2.{AssetStatus, _}
import com.waz.service.messages.MessagesService
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.wrappers.{URI => URIWrapper}
import com.waz.utils.{IoUtils, returning, sha2}
import com.waz.zclient.controllers.drawing.IDrawingController.DrawingMethod
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.drawing.DrawingFragment.Sketch
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.notifications.controllers.ImageNotificationsController
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ExternalFileSharing
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
  val messagesStorage: Signal[MessagesStorage] = zms.map(_.messagesStorage)

  lazy val messageActionsController: MessageActionsController = inject[MessageActionsController]
  lazy val singleImage: ISingleImageController = inject[ISingleImageController]
  lazy val screenController: ScreenController = inject[ScreenController]
  lazy val imageNotifications: ImageNotificationsController = inject[ImageNotificationsController]
  private lazy val externalFileSharing = inject[ExternalFileSharing]

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

  def assetSignal(assetId: AssetIdGeneral): Signal[GeneralAsset] =
    for {
      a <- assets
      status <- a.assetSignal(assetId)
    } yield status

  def assetSignal(assetId: Option[AssetIdGeneral]): Signal[Option[GeneralAsset]] =
    assetId.fold(Signal.const(Option.empty[GeneralAsset]))(aid => assetSignal(aid).map(Option(_)))

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

  def assetPreviewId(assetId: Signal[AssetIdGeneral]): Signal[Option[AssetIdGeneral]] =
    assetSignal(assetId).map {
      case u: UploadAsset[_] => u.preview match {
        case RawPreviewUploaded(aId) => Option(aId)
        case RawPreviewNotUploaded(aId) => Option(aId)
        case _ => Option.empty[AssetIdGeneral]
      }
      case d: DownloadAsset => d.preview
      case a: Asset[_] => a.preview
      case _ => Option.empty[AssetIdGeneral]
    }

  def assetPreviewId(assetId: AssetIdGeneral): Signal[Option[AssetIdGeneral]] =
    assetPreviewId(Signal.const(assetId))

  def downloadProgress(idGeneral: AssetIdGeneral): Signal[Progress] = idGeneral match {
    case id: DownloadAssetId => assets.flatMap(_.downloadProgress(id))
    case _ => Signal.empty
  }

  def uploadProgress(idGeneral: AssetIdGeneral): Signal[Progress] = idGeneral match {
    case id: UploadAssetId => assets.flatMap(_.uploadProgress(id))
    case _ => Signal.empty
  }

  def cancelUpload(idGeneral: AssetIdGeneral, message: MessageData): Unit = idGeneral match {
    case id: UploadAssetId =>
      assets.currentValue.foreach(_.cancelUpload(id, message))
    case _ => ()
  }

  def cancelDownload(idGeneral: AssetIdGeneral): Unit = idGeneral match {
    case id: DownloadAssetId => assets.currentValue.foreach(_.cancelDownload(id))
    case _ => ()
  }

  def retry(m: MessageData) =
    if (m.state == Message.Status.FAILED || m.state == Message.Status.FAILED_READ) messages.currentValue.foreach(_.retryMessageSending(m.convId, m.id))

    def getPlaybackControls(asset: Signal[GeneralAsset]): Signal[PlaybackControls] = asset.flatMap { a =>
    (a.details, a) match {
      case (_: Audio, audioAsset: Asset[_]) =>

        val file = new File(context.getCacheDir, s"${audioAsset.id.str}.mp4")
        Signal.future((if (!file.exists()) {
          file.createNewFile()
          assets.head.flatMap(_.loadContent(audioAsset)).map { is =>
            val os = new FileOutputStream(file)
            IoUtils.copy(is, os)
            is.close()
          }
        } else {
          Future.successful(())
        }).map { _ =>
          new PlaybackControls(audioAsset.id, URIWrapper.fromFile(file),
            zms.map(_.global.recordingAndPlayback))
        })
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

      assetForSharing(id).foreach { case AssetForShare(asset, file) =>
        asset.details match {
          case _: Video =>
            context.startActivity(getOpenFileIntent(externalFileSharing.getUriForFile(file), asset.mime.orDefault.str))
          case _ =>
            showOpenFileDialog(externalFileSharing.getUriForFile(file), asset)
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

  def assetForSharing(id: AssetId): Future[AssetForShare] = {

    def getSharedFilename(asset: Asset[General]): String =
      if (asset.name.nonEmpty)
        asset.name
      else
        s"${sha2(asset.id.str).take(6)}.${asset.mime.extension}"

    for {
      assets <- zms.head.map(_.assetService)
      asset <- assets.getAsset(id)
      is <- assets.loadContent(asset)
      file = new File(context.getExternalCacheDir, getSharedFilename(asset))
      _ = IoUtils.copy(is, file)
      _ = is.close()
    } yield AssetForShare(asset, file)
  }

}

object AssetsController {

  case class AssetForShare(asset: Asset[General], file: File)

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

  class PlaybackControls(assetId: AssetId, fileUri: URIWrapper, rAndP: Signal[GlobalRecordAndPlayService]) {

    val isPlaying = rAndP.flatMap(rP => rP.isPlaying(AssetMediaKey(assetId)))
    val playHead = rAndP.flatMap(rP => rP.playhead(AssetMediaKey(assetId)))

    private def rPAction(f: (GlobalRecordAndPlayService, MediaKey, Content, Boolean) => Unit): Unit = {
      for {
        rP <- rAndP.currentValue
        isPlaying <- isPlaying.currentValue
      } {
        f(rP, AssetMediaKey(assetId), UnauthenticatedContent(fileUri), isPlaying)
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
