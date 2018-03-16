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
package com.waz.zclient.conversationpager

import android.content.Intent
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.IConversation.Type
import com.waz.model.UserId
import com.waz.threading.Threading
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.connect.{ConnectRequestFragment, PendingConnectRequestManagerFragment}
import com.waz.zclient.controllers.navigation.{INavigationController, Page, PagerControllerObserver}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.connect.IConnectStore.UserRequester
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.pages.main.conversation.ConversationManagerFragment
import com.waz.zclient.ui.utils.MathUtils
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R}

class SecondPageFragment extends FragmentHelper
  with OnBackPressedListener
  with ConversationManagerFragment.Container
  with PagerControllerObserver
  with PendingConnectRequestManagerFragment.Container
  with ConnectRequestFragment.Container {

  import SecondPageFragment._
  import Threading.Implicits.Ui

  private lazy val navigationController   = inject[INavigationController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val conversationController = inject[ConversationController]

  override def setUserVisibleHint(isVisibleToUser: Boolean): Unit = {
    super.setUserVisibleHint(isVisibleToUser)
    if (isAdded) {
      val fragment = getChildFragmentManager.findFragmentById(R.id.fl__second_page_container)
      if (fragment != null) fragment.setUserVisibleHint(isVisibleToUser)
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_pager_second, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    (for {
      conv <- conversationController.currentConv
      convMembers <- conversationController.currentConvMembers
    } yield (conv.id, conv.convType, convMembers.headOption)).onUi { case (convId, convType, maybeUserId) =>
      info(s"Conversation: $convId type: $convType")

      val (page, fragment, tag) = (convType, maybeUserId) match {
        case (Type.INCOMING_CONNECTION, Some(userId)) =>
          import ConnectRequestFragment._
          (Page.CONNECT_REQUEST_INBOX, newInstance(userId), FragmentTag)
        case (Type.WAIT_FOR_CONNECTION, Some(userId)) =>
          import PendingConnectRequestManagerFragment._
          (Page.PENDING_CONNECT_REQUEST_AS_CONVERSATION, newInstance(userId, UserRequester.CONVERSATION), Tag)
        case _ =>
          import ConversationManagerFragment._
          (Page.MESSAGE_STREAM, newInstance, Tag)
      }

      info(s"openPage ${page.name} userId $maybeUserId")
      navigationController.setRightPage(Page.CONNECT_REQUEST_INBOX, SecondPageFragment.Tag)

      val transaction = getChildFragmentManager
        .beginTransaction.replace(R.id.fl__second_page_container, fragment, tag)

      val currentPage = navigationController.getCurrentPage
      if (currentPage == Page.CONVERSATION_LIST)
        transaction.setCustomAnimations(
          R.anim.message_fade_in,
          R.anim.message_fade_out,
          R.anim.message_fade_in,
          R.anim.message_fade_out
        )
      else if (currentPage == Page.CONNECT_REQUEST_INBOX || currentPage == Page.CONNECT_REQUEST_PENDING)
        transaction.setCustomAnimations(
          R.anim.fragment_animation_second_page_slide_in_from_right,
          R.anim.fragment_animation_second_page_slide_out_to_left
        )

      transaction.commit()
    }

  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    withFragment(R.id.fl__second_page_container)(_.onActivityResult(requestCode, resultCode, data))
  }

  override def onBackPressed: Boolean = {
    val fragment = getChildFragmentManager.findFragmentById(R.id.fl__second_page_container)
    fragment.isInstanceOf[OnBackPressedListener] && fragment.asInstanceOf[OnBackPressedListener].onBackPressed
  }

  override def onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int): Unit = {
    if (position == 0 || MathUtils.floatEqual(positionOffset, 0f)) getView.setAlpha(1f)
    else getView.setAlpha(Math.pow(positionOffset, 4).toFloat)
  }

  override def onPageSelected(position: Int): Unit = {
  }

  override def onPageScrollStateChanged(state: Int): Unit = {
  }

  override def onAcceptedConnectRequest(userId: UserId): Unit = {
    info(s"onAcceptedConnectRequest $userId")
    userAccountsController.getConversationId(userId).flatMap { convId =>
      conversationController.selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST)
    }
  }

  override def dismissInboxFragment(): Unit = {
    info("dismissInboxFragment")
    navigationController.setVisiblePage(Page.CONVERSATION_LIST, Tag)
  }

  override def onPagerEnabledStateHasChanged(enabled: Boolean): Unit = {
  }

  override def dismissUserProfile(): Unit = {
  }

  override def dismissSingleUserProfile(): Unit = {
  }

  override def showRemoveConfirmation(userId: UserId): Unit = {
  }
}

object SecondPageFragment {
  val Tag: String = classOf[SecondPageFragment].getName
  val ArgConversationId = "ARGUMENT_CONVERSATION_ID"

  def newInstance = new SecondPageFragment
}
