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
import android.widget.{FrameLayout, TextView}
import com.waz.service.assets.{AssetStatus, UploadAssetStatus}
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.parts.assets.AssetPart.AssetPartViewState
import com.waz.zclient.messages.{HighlightViewPart, MsgPart}
import com.waz.zclient.messages.parts.assets.DeliveryState._
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, StyleKitMethods}
import com.waz.threading.Threading._
import com.waz.zclient.paintcode.GenericStyleKitView

class FileAssetPartView(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style) with ActionableAssetPart with FileLayoutAssetPart with HighlightViewPart { self =>
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.FileAsset

  private val downloadedIndicator: GlyphTextView = findById(R.id.done_indicator)
  private val fileNameView: TextView = findById(R.id.file_name)
  private val fileInfoView: TextView = findById(R.id.file_info)

  private val content: View = findById(R.id.content)
  private val obfuscationContainer: View = findById(R.id.obfuscation_container)
  private val restrictionContainer: View = findById(R.id.restriction_container)
  private val restrictedFileNameTextView: TextView = findById(R.id.restricted_file_name)

  returning(findById[GenericStyleKitView](R.id.restricted_file_icon)) { view =>
    view.setOnDraw(StyleKitMethods().drawFileBlocked)
    view.setColor(getStyledColor(R.attr.wirePrimaryTextColor))
  }

  viewState.onUi {
    case AssetPartViewState.Restricted =>
      restrictionContainer.setVisibility(View.VISIBLE)
      obfuscationContainer.setVisibility(View.GONE)
      content.setVisibility(View.GONE)

    case AssetPartViewState.Obfuscated =>
      restrictionContainer.setVisibility(View.GONE)
      obfuscationContainer.setVisibility(View.VISIBLE)
      content.setVisibility(View.GONE)

    case AssetPartViewState.Loading =>
      restrictionContainer.setVisibility(View.GONE)
      obfuscationContainer.setVisibility(View.GONE)
      content.setVisibility(View.GONE)

    case AssetPartViewState.Loaded =>
      restrictionContainer.setVisibility(View.GONE)
      obfuscationContainer.setVisibility(View.GONE)
      content.setVisibility(View.VISIBLE)

    case unknown =>
      info(l"Unknown AssetPartViewState: $unknown")
  }

  asset
    .map(_.name)
    .onUi { fileName =>
      fileNameView.setText(fileName)
      restrictedFileNameTextView.setText(fileName.toUpperCase)
    }

  assetStatus
    .map(_._1)
    .map(_ == AssetStatus.Done)
    .map { case true => View.VISIBLE; case false => View.GONE }
    .onUi(downloadedIndicator.setVisibility)



  val sizeAndExt = asset.map { asset =>
    val size = if (asset.size <= 0) None else Some(Formatter.formatFileSize(context, asset.size))
    val ext = Option(asset.mime.extension).map(_.toUpperCase(Locale.getDefault))
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

  text.onUi(fileInfoView.setText)

  assetActionButton.onClick {
    for {
      s     <- assetStatus.map(_._1).currentValue
      a     <- asset.currentValue
      m     <- message.currentValue
      state <- viewState.currentValue
    } yield s match {
      case AssetStatus.Done if (state != AssetPartViewState.Restricted) =>
        controller.openFile(a.id)
      case UploadAssetStatus.NotStarted | UploadAssetStatus.InProgress =>
        controller.cancelUpload(a.id, m)
      case _ =>
    }
  }

}
