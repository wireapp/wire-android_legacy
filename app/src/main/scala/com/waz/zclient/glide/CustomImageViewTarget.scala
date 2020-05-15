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
package com.waz.zclient.glide

import android.graphics.drawable.{Animatable, Drawable}
import android.widget.ImageView
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition

//TODO: Not sure why this one works but not the ImageViewTarget[T]
class CustomImageViewTarget[T <: ImageView](view: T) extends CustomViewTarget[T, Drawable](view) with Transition.ViewAdapter {

  private var animatable = Option.empty[Animatable]

  override def onResourceCleared(placeholder: Drawable): Unit = {
    animatable.foreach(_.stop())
    setDrawable(placeholder)
  }

  override def getCurrentDrawable: Drawable = view.getDrawable

  override def setDrawable(drawable: Drawable): Unit = {
    view.setImageDrawable(drawable)
    setAnimatable(drawable)
  }

  override def onLoadFailed(errorDrawable: Drawable): Unit = {
    animatable.foreach(_.stop())
    setDrawable(errorDrawable)
  }

  override def onResourceReady(resource: Drawable, transition: Transition[_ >: Drawable]): Unit = {
    if (transition != null && !transition.transition(resource, this))
      setDrawable(resource)
    else
      setAnimatable(resource)
  }

  def setAnimatable(resource: Drawable) = {
    animatable = resource match {
      case a: Animatable => Option(a)
      case _ => None

    }
    animatable.foreach(_.start())
  }

  override def onStart(): Unit = animatable.foreach(_.start())

  override def onStop(): Unit = animatable.foreach(_.stop())
}
