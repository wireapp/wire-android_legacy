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

import android.graphics.Bitmap
import android.graphics.drawable.{BitmapDrawable, ColorDrawable, TransitionDrawable}
import android.support.v7.widget.RecyclerView
import android.view.{View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.model.AssetData
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.utils.events.{EventContext, Signal, SourceSignal}
import com.waz.utils.returning
import com.waz.zclient.giphy.GiphyGridViewAdapter.{AssetLoader, ScrollGifCallback}
import com.waz.zclient.giphy.GiphySharingPreviewFragment.GifData
import com.waz.zclient.pages.main.conversation.views.AspectRatioImageView
import com.waz.zclient.ui.utils.MathUtils
import com.waz.zclient.{R, ViewHelper}


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

object GiphyGridViewAdapter {

  type AssetLoader = (AssetData, BitmapRequest) => Signal[BitmapResult]

  import GiphySharingPreviewFragment.RichSignal

  class ViewHolder(view: View,
                   val assetLoader: AssetLoader,
                   val scrollGifCallback: GiphyGridViewAdapter.ScrollGifCallback)
                  (implicit val ec: EventContext)
    extends RecyclerView.ViewHolder(view) {

    private val preview: SourceSignal[AssetData] = Signal[AssetData]()
    private val position: SourceSignal[Int] = Signal[Int]()
    private var image = Option.empty[AssetData]

    private val animationDuration = itemView.getContext.getResources.getInteger(R.integer.framework_animation_duration_short)
    private lazy val gifPreview = returning(itemView.findViewById[AspectRatioImageView](R.id.iv__row_giphy_image)){ iv =>
      iv.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View): Unit = {
          image.foreach(scrollGifCallback.setSelectedGifFromGridView)
        }
      })

      val defaultDrawable = for {
        position <- position
        colorArray = itemView.getContext.getResources.getIntArray(R.array.selectable_accents_color)
        drawable = new ColorDrawable(colorArray(position % (colorArray.length - 1)))
      } yield drawable

      val previewBitmap: Signal[Bitmap] = for {
        preview <- preview
        previewBitmap <- assetLoader(preview, BitmapRequest.Regular(width = iv.getWidth)).collect { case BitmapResult.BitmapLoaded(bitmap, _) => bitmap }
      } yield previewBitmap

      subscription = defaultDrawable either previewBitmap onUi {
        case Left(colorDrawable) => iv.setImageDrawable(colorDrawable)
        case Right(bitmap) =>
          val newImg = new BitmapDrawable(itemView.getContext.getResources, bitmap)
          val images = Array(iv.getDrawable, newImg)
          val crossfader = new TransitionDrawable(images)
          iv.setImageDrawable(crossfader)
          crossfader.startTransition(animationDuration)
          iv.setImageBitmap(bitmap)
      }
    }

    def setImageAssets(image: AssetData, preview: Option[AssetData], position: Int): Unit = {
      this.image = Some(image)
      this.position ! position

      preview.foreach { data =>
        this.preview ! data
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
                          (implicit val ec: EventContext)
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
