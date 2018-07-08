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
package com.waz.zclient.messages.parts.assets

import java.util.Locale

import android.content.Context
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.View
import android.widget._
import com.waz.api.AssetStatus._
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.messages.MsgPart
import com.waz.zclient.messages.parts.assets.DeliveryState._
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.ZLog.ImplicitTag._

class FileAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ActionableAssetPart with FileLayoutAssetPart { self =>
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.FileAsset

  private val downloadedIndicator: GlyphTextView = findById(R.id.done_indicator)
  private val fileNameView: TextView = findById(R.id.file_name)
  private val fileInfoView: TextView = findById(R.id.file_info)

  asset.map(_._1.name.getOrElse("")).on(Threading.Ui)(fileNameView.setText)
  asset.map(_._2).map(_ == DOWNLOAD_DONE).map { case true => View.VISIBLE; case false => View.GONE }.on(Threading.Ui)(downloadedIndicator.setVisibility)


  val sizeAndExt = asset.map {
    case (a, _) =>
      val size = if (a.sizeInBytes <= 0) None else Some(Formatter.formatFileSize(context, a.sizeInBytes))
      val ext = Option(a.mime.extension).map(_.toUpperCase(Locale.getDefault))
      (size, ext)
  }

  val text = deliveryState.map {
    case Uploading        => (R.string.content__file__status__uploading__minimized,       R.string.content__file__status__uploading,        R.string.content__file__status__uploading__size_and_extension)
    case Downloading      => (R.string.content__file__status__downloading__minimized,     R.string.content__file__status__downloading,      R.string.content__file__status__downloading__size_and_extension)
    case Cancelled        => (R.string.content__file__status__cancelled__minimized,       R.string.content__file__status__cancelled,        R.string.content__file__status__cancelled__size_and_extension)
    case UploadFailed     => (R.string.content__file__status__upload_failed__minimized,   R.string.content__file__status__upload_failed,    R.string.content__file__status__upload_failed__size_and_extension)
    case DownloadFailed   => (R.string.content__file__status__download_failed__minimized, R.string.content__file__status__download_failed,  R.string.content__file__status__download_failed__size_and_extension)
    case Complete         => (R.string.content__file__status__default,                    R.string.content__file__status__default,          R.string.content__file__status__default__size_and_extension)
    case _                => (0, 0, 0)
  }.zip(sizeAndExt).map {
    case ((min, default, full), sAndE) =>
      sAndE match {
        case (Some(size), Some(ext))  => getStringOrEmpty(full, size, ext)
        case (None, Some(ext))        => getStringOrEmpty(default, ext)
        case _                        => getStringOrEmpty(min)
      }
  }

  text.on(Threading.Ui)(fileInfoView.setText)

  assetActionButton.onClicked.filter(state => state == DeliveryState.Complete || state == DeliveryState.DownloadFailed) { _ =>
    asset.currentValue.foreach { case (a, _ ) => controller.openFile(a) }
  }
}



