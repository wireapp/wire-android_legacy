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
package com.waz.zclient.messages.parts

import java.io.File

import android.animation.ObjectAnimator
import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.wrappers.{AndroidURIUtil, Bitmap, URI}
import com.waz.zclient.pages.extendedcursor.image.CursorImagesLayout
import com.waz.zclient.utils.{LocalThumbnailCache, RichView}
import com.waz.zclient.{R, ViewHelper}
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.utils.returning

import scala.concurrent.Promise

class GalleryItemViewHolder(imageView: CursorGalleryItem) extends RecyclerView.ViewHolder(imageView) {

  private var uri = Option.empty[URI]

  def bind(path: String, callback: CursorImagesLayout.Callback): Unit =
    if (!uri.exists(_.getPath == path)) {
      uri = Some(AndroidURIUtil.fromFile(new File(path)))
      imageView.setThumbnail(path)
      imageView.onClick(uri.foreach(callback.onGalleryPictureSelected))
    }

}

class CursorGalleryItem(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends ImageView(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  import CursorGalleryItem.measuredHeight

  private var loadHandle    = Option.empty[CancellableFuture[Bitmap]]
  private lazy val cache    = inject[LocalThumbnailCache]
  private lazy val animator = returning(ObjectAnimator.ofFloat(this, View.ALPHA, 0, 1f)) {
    _.setDuration(context.getResources.getInteger(R.integer.animation_duration_medium))
  }

  def setThumbnail(path: String): Unit = {
    setImageResource(android.R.color.transparent)

    loadHandle.foreach(_.cancel())

    loadHandle = Option(returning {
      CancellableFuture.lift(measuredHeight.future.map { mh =>
        cache.getOrCreate(LocalThumbnailCache.Thumbnail(path, mh, mh))
      }(Threading.Background))
    } {
      _.foreach { bm =>
        setImageBitmap(bm)
        animator.start()
      }(Threading.Ui)
    })
  }

  override protected def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int): Unit = {
    super.onMeasure(heightMeasureSpec, heightMeasureSpec) // to make it square
    if (!measuredHeight.isCompleted) measuredHeight.success(this.getMeasuredHeight)
  }
}

object CursorGalleryItem {
  private val measuredHeight = Promise[Int]()
}
