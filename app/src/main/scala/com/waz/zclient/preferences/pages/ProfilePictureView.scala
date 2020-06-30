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
package com.waz.zclient.preferences.pages

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.{ImageView, LinearLayout}
import com.waz.model.Picture
import com.waz.service.ZMessaging
import com.wire.signals.{EventContext, Signal}
import com.waz.zclient.common.views.GlyphButton
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.preferences.PreferencesActivity
import com.waz.zclient.utils.{BackStackKey, UiStorage, UserSignal}
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}
import com.waz.threading.Threading._

trait ProfilePictureView {
    def setPicture(picture: Picture): Unit
}

class ProfilePictureViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ProfilePictureView with ViewHelper{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preference_profile_picture_layout)

  val image = findById[ImageView](R.id.profile_user_picture)
  val cameraButton = findById[GlyphButton](R.id.profile_user_camera)

  cameraButton.setOnClickListener(new OnClickListener {
    override def onClick(v: View) = {
      Option(context.asInstanceOf[PreferencesActivity]).foreach(_.getControllerFactory.getCameraController.openCamera(CameraContext.SETTINGS))
    }
  })

  override def setPicture(picture: Picture): Unit = WireGlide(context).load(picture).into(image)
}

case class ProfilePictureBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_account_picture_title

  override def layoutId = R.layout.preference_profile_picture

  var controller = Option.empty[ProfilePictureViewController]

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[ProfilePictureViewImpl]).map(v => new ProfilePictureViewController(v)(v.wContext.injector, v))
  }

  override def onViewDetached() = {
    controller = None
  }
}

class ProfilePictureViewController(view: ProfilePictureView)(implicit inj: Injector, ec: EventContext) extends Injectable {
  val zms = inject[Signal[ZMessaging]]
  implicit val uiStorage = inject[UiStorage]

  val image = for {
    zms <- zms
    self <- UserSignal(zms.selfUserId)
    image <- self.picture.fold(Signal.empty[Picture])(Signal(_))
  } yield image

  image.onUi { view.setPicture }
}

