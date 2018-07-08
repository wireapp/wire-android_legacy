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
package com.waz.zclient.giphy

import android.graphics.drawable.ColorDrawable
import android.support.v7.widget.RecyclerView
import android.view.{View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.model.AssetData
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageController.DataImage
import com.waz.zclient.giphy.GiphyGridViewAdapter.{AssetLoader, ScrollGifCallback}
import com.waz.zclient.giphy.GiphySharingPreviewFragment.GifData
import com.waz.zclient.pages.main.conversation.views.AspectRatioImageView
import com.waz.zclient.ui.utils.MathUtils
import com.waz.zclient.{Injector, R, ViewHelper}

object GiphyGridViewAdapter {

  type AssetLoader = (AssetData, BitmapRequest) => Signal[BitmapResult]

  class ViewHolder(view: View,
                   val assetLoader: AssetLoader,
                   val scrollGifCallback: GiphyGridViewAdapter.ScrollGifCallback)
                  (implicit val ec: EventContext, injector: Injector)
    extends RecyclerView.ViewHolder(view) {

    private lazy val gifPreview = itemView.findViewById[AspectRatioImageView](R.id.iv__row_giphy_image)

    def setImageAssets(image: AssetData, preview: Option[AssetData], position: Int): Unit = {
      gifPreview.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View): Unit = {
          scrollGifCallback.setSelectedGifFromGridView(image)
        }
      })

      val colorArray = itemView.getContext.getResources.getIntArray(R.array.selectable_accents_color)
      val defaultDrawable = new ColorDrawable(colorArray(position % (colorArray.length - 1)))

      preview match {
        case None => gifPreview.setImageDrawable(defaultDrawable)
        case Some(data) =>
          val imageAssetDrawable = new ImageAssetDrawable(
            Signal.const(DataImage(data)),
            background = Some(defaultDrawable)
          )
          gifPreview.setImageDrawable(imageAssetDrawable)
          gifPreview.setAspectRatio(
            if (MathUtils.floatEqual(data.height, 0)) 1f
            else data.width.toFloat / data.height
          )
      }
    }
  }

  trait ScrollGifCallback {
    def setSelectedGifFromGridView(gifAsset: AssetData): Unit
  }

}

class GiphyGridViewAdapter(val scrollGifCallback: ScrollGifCallback,
                           val assetLoader: AssetLoader)
                          (implicit val ec: EventContext, injector: Injector)
  extends RecyclerView.Adapter[GiphyGridViewAdapter.ViewHolder] {

  import GiphyGridViewAdapter._

  private var giphyResults = Seq.empty[GifData]

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): GiphyGridViewAdapter.ViewHolder = {
    val rootView = ViewHelper.inflate[View](R.layout.row_giphy_image, parent, addToParent = false)
    new ViewHolder(rootView, assetLoader, scrollGifCallback)
  }

  override def onBindViewHolder(holder: GiphyGridViewAdapter.ViewHolder, position: Int): Unit = {
    val GifData(preview, image) = giphyResults(position)
    holder.setImageAssets(image, preview, position)
  }

  override def getItemCount: Int = giphyResults.size

  def setGiphyResults(giphyResults: Seq[GifData]): Unit = {
    this.giphyResults = giphyResults
    notifyDataSetChanged()
  }
}
