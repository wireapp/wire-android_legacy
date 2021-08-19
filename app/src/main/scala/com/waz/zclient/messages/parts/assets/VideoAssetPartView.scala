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

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.{FrameLayout, ImageView}
import com.waz.service.assets.{AssetStatus, DownloadAssetStatus, UploadAssetStatus}
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.{HighlightViewPart, MsgPart}
import com.waz.zclient.utils.RichView
import com.waz.threading.Threading._
import com.waz.zclient.messages.parts.assets.AssetPart.AssetPartViewState

class VideoAssetPartView(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style) with PlayableAsset with ImageLayoutAssetPart with HighlightViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.VideoAsset

  private val controls = findById[View](R.id.controls)
  private val assetController = inject[AssetsController]

  private val image = findById[ImageView](R.id.image)
  private val obfuscationContainer = findById[View](R.id.obfuscation_container)
  private val restrictionContainer = findById[View](R.id.restriction_container)

  assetController.openVideoProgress.onUi {
    case true  => assetActionButton.startEndlessProgress()
    case false => assetActionButton.clearProgress()
  }

  viewState.onUi {
    case AssetPartViewState.Restricted =>
      restrictionContainer.setVisible(true)
      obfuscationContainer.setVisible(false)
      image.setVisible(false)
      controls.setVisible(false)

    case AssetPartViewState.Obfuscated =>
      restrictionContainer.setVisible(false)
      obfuscationContainer.setVisible(true)
      image.setVisible(false)
      controls.setVisible(false)

    case AssetPartViewState.Loading =>
      restrictionContainer.setVisible(false)
      obfuscationContainer.setVisible(false)
      image.setVisible(false)
      controls.setVisible(false)

    case AssetPartViewState.Loaded =>
      restrictionContainer.setVisible(false)
      obfuscationContainer.setVisible(false)
      image.setVisible(true)
      controls.setVisible(true)

    case unknown =>
      info(l"Unknown AssetPartViewState: $unknown")
  }

  (for {
    assetId <- previewAssetId
    state   <- viewState
  } yield (assetId, state)).onUi {
    case (Some(aId), state) if state != AssetPartViewState.Restricted =>
      WireGlide(context).load(aId).into(image)
    case _ =>
      WireGlide(context).clear(image)
  }

  assetActionButton.onClick {
    viewState.head.foreach { state =>
      if (state != AssetPartViewState.Restricted) {
        assetStatus.map(_._1).currentValue.foreach {
          case UploadAssetStatus.Failed       => message.currentValue.foreach(retr => { println(retr);  controller.retry(retr)})
          case UploadAssetStatus.InProgress   => message.currentValue.foreach(m => controller.cancelUpload(m.assetId.get, m))
          case DownloadAssetStatus.InProgress => message.currentValue.foreach(m => controller.cancelDownload(m.assetId.get))
          case AssetStatus.Done               => {
            assetController.openVideoProgress ! true
            asset.head.foreach(a => controller.openFile(a.id))(Threading.Ui)
          }
          case status                         => error(l"Unhandled asset status: $status")
        }
      }
    }(Threading.Ui)
  }

  padding.onUi { p =>
    durationView.setMargin(p.l, p.t, p.r, p.b)
  }

}
