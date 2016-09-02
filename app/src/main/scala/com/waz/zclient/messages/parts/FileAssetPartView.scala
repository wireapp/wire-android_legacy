/**
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
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  */
package com.waz.zclient.messages.parts

import java.util.Locale

import android.app.DownloadManager
import android.content.{Context, Intent}
import android.net.Uri
import android.support.v7.app.AppCompatDialog
import android.text.TextUtils
import android.text.format.Formatter
import android.util.{AttributeSet, TypedValue}
import android.view.{Gravity, View}
import android.widget._
import com.waz.api.AssetStatus._
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.messages.MsgPart
import com.waz.zclient.messages.parts.DeliveryState._
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{AssetUtils, RichView}

import scala.util.Success

class FileAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ActionableAssetPart {
  self =>
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.FileAsset

  override def inflate() = inflate(R.layout.message_file_asset_content)

  private val downloadedIndicator: GlyphTextView = findById(R.id.done_indicator)
  private val fileNameView: TextView = findById(R.id.file_name)
  private val fileInfoView: TextView = findById(R.id.file_info)

  asset.map(_._1.name.getOrElse("")).on(Threading.Ui)(fileNameView.setText)
  asset.map(_._2).map(_ == DOWNLOAD_DONE).map { case true => View.VISIBLE; case false => View.GONE }.on(Threading.Ui)(downloadedIndicator.setVisibility)

  val sizeAndExt = asset.map {
    case (a, _) =>
      val size = if (a.sizeInBytes <= 0) None else Some(Formatter.formatFileSize(context, a.sizeInBytes))
      val ext = Option(a.mimeType.extension).map(_.toUpperCase(Locale.getDefault))
      (size, ext)
  }

  deliveryState.map {
    case Uploading => (R.string.content__file__status__uploading__minimized, R.string.content__file__status__uploading, R.string.content__file__status__uploading__size_and_extension)
    case Downloading => (R.string.content__file__status__downloading__minimized, R.string.content__file__status__downloading, R.string.content__file__status__downloading__size_and_extension)
    case Cancelled => (R.string.content__file__status__cancelled__minimized, R.string.content__file__status__cancelled, R.string.content__file__status__cancelled__size_and_extension)
    case UploadFailed => (R.string.content__file__status__upload_failed__minimized, R.string.content__file__status__upload_failed, R.string.content__file__status__upload_failed__size_and_extension)
    case DownloadFailed => (R.string.content__file__status__download_failed__minimized, R.string.content__file__status__download_failed, R.string.content__file__status__download_failed__size_and_extension)
    case Complete => (R.string.content__file__status__default, R.string.content__file__status__default, R.string.content__file__status__default__size_and_extension)
    case _ => (0, 0, 0)
  }.zip(sizeAndExt).map {
    case ((min, dfault, full), sAndE) =>
      sAndE match {
        case (Some(size), Some(ext)) => getStringOrEmpty(full, size, ext)
        case (None, Some(ext)) => getStringOrEmpty(dfault, ext)
        case _ => getStringOrEmpty(min)
      }
  }.on(Threading.Ui)(fileInfoView.setText)

  actionReady.on(Threading.Ui) {
    case true =>
      assetActionButton.onClick {
        for {
          id <- assetId.currentValue
          as <- zms.map(_.assets).currentValue
          a <- asset.map(_._1).currentValue
          mime <- asset.map(_._1.mimeType).currentValue
        } {
          as.getAssetUri(id).foreach {
            case Some(uri) =>
              val intent = AssetUtils.getOpenFileIntent(uri, mime.orDefault.str)
              val fileCanBeOpened = AssetUtils.fileTypeCanBeOpened(context.getPackageManager, intent)

              //TODO tidy up
              //TODO there is also a weird flash or double-dialog issue when you click outside of the dialog
              val dialog = new AppCompatDialog(context)
              a.name.foreach(dialog.setTitle)
              dialog.setContentView(R.layout.file_action_sheet_dialog)

              val title = dialog.findViewById(R.id.title).asInstanceOf[TextView]
              title.setEllipsize(TextUtils.TruncateAt.MIDDLE)
              title.setTypeface(TypefaceUtils.getTypeface(getString(R.string.wire__typeface__medium)))
              title.setTextSize(TypedValue.COMPLEX_UNIT_PX, getDimenPx(R.dimen.wire__text_size__regular))
              title.setGravity(Gravity.CENTER)

              val openButton = dialog.findViewById(R.id.ttv__file_action_dialog__open).asInstanceOf[TextView]
              val noAppFoundLabel = dialog.findViewById(R.id.ttv__file_action_dialog__open__no_app_found)
              val saveButton = dialog.findViewById(R.id.ttv__file_action_dialog__save)

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
                  as.saveAssetToDownloads(id).onComplete {
                    case Success(Some(file)) =>
                      val uri = Uri.fromFile(file)
                      val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE).asInstanceOf[DownloadManager]
                      downloadManager.addCompletedDownload(a.name.get, a.name.get, false, a.mimeType.orDefault.str, uri.getPath, a.sizeInBytes, true)
                      Toast.makeText(context, com.waz.zclient.ui.R.string.content__file__action__save_completed, Toast.LENGTH_SHORT).show()

                      val intent: Intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                      intent.setData(uri)
                      context.sendBroadcast(intent)
                    case _ =>
                  }(Threading.Ui)
                }
              })

              dialog.show()
            case _ =>
          }(Threading.Ui)
        }
      }

    case false =>
  }
}



