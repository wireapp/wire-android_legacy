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

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.{View, ViewGroup}
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.RequestOptions
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.media.GiphyService.GifObject
import com.waz.utils.events.EventContext
import com.waz.zclient.giphy.GiphyGridViewAdapter.ScrollGifCallback
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.pages.main.conversation.views.AspectRatioImageView
import com.waz.zclient.ui.utils.MathUtils
import com.waz.zclient.{Injector, R, ViewHelper}

object GiphyGridViewAdapter {
  class ViewHolder(view: View,
                   val scrollGifCallback: GiphyGridViewAdapter.ScrollGifCallback)
                  (implicit val ec: EventContext, injector: Injector)
    extends RecyclerView.ViewHolder(view) {

    implicit private val cxt: Context = itemView.getContext

    private lazy val gifPreview = itemView.findViewById[AspectRatioImageView](R.id.iv__row_giphy_image)

    def setGif(gifObject: GifObject): Unit = {
      gifPreview.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View): Unit = {
          scrollGifCallback.setSelectedGifFromGridView(gifObject)
        }
      })

      val colorArray = cxt.getResources.getIntArray(R.array.selectable_accents_color)
      lazy val defaultDrawable = new ColorDrawable(colorArray(getAdapterPosition % (colorArray.length - 1)))


      val gifDims = gifObject.original.dimensions
      gifPreview.setAspectRatio(
        if (MathUtils.floatEqual(gifDims.height, 0)) 1f
        else gifDims.width.toFloat / gifDims.height
      )

      gifObject.preview match {
        case Some(gif) =>
          WireGlide(cxt)
            .load(gif.source.toString)
            .apply(new RequestOptions()
              .fitCenter()
              .placeholder(defaultDrawable))
            .into(gifPreview)
        case None =>
          WireGlide(cxt).clear(gifPreview)
          gifPreview.setImageDrawable(defaultDrawable)
      }
    }
  }

  trait ScrollGifCallback {
    def setSelectedGifFromGridView(gif: GifObject): Unit
  }

}

class GiphyGridViewAdapter(val scrollGifCallback: ScrollGifCallback)
                          (implicit val ec: EventContext, injector: Injector)
  extends RecyclerView.Adapter[GiphyGridViewAdapter.ViewHolder]
    with DerivedLogTag {

  import GiphyGridViewAdapter._

  private var giphyResults = Seq.empty[GifObject]

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): GiphyGridViewAdapter.ViewHolder = {
    val rootView = ViewHelper.inflate[View](R.layout.row_giphy_image, parent, addToParent = false)
    new ViewHolder(rootView, scrollGifCallback)
  }

  override def onBindViewHolder(holder: GiphyGridViewAdapter.ViewHolder, position: Int): Unit = {
    holder.setGif(giphyResults(position))
  }

  override def getItemCount: Int = giphyResults.size

  def setGiphyResults(giphyResults: Seq[GifObject]): Unit = {
    this.giphyResults = giphyResults
    notifyDataSetChanged()
  }
}
