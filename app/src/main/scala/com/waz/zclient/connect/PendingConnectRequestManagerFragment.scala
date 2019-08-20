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
package com.waz.zclient.connect

import android.content.Context
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.model.UserId
import com.waz.service.NetworkModeService
import com.waz.utils.returning
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.messages.controllers.NavigationController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.connect.UserProfileContainer
import com.waz.zclient.participants.UserRequester
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R}

class PendingConnectRequestManagerFragment extends BaseFragment[PendingConnectRequestManagerFragment.Container]
  with FragmentHelper
  with PendingConnectRequestFragment.Container
  with OnBackPressedListener {

  import PendingConnectRequestManagerFragment._

  implicit def context: Context = getActivity

  private lazy val networkService = inject[NetworkModeService]
  private lazy val navigationController = inject[NavigationController]

  private lazy val userRequester =
    UserRequester.valueOf(getArguments.getString(ArgUserRequester))

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_connect_request_pending_manager, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    if (savedInstanceState == null) {
      val userId = UserId(getArguments.getString(PendingConnectRequestManagerFragment.ArgUserId))

      {
        import PendingConnectRequestFragment._
        getChildFragmentManager
          .beginTransaction
          .add(R.id.fl__pending_connect_request, newInstance(userId, userRequester), Tag)
          .commit
      }
    }
  }

  override def dismissUserProfile(): Unit = getContainer.dismissUserProfile()

  override def dismissSingleUserProfile(): Unit =
    if (getChildFragmentManager.popBackStackImmediate) restoreCurrentPageAfterClosingOverlay()

  override def showRemoveConfirmation(userId: UserId): Unit = {
    if (networkService.isOnlineMode) {
      getContainer.showRemoveConfirmation(userId)
    } else {
      ViewUtils.showAlertDialog(
        getActivity,
        R.string.alert_dialog__no_network__header,
        R.string.remove_from_conversation__no_network__message,
        R.string.alert_dialog__confirmation,
        null,
        true
      )
    }
  }

  private def restoreCurrentPageAfterClosingOverlay() = {
    val targetLeftPage =
      if (userRequester == UserRequester.CONVERSATION)
        Page.PENDING_CONNECT_REQUEST_AS_CONVERSATION
      else
        Page.PENDING_CONNECT_REQUEST

    inject[INavigationController].setRightPage(targetLeftPage, Tag)
  }

  override def onAcceptedConnectRequest(userId: UserId): Unit =
    getContainer.onAcceptedConnectRequest(userId)

}

object PendingConnectRequestManagerFragment {
  val Tag: String = classOf[PendingConnectRequestManagerFragment].getName
  val ArgUserId = "ARGUMENT_USER_ID"
  val ArgUserRequester = "ARGUMENT_USER_REQUESTER"

  def newInstance(userId: UserId, userRequester: UserRequester): PendingConnectRequestManagerFragment =
    returning(new PendingConnectRequestManagerFragment)(fragment =>
      fragment.setArguments(returning(new Bundle) { args =>
        args.putString(ArgUserId, userId.str)
        args.putString(ArgUserRequester, userRequester.toString)
      })
    )

  trait Container extends UserProfileContainer {
    def onAcceptedConnectRequest(userId: UserId): Unit
  }

}
