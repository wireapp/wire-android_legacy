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

import java.io.File

import android.app.DownloadManager
import android.content.pm.PackageManager
import android.content.{Context, Intent}
import android.net.Uri
import android.os.Environment
import android.text.TextUtils
import android.util.TypedValue
import android.view.{Gravity, View}
import android.widget.{TextView, Toast}
import androidx.appcompat.app.AppCompatDialog
import com.waz.content.MessagesStorage
import com.waz.content.UserPreferences.DownloadImagesAlways
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service
import com.waz.service.ZMessaging
import com.waz.service.assets.{Asset, AssetService, DownloadAsset, GeneralAsset, GlobalRecordAndPlayService, PreviewNotUploaded, PreviewUploaded, UploadAsset}
import com.waz.service.assets.GlobalRecordAndPlayService.{AssetMediaKey, Content, MediaKey, UnauthenticatedContent}
import com.waz.service.assets.Asset.{Audio, Video}
import com.waz.service.messages.MessagesService
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.wrappers.{URI => URIWrapper}
import com.waz.utils.{IoUtils, returning, sha2}
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.log.LogUI._
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

class AssetsController(implicit context: Context, inj: Injector, ec: EventContext)
  extends Injectable with DerivedLogTag { controller =>

  import AssetsController._
  import Threading.Implicits.Ui

  val zms: Signal[ZMessaging] = inject[Signal[ZMessaging]]
  val assets: Signal[AssetService] = zms.map(_.assetService)
  val permissions: Signal[PermissionsService] = zms.map(_.permissions)
  val messages: Signal[MessagesService] = zms.map(_.messages)
  val messagesStorage: Signal[MessagesStorage] = zms.map(_.messagesStorage)
  val openVideoProgress = Signal(false)

  lazy val messageActionsController: MessageActionsController = inject[MessageActionsController]
  lazy val singleImage: ISingleImageController = inject[ISingleImageController]
  lazy val screenController: ScreenController = inject[ScreenController]
  lazy val imageNotifications: ImageNotificationsController = inject[ImageNotificationsController]
  private lazy val externalFileSharing = inject[ExternalFileSharing]

  //TODO make a preference controller for handling UI preferences in conjunction with SE preferences
  val downloadsAlwaysEnabled =
    zms.flatMap(_.userPrefs.preference(DownloadImagesAlways).signal).disableAutowiring()

  messageActionsController.onMessageAction
    .collect { case (MessageAction.OpenFile, msg) => msg.assetId } {
      case Some(id) => openFile(id)
      case _ =>
    }

  def assetSignal(assetId: GeneralAssetId): Signal[GeneralAsset] =
    for {
      a <- assets
      status <- a.assetSignal(assetId)
    } yield status

  def assetSignal(assetId: Option[GeneralAssetId]): Signal[Option[GeneralAsset]] =
    assetId.fold(Signal.const(Option.empty[GeneralAsset]))(aid => assetSignal(aid).map(Option(_)))

  def assetSignal(assetId: Signal[GeneralAssetId]): Signal[GeneralAsset] =
    for {
      a <- assets
      id <- assetId
      status <- a.assetSignal(id)
    } yield status

  def assetStatusSignal(assetId: Signal[GeneralAssetId]): Signal[(service.assets.AssetStatus, Option[Progress])] =
    for {
      a <- assets
      id <- assetId
      status <- a.assetStatusSignal(id)
    } yield status

  def assetStatusSignal(assetId: GeneralAssetId): Signal[(service.assets.AssetStatus, Option[Progress])] =
    assetStatusSignal(Signal.const(assetId))

  def assetPreviewId(assetId: Signal[GeneralAssetId]): Signal[Option[GeneralAssetId]] =
    assetSignal(assetId).map {
      case u: UploadAsset   => u.preview match {
        case PreviewUploaded(aId)    => Option(aId)
        case PreviewNotUploaded(aId) => Option(aId)
        case _                       => Option.empty[GeneralAssetId]
      }
      case d: DownloadAsset => d.preview
      case a: Asset         => a.preview
      case _                => Option.empty[GeneralAssetId]
    }

  def downloadProgress(idGeneral: GeneralAssetId): Signal[Progress] = idGeneral match {
    case id: DownloadAssetId => assets.flatMap(_.downloadProgress(id))
    case _ => Signal.empty
  }

  def uploadProgress(idGeneral: GeneralAssetId): Signal[Progress] = idGeneral match {
    case id: UploadAssetId => assets.flatMap(_.uploadProgress(id))
    case _ => Signal.empty
  }

  def cancelUpload(idGeneral: GeneralAssetId, message: MessageData): Unit = idGeneral match {
    case id: UploadAssetId =>
      assets.currentValue.foreach(_.cancelUpload(id, message))
    case _ => ()
  }

  def cancelDownload(idGeneral: GeneralAssetId): Unit = idGeneral match {
    case id: DownloadAssetId => assets.currentValue.foreach(_.cancelDownload(id))
    case _ => ()
  }

  def retry(m: MessageData): Unit =
    if (m.isFailed) messages.currentValue.foreach(_.retryMessageSending(m.convId, m.id))

    def getPlaybackControls(asset: Signal[GeneralAsset]): Signal[PlaybackControls] = asset.flatMap { a =>
    (a.details, a) match {
      case (_: Audio, audioAsset: Asset) =>
        val file = new File(context.getCacheDir, s"${audioAsset.id.str}.m4a")
        Signal.future((if (!file.exists()) {
          file.createNewFile()
          assets.head.flatMap(_.loadContent(audioAsset).future).flatMap(ai => Future.fromTry(ai.toInputStream)).map { is =>
            IoUtils.copy(is, file)
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
  def showSingleImage(msg: MessageData, container: View): Unit =
    if (!(msg.isEphemeral && msg.expired)) {
      verbose(l"message loaded, opening single image for ${msg.id}")
      singleImage.setViewReferences(container)
      singleImage.showSingleImage(msg.id.str)
    }

  def openFile(idGeneral: GeneralAssetId): Unit = idGeneral match {
    case id: AssetId =>
      assetForSharing(id).foreach {
        case AssetForShare(asset, file) =>
          asset.details match {
            case _: Video =>
              context.startActivity(getOpenFileIntent(externalFileSharing.getUriForFile(file), asset.mime.orDefault.str))
              openVideoProgress ! false
            case _ =>
              showOpenFileDialog(externalFileSharing.getUriForFile(file), asset)
          }
        case _ =>
          error(l"Asset $id is not for share")
      }
    case _ =>
      error(l"GeneralAssetId is not AssetId: $idGeneral")
  }

  def showOpenFileDialog(uri: Uri, asset: Asset): Unit = {
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
        dialog.dismiss()
        saveToDownloads(asset)
      }
    })

    dialog.show()
  }

  private def saveAssetContentToFile(asset: Asset, targetDir: File): Future[File] =
    for {
      permissions <- permissions.head
      _           <- permissions.ensurePermissions(ListSet(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE))
      assets      <- assets.head
      assetInput  <- assets.loadContent(asset).future
      bytes       <- Future.fromTry(assetInput.toByteArray)
      // even if the asset input is a file it has to be copied to the "target file", visible from the outside
      targetFile  =  getTargetFile(asset, targetDir)
      _           =  IoUtils.writeBytesToFile(targetFile, bytes)
    } yield targetFile

  def saveImageToGallery(asset: Asset): Unit =
    saveAssetContentToFile(asset, createWireImageDirectory()).onComplete {
      case Success(file) =>
        val uri = URIWrapper.fromFile(file)
        imageNotifications.showImageSavedNotification(asset.id, uri)
        Toast.makeText(context, R.string.message_bottom_menu_action_save_ok, Toast.LENGTH_SHORT).show()
        context.sendBroadcast(returning(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE))(_.setData(Uri.fromFile(file))))
      case _             =>
        Toast.makeText(context, R.string.content__file__action__save_error, Toast.LENGTH_SHORT).show()
    }

  private def createWireImageDirectory() =
    returning(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/Wire Images/")) {
      IoUtils.createDirectory
    }

  def saveToDownloads(asset: Asset): Unit =
    saveAssetContentToFile(asset, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)).onComplete {
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
        Toast.makeText(context, R.string.content__file__action__save_error, Toast.LENGTH_SHORT).show()
    }

  def assetForSharing(id: AssetId): Future[AssetForShare] = {

    def getSharedFilename(asset: Asset): String =
      if (asset.name.isEmpty) s"${sha2(asset.id.str.take(6))}.${asset.mime.extension}"
      else if (!asset.name.endsWith(asset.mime.extension)) s"${asset.name}.${asset.mime.extension}"
      else asset.name

    for {
      assets     <- zms.head.map(_.assetService)
      asset      <- assets.getAsset(id)
      assetInput <- assets.loadContent(asset).future
      is         <- Future.fromTry(assetInput.toInputStream)
      // even if the asset input is a file it has to be copied to the "target file", visible from the outside
      file       =  new File(context.getExternalCacheDir, getSharedFilename(asset))
      _          =  IoUtils.copy(is, file)
      _          =  is.close()
    } yield AssetForShare(asset, file)
  }
}

object AssetsController {

  case class AssetForShare(asset: Asset, file: File)

  def getTargetFile(asset: Asset, directory: File): File = {
    def file(prefix: String = "") = {
      val prefixPart = if (prefix.isEmpty) "" else prefix + "_"
      val name = asset.name.replaceAll("/","_")
      val namePart = if (name.contains('.')) name else s"$name.${asset.mime.extension}"
      new File(directory, prefixPart + namePart)
    }

    val baseFile = file()
    if (!baseFile.exists()) baseFile
    else {
      (1 to 20).map(i => file(i.toString)).find(!_.exists())
        .getOrElse(file(AESKey.random.str))
    }
  }

  class PlaybackControls(assetId: AssetId, fileUri: URIWrapper, rAndP: Signal[GlobalRecordAndPlayService]) extends DerivedLogTag {

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

    def setPlayHead(duration: Duration) = rPAction { case (rP, key, content, _) => rP.setPlayhead(key, content, duration) }
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
