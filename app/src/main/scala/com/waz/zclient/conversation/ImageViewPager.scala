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
package com.waz.zclient.conversation

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import androidx.viewpager.widget.{PagerAdapter, ViewPager}
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams
import android.view.{View, ViewGroup}
import com.waz.api.MessageFilter
import com.waz.model.MessageData
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.collection.controllers.CollectionController.{AllContent, ContentType, Images}
import com.waz.zclient.collection.fragments.SingleImageCollectionFragment.SwipeImageView
import com.waz.zclient.messages.RecyclerCursor
import com.waz.zclient.messages.RecyclerCursor.RecyclerNotifier
import com.waz.zclient.pages.main.conversationpager.CustomPagerTransformer
import com.waz.zclient.{Injectable, Injector, ViewHelper}

import scala.collection.mutable

//TODO: merge this with collections version
class ImageViewPager(context: Context, attrs: AttributeSet) extends ViewPager(context, attrs) with ViewHelper {
  def this(context: Context) = this(context, null)

  lazy val collectionController = inject[CollectionController]

  val messageData = for {
    Some(msg) <- collectionController.focusedItem
  } yield msg

  private var imageAdapter: Option[PagerAdapter] = None

  messageData { msg => imageAdapter match {
    case None => setImageAdapter(msg)
    case Some(adapter) => adapter match { // Maciek: needed for an AN-5315 corner case where focusedItem gives two different messages and the second one is correct
      case _: ImageSwipeAdapter if msg.isEphemeral => setImageAdapter(msg)
      case a: SingleImageAdapter => setImageAdapter(msg)
      case _ =>
    }
  }}

  private def setImageAdapter(msg: MessageData): Unit = {
    val adapter = if (msg.isEphemeral) singleImageAdapter(msg) else imageSwipeAdapter()
    setAdapter(adapter)
    imageAdapter = Some(adapter)
  }

  private def singleImageAdapter(msg: MessageData) = {
    clearOnPageChangeListeners()
    setPageTransformer(false, null)
    setCurrentItem(0, false)
    new SingleImageAdapter(getContext, msg)
  }

  private def imageSwipeAdapter() = {
    val adapter = new ImageSwipeAdapter(getContext)
    setPageTransformer(false, new CustomPagerTransformer (CustomPagerTransformer.SLIDE))
    addOnPageChangeListener(new OnPageChangeListener {
      override def onPageScrollStateChanged(state: Int): Unit = {}
      override def onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int): Unit = {}
      override def onPageSelected(position: Int): Unit = collectionController.focusedItem ! adapter.getItem(position)
    })
    messageData.flatMap(adapter.positionForMessage).on(Threading.Ui) { pos => if (pos >= 0) setCurrentItem(pos, false) }
    adapter
  }

}

class SingleImageAdapter(context: Context, val msg: MessageData)(implicit injector: Injector, ev: EventContext) extends PagerAdapter with Injectable { self =>
  override def isViewFromObject(view: View, obj: scala.Any): Boolean = true

  override def getCount: Int = 1

  override def instantiateItem(container: ViewGroup, position: Int): AnyRef = {
    val imageView = new SwipeImageView(context)
    imageView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    imageView.setImageDrawable(new ColorDrawable(Color.TRANSPARENT))
    container.addView(imageView)
    imageView.setMessageData(msg)
    imageView
  }

  override def destroyItem(container: ViewGroup, position: Int, obj: scala.Any): Unit = {
    val view = obj.asInstanceOf[SwipeImageView]
    view.resetZoom()
    container.removeView(view)
  }
}

class ImageSwipeAdapter(context: Context)(implicit injector: Injector, ev: EventContext) extends PagerAdapter with Injectable { self =>

  private val discardedImages = mutable.Queue[SwipeImageView]()

  private val zms = inject[Signal[ZMessaging]]

  val contentMode = Signal[ContentType](AllContent)

  val notifier = new RecyclerNotifier(){
    override def notifyDataSetChanged(): Unit = self.notifyDataSetChanged()

    override def notifyItemRangeInserted(index: Int, length: Int): Unit = self.notifyDataSetChanged()

    override def notifyItemRangeChanged(index: Int, length: Int): Unit = self.notifyDataSetChanged()

    override def notifyItemRangeRemoved(pos: Int, count: Int): Unit = self.notifyDataSetChanged()
  }

  var recyclerCursor: Option[RecyclerCursor] = None
  val cursor = for {
    zs <- zms
    convId <- inject[ConversationController].currentConvId
  } yield new RecyclerCursor(convId, zs, notifier, Some(MessageFilter(Some(Images.typeFilter))))

  implicit val executionContext = Threading.Ui

  def positionForMessage(msg: MessageData): Signal[Int] =
    for {
    c <- cursor
    pos <- Signal.future(c.positionForMessage(msg))
  } yield pos

  cursor.on(Threading.Ui) { c =>
    if (!recyclerCursor.contains(c)) {
      recyclerCursor.foreach(_.close())
      recyclerCursor = Some(c)
      notifier.notifyDataSetChanged()
    }
  }

  def getItem(position: Int): Option[MessageData] = recyclerCursor.flatMap {
    case c if c.count > position => Some(c.apply(position).message)
    case _ => None
  }

  override def instantiateItem(container: ViewGroup, position: Int): AnyRef = {
    val imageView = if (discardedImages.nonEmpty) discardedImages.dequeue() else new SwipeImageView(context)
    imageView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    imageView.setImageDrawable(new ColorDrawable(Color.TRANSPARENT))
    container.addView(imageView)
    imageView.setPosition(position)
    getItem(position).foreach { messageData => imageView.setMessageData(messageData) }
    imageView
  }

  override def destroyItem(container: ViewGroup, position: Int, obj: scala.Any): Unit = {
    val view = obj.asInstanceOf[SwipeImageView]
    view.resetZoom()
    discardedImages.enqueue(view)
    container.removeView(view)
  }

  override def isViewFromObject(view: View, obj: scala.Any): Boolean =
    (view, obj) match {
      case (v: SwipeImageView, o: SwipeImageView) => v.getPosition == o.getPosition
      case _ => false
    }

  override def getCount: Int = recyclerCursor.fold(0)(_.count)
}
