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
package com.waz.zclient.conversation

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v7.widget.{RecyclerView, StaggeredGridLayoutManager}
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.Message
import com.waz.bitmap.BitmapUtils
import com.waz.content.MessagesStorage
import com.waz.model.MessageData.MessageDataDao
import com.waz.model.{AssetData, AssetId, ConvId}
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.images.BitmapSignal
import com.waz.threading.Threading
import com.waz.ui.MemoryImageCache.BitmapRequest.Regular
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.conversation.CollectionAdapter.CollViewHolder
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.conversation.views.AspectRatioImageView
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{FragmentHelper, Injectable, Injector, R}

class CollectionFragment extends BaseFragment[CollectionFragment.Container] with FragmentHelper {

  private implicit lazy val context: Context = getContext

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = inflater.inflate(R.layout.fragment_collection, container, false)
    val recyclerView: RecyclerView = ViewUtils.getView(view, R.id.rv__collection)
    recyclerView.setAdapter(new CollectionAdapter)
    recyclerView.setLayoutManager(new StaggeredGridLayoutManager(4, StaggeredGridLayoutManager.VERTICAL))
    view
  }

}

object CollectionFragment {

  val TAG = CollectionFragment.getClass.getSimpleName

  def newInstance() = new CollectionFragment

  trait Container

}

//For now just handling images
class CollectionAdapter(implicit context: Context, injector: Injector, eventContext: EventContext) extends RecyclerView.Adapter[CollectionAdapter.CollViewHolder] with Injectable {

  val ctrler = new CollectionController
  val images = ctrler.images

  images.onChanged.on(Threading.Ui) { _ => notifyDataSetChanged() }

  override def getItemCount: Int = images.currentValue.map(_.size).getOrElse(0)

  override def onBindViewHolder(holder: CollViewHolder, position: Int): Unit =
    holder.setAsset(images.currentValue.getOrElse(Seq.empty)(position), ctrler.bitmapSignal)

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): CollViewHolder =
    CollViewHolder(LayoutInflater.from(parent.getContext).inflate(R.layout.row_giphy_image, parent, false).asInstanceOf[AspectRatioImageView])

}

object CollectionAdapter {

  case class CollViewHolder(view: AspectRatioImageView)(implicit eventContext: EventContext) extends RecyclerView.ViewHolder(view) {

    def setAsset(asset: Signal[AssetData], bitmap: (AssetId, Int) => Signal[Option[Bitmap]]) = asset.on(Threading.Ui) { a =>

      view.setAspectRatio(a.width.toFloat / a.height.toFloat)
      //TODO set some placeholder color or dots or something

      bitmap(a.id, view.getWidth).on(Threading.Ui) {
        case Some(b) => view.setImageBitmap(b)
        case None => //TODO bitmap didn't load
      }

    }
  }

}

//testable!
protected class CollectionController(implicit injector: Injector) extends Injectable {

  val zms = inject[Signal[ZMessaging]]

  val currentConv = zms.flatMap(_.convsStats.selectedConversationId).collect { case Some(id) => id }

  val msgStorage = zms.map(_.messagesStorage)

  val assetStorage = zms.map(_.assetsStorage)

  val images = assetStorage.zip(currentConv.zip(msgStorage).flatMap {
    case (id, msgs) => Signal.future(loadImages(id, msgs))
  }).map {
    case (as, ids) => ids.map(as.signal)
  }

  private def loadImages(conv: ConvId, storage: MessagesStorage) = {
    verbose(s"loadCursor for $conv")
    storage.find(m => m.convId == conv && m.msgType == Message.Type.ASSET, MessageDataDao.findByType(conv, Message.Type.ASSET)(_), _.assetId)
  }

  def bitmapSignal(assetId: AssetId, width: Int) = zms.flatMap { zms =>
    zms.assetsStorage.signal(assetId).flatMap {
      case data@AssetData.IsImage() => BitmapSignal(data, Regular(width), zms.imageLoader, zms.imageCache)
      case _ => Signal.empty[BitmapResult]
    }
  }.map {
    case BitmapLoaded(bmp, _) => Option(BitmapUtils.cropRect(bmp, width))
    case _ => None
  }

}
