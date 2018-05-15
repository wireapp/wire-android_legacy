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
package com.waz.zclient.participants.fragments

import android.content.Context
import android.os.Bundle
import android.support.annotation.Nullable
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.connect.{PendingConnectRequestFragment, SendConnectRequestFragment}
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.integrations.IntegrationDetailsFragment
import com.waz.zclient.pages.main.connect.BlockedUserProfileFragment
import com.waz.zclient.pages.main.conversation.controller.{ConversationScreenControllerObserver, IConversationScreenController}
import com.waz.zclient.participants.ConversationOptionsMenuController.Mode
import com.waz.zclient.participants.{ConversationOptionsMenuController, OptionsMenu, ParticipantsController}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.views.DefaultPageTransitionAnimation
import com.waz.zclient.{FragmentHelper, ManagerFragment, R}

import scala.concurrent.Future

class ParticipantFragment extends ManagerFragment
  with ConversationScreenControllerObserver
  with SendConnectRequestFragment.Container
  with BlockedUserProfileFragment.Container
  with PendingConnectRequestFragment.Container {

  import ParticipantFragment._

  implicit def ctx: Context = getActivity
  import Threading.Implicits.Ui

  override val contentId: Int = R.id.fl__participant__container

  private lazy val bodyContainer             = view[View](R.id.fl__participant__container)
  private lazy val participantsContainerView = view[View](R.id.ll__participant__container)

  private lazy val convController         = inject[ConversationController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val screenController       = inject[IConversationScreenController]
  private lazy val singleImageController  = inject[ISingleImageController]
  private lazy val userAccountsController = inject[UserAccountsController]

  private lazy val headerFragment = ParticipantHeaderFragment.newInstance

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation =
    if (nextAnim == 0 || getParentFragment == null)
      super.onCreateAnimation(transit, enter, nextAnim)
    else new DefaultPageTransitionAnimation(
      0,
      getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
      enter,
      getInt(R.integer.framework_animation_duration_medium),
      if (enter) getInt(R.integer.framework_animation_duration_medium) else 0,
      1f
    )

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    returning(inflater.inflate(R.layout.fragment_participant, container, false)) { _ =>
      withFragment(R.id.fl__participant__overlay)(getChildFragmentManager.beginTransaction.remove(_).commit)
    }

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {

    withFragmentOpt(R.id.fl__participant__container) {
      case Some(_) => //no action to take, view was already set
      case _ =>
        (getStringArg(PageToOpenArg) match {
          case Some(GuestOptionsFragment.Tag) => Future.successful((new GuestOptionsFragment, GuestOptionsFragment.Tag))
          case Some(SingleParticipantFragment.TagDevices) => Future.successful((SingleParticipantFragment.newInstance(Some(SingleParticipantFragment.TagDevices)), SingleParticipantFragment.Tag))
          case _ =>
            participantsController.isGroupOrBot.head.map {
              case true => (GroupParticipantsFragment.newInstance(), GroupParticipantsFragment.Tag)
              case false => (SingleParticipantFragment.newInstance(), SingleParticipantFragment.Tag)
            }
        }).map {
          case (f, tag) =>
            getChildFragmentManager.beginTransaction
              .replace(R.id.fl__participant__header__container, headerFragment, ParticipantHeaderFragment.TAG)
              .replace(R.id.fl__participant__container, f, tag)
              .addToBackStack(tag)
              .commit
        }
    }

    bodyContainer
    participantsContainerView
  }

  override def onStart(): Unit = {
    super.onStart()
    screenController.addConversationControllerObservers(this)
  }

  override def onStop(): Unit = {
    screenController.removeConversationControllerObservers(this)
    super.onStop()
  }

  override def onDestroyView(): Unit = {
    singleImageController.clearReferences()
    super.onDestroyView()
  }

  override def onBackPressed(): Boolean = {
    withFragmentOpt(R.id.fl__participant__overlay) {
      case Some(f: SingleOtrClientFragment) if f.onBackPressed() => true
      case _ =>
        withContentFragment {
          case _ if screenController.isShowingUser =>
            verbose(s"onBackPressed with screenController.isShowingUser")
            screenController.hideUser()
            participantsController.unselectParticipant()
            true
          case Some(f: FragmentHelper) if f.onBackPressed() => true
          case Some(_: FragmentHelper) =>
            if (getChildFragmentManager.getBackStackEntryCount <= 1) participantsController.onHideParticipants ! true
            else getChildFragmentManager.popBackStack()
            true
          case _ =>
            warn("OnBackPressed was not handled anywhere")
            false
        }
    }
  }

  override def onShowConversationMenu(inConvList: Boolean, convId: ConvId): Unit =
    if (!inConvList) OptionsMenu(getContext, new ConversationOptionsMenuController(convId, Mode.Normal(inConvList))).show()

  def showOtrClient(userId: UserId, clientId: ClientId): Unit =
    getChildFragmentManager
      .beginTransaction
      .setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_out_to_left,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_out_to_right)
      .add(
        R.id.fl__participant__overlay,
        SingleOtrClientFragment.newInstance(userId, clientId),
        SingleOtrClientFragment.Tag
      )
      .addToBackStack(SingleOtrClientFragment.Tag)
      .commit

  def showCurrentOtrClient(): Unit =
    getChildFragmentManager
      .beginTransaction
      .setCustomAnimations(
        R.anim.slide_in_from_bottom_pick_user,
        R.anim.open_new_conversation__thread_list_out,
        R.anim.open_new_conversation__thread_list_in,
        R.anim.slide_out_to_bottom_pick_user)
      .add(
        R.id.fl__participant__overlay,
        SingleOtrClientFragment.newInstance,
        SingleOtrClientFragment.Tag
      )
      .addToBackStack(SingleOtrClientFragment.Tag)
      .commit

  // TODO: AN-5980
  def showIntegrationDetails(pId: ProviderId, iId: IntegrationId): Unit = {
    getChildFragmentManager
      .beginTransaction
      .setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_out_to_left,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_out_to_right
      )
      .replace(
        R.id.fl__participant__overlay,
        IntegrationDetailsFragment.newInstance(pId, iId, isTransparent = false),
        IntegrationDetailsFragment.Tag
      )
      .addToBackStack(IntegrationDetailsFragment.Tag)
      .commit
  }

  override def onHideUser(): Unit = if (screenController.isShowingUser) {
    getChildFragmentManager.popBackStack()
  }

  override def showRemoveConfirmation(userId: UserId): Unit =
    participantsController.showRemoveConfirmation(userId)

  override def dismissUserProfile(): Unit = screenController.hideUser()

  override def dismissSingleUserProfile(): Unit = dismissUserProfile()

  override def onAcceptedConnectRequest(userId: UserId): Unit = {
    screenController.hideUser()
    verbose(s"onAcceptedConnectRequest $userId")
    userAccountsController.getConversationId(userId).flatMap { convId =>
      convController.selectConv(convId, ConversationChangeRequester.START_CONVERSATION)
    }
  }

  override def onUnblockedUser(restoredConversationWithUser: ConvId): Unit = {
    screenController.hideUser()
    verbose(s"onUnblockedUser $restoredConversationWithUser")
    convController.selectConv(restoredConversationWithUser, ConversationChangeRequester.START_CONVERSATION)
  }

  override def onConnectRequestWasSentToUser(): Unit = screenController.hideUser()

  override def onHideOtrClient(): Unit = getChildFragmentManager.popBackStack()

}

object ParticipantFragment {
  val TAG: String = classOf[ParticipantFragment].getName
  private val PageToOpenArg = "ARG__FIRST__PAGE"

  def newInstance(page: Option[String]): ParticipantFragment =
    returning(new ParticipantFragment) { f =>
      page.foreach { p =>
        f.setArguments(returning(new Bundle)(_.putString(PageToOpenArg, p)))
      }
    }

}
