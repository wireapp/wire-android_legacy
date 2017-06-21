/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.profile.preferences.pages

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.{ImageView, LinearLayout}
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}
import com.waz.zclient.utils.{UiStorage, UserSignal, ViewState}
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageAssetDrawable.ScaleType
import com.waz.zclient.views.ImageController.{ImageSource, WireImage}

trait ProfilePictureView {

  def setPictureDrawable(drawable: Drawable): Unit
}

class ProfilePictureViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ProfilePictureView with ViewHelper{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preference_profile_picture_layout)

  val image = findById[ImageView](R.id.profile_user_picture)

  override def setPictureDrawable(drawable: Drawable) = image.setImageDrawable(drawable)
}

case class ProfilePictureViewState() extends ViewState {
  override def name = "Profile Picture"

  override def layoutId = R.layout.preference_profile_picture

  var controller = Option.empty[ProfilePictureViewController]

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[ProfilePictureViewImpl]).map(new ProfilePictureViewController(_))
  }

  override def onViewDetached() = {
    controller = None
  }

  override def inAnimation(view: View, root: View, forward: Boolean) = {
    view.setAlpha(0.0f)
    if (forward)
      view.setTranslationY(root.getHeight)
    else
      view.setTranslationY(-root.getHeight)
    view.animate().alpha(1.0f).translationX(0)
  }

  override def outAnimation(view: View, root: View, forward: Boolean) = super.outAnimation(view, root, forward)
}

class ProfilePictureViewController(view: ProfilePictureView)(implicit inj: Injector, ec: EventContext) extends Injectable {
  val zms = inject[Signal[ZMessaging]]
  implicit val uiStorage = inject[UiStorage]

  val image = for {
    zms <- zms
    self <- UserSignal(zms.selfUserId)
    image <- self.picture.map(WireImage).fold(Signal.empty[ImageSource])(Signal(_))
  } yield image

  view.setPictureDrawable(new ImageAssetDrawable(image, scaleType = ScaleType.CenterInside))
}

