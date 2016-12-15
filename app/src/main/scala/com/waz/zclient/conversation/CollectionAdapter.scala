/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.waz.zclient.conversation

import android.content.Context
import android.graphics.Bitmap
import android.support.v7.widget.RecyclerView
import android.view.{LayoutInflater, ViewGroup}
import com.waz.model.{AssetData, AssetId}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.conversation.CollectionAdapter.CollViewHolder
import com.waz.zclient.pages.main.conversation.views.AspectRatioImageView
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{Injectable, Injector, R}

//For now just handling images
class CollectionAdapter(val screenWidth: Int, val columns: Int)(implicit context: Context, injector: Injector, eventContext: EventContext) extends RecyclerView.Adapter[CollectionAdapter.CollViewHolder] with Injectable {

  val ctrler = new CollectionController
  val images = ctrler.images

  images.onChanged.on(Threading.Ui) { _ => notifyDataSetChanged() }

  override def getItemCount: Int = images.currentValue.map(_.size).getOrElse(0)

  override def onBindViewHolder(holder: CollViewHolder, position: Int): Unit =
    holder.setAsset(images.currentValue.getOrElse(Seq.empty)(position), ctrler.bitmapSignal, screenWidth / columns)

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): CollViewHolder =
    CollViewHolder(LayoutInflater.from(parent.getContext).inflate(R.layout.row_collection_image, parent, false).asInstanceOf[AspectRatioImageView])

  def getHeaderId(position: Int): Int = {
    //val image = images.currentValue.getOrElse(Seq.empty)(position)
    //val imageDate = image.getDate
//    if (imageDate.isToday) headerToday
//    if (imageDate.isYesterday) headerYesterday
//      ... you get the point... ;)
    // TODO just testing headers here...
    if (position >= 3)
      1
    else
      0
  }

}

object CollectionAdapter {

  case class CollViewHolder(view: AspectRatioImageView)(implicit eventContext: EventContext) extends RecyclerView.ViewHolder(view) {

    def setAsset(asset: Signal[AssetData], bitmap: (AssetId, Int) => Signal[Option[Bitmap]], width: Int) = asset.on(Threading.Ui) { a =>

      view.setAspectRatio(1)
      //TODO set some placeholder color or dots or something
      ViewUtils.setWidth(view, width);
      ViewUtils.setHeight(view, width);
      bitmap(a.id, view.getWidth).on(Threading.Ui) {
        case Some(b) => view.setImageBitmap(b)
        case None => //TODO bitmap didn't load
      }

    }
  }

}