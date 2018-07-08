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
package com.waz.zclient

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.service.AccountManager
import com.waz.service.AccountManager.ClientRegistrationState.LimitReached
import com.waz.utils.events.{Signal, Subscription}
import com.waz.utils.returning
import com.waz.zclient.pages.BaseDialogFragment
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.utils.{ContextUtils, RichView}

class OtrDeviceLimitFragment extends BaseDialogFragment[OtrDeviceLimitFragment.Container] with FragmentHelper {

  private lazy val limitReached = inject[Signal[AccountManager]].flatMap(_.clientState).map {
    case LimitReached => true
    case _ => false
  }

  private lazy val logoutButton = returning(view[ZetaButton](R.id.zb__otr_device_limit__logout)) ( _.foreach { button =>
    button.setIsFilled(false)
    button.onClick { getContainer.logout() }
    button.setAccentColor(ContextUtils.getColorWithTheme(R.color.text__primary_dark, getContext))
  })

  private lazy val manageDevicesButton = returning(view[ZetaButton](R.id.zb__otr_device_limit__manage_devices)) ( _.foreach { button =>
    button.setIsFilled(true)
    button.setAccentColor(ContextUtils.getColorWithTheme(R.color.text__primary_dark, getContext))
  })

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_otr_device_limit, container, false)
  }

  private var subs = Set.empty[Subscription]

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    logoutButton.foreach(_.onClick { getContainer.logout() })
    manageDevicesButton.foreach(_.onClick { getContainer.manageDevices() })

    subs += limitReached.onUi {
      case true  =>
      case false => getContainer.dismissOtrDeviceLimitFragment()
    }
  }

  override def onDestroyView(): Unit = {
    subs.foreach(_.destroy())
    subs = Set.empty
    super.onDestroyView()
  }

  override def onBackPressed(): Boolean = true
}

object OtrDeviceLimitFragment {
  val Tag: String = classOf[OtrDeviceLimitFragment].getName

  def newInstance = new OtrDeviceLimitFragment

  trait Container {
    def logout(): Unit

    def manageDevices(): Unit

    def dismissOtrDeviceLimitFragment(): Unit
  }

}
