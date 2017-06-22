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
import android.view.View.OnClickListener
import android.widget.{ImageView, LinearLayout}
import com.waz.ZLog
import com.waz.api.impl.AccentColor
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient._
import com.waz.zclient.pages.main.profile.preferences.views.TextButton
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.{BackStackNavigator, StringUtils, UiStorage, UserSignal, ViewState}
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.views.ImageController.{ImageSource, WireImage}

trait ProfileView {
  def setUserName(name: String): Unit
  def setHandle(handle: String): Unit
  def setProfilePictureDrawable(drawable: Drawable): Unit
  def setAccentColor(color: Int): Unit
}

class ProfileViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ProfileView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_profile_layout)

  val navigator = inject[BackStackNavigator]

  val userNameText = findById[TypefaceTextView](R.id.profile_user_name)
  val userPicture = findById[ImageView](R.id.profile_user_picture)
  val userHandleText = findById[TypefaceTextView](R.id.profile_user_handle)

  val settingsButton = findById[TextButton](R.id.profile_settings)

  settingsButton.onClickEvent.on(Threading.Ui) { _ =>
    navigator.goTo(SettingsViewState())
  }

  userPicture.setOnClickListener(new OnClickListener {
    override def onClick(v: View) = navigator.goTo(ProfilePictureViewState())
  })

  override def setUserName(name: String): Unit = userNameText.setText(name)

  override def setHandle(handle: String): Unit = userHandleText.setText(handle)

  override def setProfilePictureDrawable(drawable: Drawable): Unit = userPicture.setImageDrawable(drawable)

  override def setAccentColor(color: Int): Unit = {}
}
object ProfileView {
  val Tag = ZLog.logTagFor[ProfileView]
}

case class ProfileViewState() extends ViewState {

  override def name = "Profile"//TODO: resource

  override def layoutId = R.layout.preferences_profile

  var controller = Option.empty[ProfileViewController]

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[ProfileViewImpl]).map(view => new ProfileViewController(view)(view.wContext.injector, view))
  }

  override def onViewDetached() = {
    controller = None
  }
}

class ProfileViewController(view: ProfileView)(implicit inj: Injector, ec: EventContext) extends Injectable {
  val zms = inject[Signal[ZMessaging]]
  implicit val uiStorage = inject[UiStorage]

  val self = for {
    zms <- zms
    self <- UserSignal(zms.selfUserId)
  } yield self

  val selfPicture: Signal[ImageSource] = self.map(_.picture).collect{case Some(pic) => WireImage(pic)}

  view.setProfilePictureDrawable(new ImageAssetDrawable(selfPicture, scaleType = ScaleType.CenterInside, request = RequestBuilder.Round))

  self.on(Threading.Ui) { self =>
      view.setAccentColor(AccentColor(self.accent).getColor())
      self.handle.foreach(handle => view.setHandle(StringUtils.formatHandle(handle.string)))
      view.setUserName(self.getDisplayName)
  }
}
