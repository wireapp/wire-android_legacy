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
package com.waz.zclient.pages.main.profile.preferences.fragments

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.ImageView
import com.waz.ZLog
import com.waz.api.impl.AccentColor
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient._
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.profile.preferences.fragments.ProfileFragment._
import com.waz.zclient.pages.main.profile.preferences.views.TextButton
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.{StringUtils, UiStorage, UserSignal}
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.views.ImageController.{ImageSource, WireImage}


trait ProfileView {
  def setUserName(name: String): Unit
  def setHandle(handle: String): Unit
  def setProfilePictureDrawable(drawable: Drawable): Unit
  def setAccentColor(color: Int): Unit
  def setNewDevicesCount(count: Int): Unit
}

class ProfileFragment extends BaseFragment[Container] with ProfileView with FragmentHelper {

  def userNameText = findById[TypefaceTextView](R.id.profile_user_name)
  def userPicture = findById[ImageView](R.id.profile_user_picture)
  def userHandleText = findById[TypefaceTextView](R.id.profile_user_handle)

  def devicesButton = findById[TextButton](R.id.profile_devices)
  def settingsButton = findById[TextButton](R.id.profile_settings)
  def inviteButton = findById[TextButton](R.id.profile_invite)

  lazy val controller = new ProfileFragmentController()

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    inflater.inflate(R.layout.preferences_profile_layout, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    controller.view ! this
    devicesButton.onClickEvent.on(Threading.Ui) { _ =>
      PreferencesViewsManager(getActivity.asInstanceOf[BaseActivity]).openView(SettingsFragment.Tag)
    }
    settingsButton.onClickEvent.on(Threading.Ui) { _ =>
      PreferencesViewsManager(getActivity.asInstanceOf[BaseActivity]).openView(SettingsFragment.Tag)
    }
    inviteButton.onClickEvent.on(Threading.Ui) { _ =>
      PreferencesViewsManager(getActivity.asInstanceOf[BaseActivity]).openView(SettingsFragment.Tag)
    }
  }

  override def setUserName(name: String): Unit = userNameText.setText(name)

  override def setHandle(handle: String): Unit = userHandleText.setText(handle)

  override def setProfilePictureDrawable(drawable: Drawable): Unit = userPicture.setImageDrawable(drawable)

  override def setAccentColor(color: Int): Unit = {
    inviteButton.setBackgroundColor(AccentColor(color).getColor())
  }

  override def setNewDevicesCount(count: Int): Unit = {}
}
object ProfileFragment {
  val Tag = ZLog.logTagFor[ProfileFragment]

  trait Container
}

class ProfileFragmentController()(implicit inj: Injector, ec: EventContext) extends Injectable {
  val zms = inject[Signal[ZMessaging]]
  implicit val uiStorage = inject[UiStorage]

  val view = Signal[ProfileView]()

  val self = for {
    zms <- zms
    self <- UserSignal(zms.selfUserId)
    view <- view
  } yield self

  val selfPicture: Signal[ImageSource] = self.map(_.picture).collect{case Some(pic) => WireImage(pic)}

  view.on(Threading.Ui) {
    _.setProfilePictureDrawable(new ImageAssetDrawable(selfPicture, scaleType = ScaleType.CenterInside, request = RequestBuilder.Round))
  }

  Signal(self, view).on(Threading.Ui) {
    case (self, view) =>
      view.setAccentColor(self.accent)
      self.handle.foreach(handle => view.setHandle(StringUtils.formatHandle(handle.string)))
      view.setUserName(self.getDisplayName)
  }
}
