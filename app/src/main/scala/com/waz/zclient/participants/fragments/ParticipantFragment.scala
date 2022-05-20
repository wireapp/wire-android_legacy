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
import androidx.annotation.Nullable
import androidx.fragment.app.{Fragment, FragmentTransaction}
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.integrations.IntegrationDetailsFragment
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.main.conversation.controller.{ConversationScreenControllerObserver, IConversationScreenController}
import com.waz.zclient.participants.ConversationOptionsMenuController.Mode
import com.waz.zclient.participants.{ConversationOptionsMenuController, OptionsMenu, ParticipantsController, UserRequester}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.views.DefaultPageTransitionAnimation
import com.waz.zclient.{FragmentHelper, ManagerFragment, R}
import com.waz.api.ConnectionStatus._
import com.waz.zclient.legalhold.{AllLegalHoldSubjectsFragment, LegalHoldController, LegalHoldInfoFragment}
import com.waz.zclient.messages.UsersController
import com.waz.zclient.utils.ContextUtils

import scala.concurrent.Future

final class ParticipantFragment extends ManagerFragment with ConversationScreenControllerObserver {

  import ParticipantFragment._

  implicit def ctx: Context = getActivity
  import Threading.Implicits.Ui

  override val contentId: Int = R.id.fl__participant__container

  private lazy val bodyContainer             = view[View](R.id.fl__participant__container)
  private lazy val participantsContainerView = view[View](R.id.ll__participant__container)

  private lazy val usersController        = inject[UsersController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val screenController       = inject[IConversationScreenController]
  private lazy val singleImageController  = inject[ISingleImageController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val convScreenController   = inject[IConversationScreenController]
  private lazy val legalHoldController    = inject[LegalHoldController]

  private lazy val headerFragment = ParticipantHeaderFragment.newInstance(fromDeepLink = getBooleanArg(FromDeepLinkArg))

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
      withChildFragment(R.id.fl__participant__overlay)(getChildFragmentManager.beginTransaction.remove(_).commit)
    }

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    verbose(l"onViewCreated.")

    withChildFragmentOpt(R.id.fl__participant__container) {
      case Some(_) => //no action to take, view was already set
      case _ =>
        (getStringArg(PageToOpenArg) match {
          case Some(GuestOptionsFragment.Tag) =>
            Future.successful((new GuestOptionsFragment, GuestOptionsFragment.Tag))
          case Some(SingleParticipantFragment.DevicesTab.str) =>
            Future.successful((SingleParticipantFragment.newInstance(Some(SingleParticipantFragment.DevicesTab.str)), SingleParticipantFragment.Tag))
          case Some(LegalHoldInfoFragment.Tag) =>
            createLegalHoldInfoFragment.map((_, LegalHoldInfoFragment.Tag))
          case _ =>
            participantsController.flags.head.map {
              case flags if (flags.isGroup || flags.hasBot) && getStringArg(UserToOpenArg).isEmpty =>
                (GroupParticipantsFragment.newInstance(), GroupParticipantsFragment.Tag)
              case _ =>
                (SingleParticipantFragment.newInstance(fromDeepLink = getBooleanArg(FromDeepLinkArg)), SingleParticipantFragment.Tag)
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

    participantsController.onShowUser.onUi {
      case Some(userId) => showUser(userId)
      case _ =>
    }

    legalHoldController.onLegalHoldSubjectClick.onUi { userId => showUser(userId, forLegalHold = true) }
    legalHoldController.onAllLegalHoldSubjectsClick.onUi { _ => showAllLegalHoldSubjects() }
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
    withChildFragmentOpt(R.id.fl__participant__overlay) {
      case Some(f: SingleOtrClientFragment) if f.onBackPressed() => true
      case _ =>
        withContentFragment {
          case _ if screenController.isShowingUser =>
            verbose(l"onBackPressed with screenController.isShowingUser")
            screenController.hideUser()
            participantsController.unselectParticipant()
            true
          case Some(f: FragmentHelper) if f.onBackPressed() => true
          case Some(_: FragmentHelper) =>
            if (getChildFragmentManager.getBackStackEntryCount <= 1) participantsController.onLeaveParticipants ! true
            else getChildFragmentManager.popBackStack()
            true
          case _ =>
            warn(l"OnBackPressed was not handled anywhere")
            false
        }
    }
  }

  override def onShowConversationMenu(inConvList: Boolean, convId: ConvId): Unit =
    if (!inConvList) {
      val fromDeepLink = getBooleanArg(FromDeepLinkArg)
      val controller = new ConversationOptionsMenuController(convId, Mode.Normal(inConvList), fromDeepLink)
      OptionsMenu(getContext, controller).show()
    }

  def showOtrClient(userId: UserId, clientId: ClientId): Unit =
    withSlideAnimation(getChildFragmentManager.beginTransaction)
      .add(
        R.id.fl__participant__overlay,
        SingleOtrClientFragment.newInstance(userId, clientId),
        SingleOtrClientFragment.Tag)
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
  def showIntegrationDetails(service: IntegrationData, convId: ConvId, userId: UserId): Unit = {
    withSlideAnimation(getChildFragmentManager.beginTransaction)
      .replace(
        R.id.fl__participant__overlay,
        IntegrationDetailsFragment.newRemovingInstance(service, convId, userId),
        IntegrationDetailsFragment.Tag)
      .addToBackStack(IntegrationDetailsFragment.Tag)
      .commit
  }

  private def openUserProfileFragment(fragment: Fragment, tag: String) =
    withSlideAnimation(getChildFragmentManager.beginTransaction)
      .replace(R.id.fl__participant__container, fragment, tag)
      .addToBackStack(tag)
      .commit

  def openLegalHoldInfoScreen(): Unit =
    createLegalHoldInfoFragment.foreach(frag =>
      getChildFragmentManager.beginTransaction
        .setCustomAnimations(
          R.anim.slide_in_from_bottom_pick_user,
          R.anim.open_new_conversation__thread_list_out,
          R.anim.open_new_conversation__thread_list_in,
          R.anim.slide_out_to_bottom_pick_user)
        .replace(
          R.id.fl__participant__container,
          frag, LegalHoldInfoFragment.Tag
        )
        .addToBackStack(LegalHoldInfoFragment.Tag)
        .commit
    )

  private def createLegalHoldInfoFragment: Future[LegalHoldInfoFragment] =
    participantsController.conv.head.map(conv => LegalHoldInfoFragment.newInstance(Some(conv.id)))

  private def showAllLegalHoldSubjects(): Unit =
    withSlideAnimation(getChildFragmentManager.beginTransaction)
      .replace(R.id.fl__participant__container,
        AllLegalHoldSubjectsFragment.newInstance(),
        AllLegalHoldSubjectsFragment.Tag)
      .addToBackStack(AllLegalHoldSubjectsFragment.Tag)
      .commit

  private def withSlideAnimation(transaction: FragmentTransaction): FragmentTransaction =
    returning(transaction) {
      _.setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_out_to_left,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_out_to_right)
    }

  private def showUser(userId: UserId, forLegalHold: Boolean = false): Unit = usersController.syncUserAndCheckIfDeleted(userId).foreach {
    case (Some(user), None) =>
      ContextUtils.showToast(getString(R.string.participant_was_removed_from_team, user.name.str))
    case (None, None) =>
      warn(l"Trying to show a non-existing user with id $userId")
    case _ =>
      convScreenController.showUser(userId)
      participantsController.selectParticipant(userId)

      KeyboardUtils.hideKeyboard(getActivity)

      for {
        userOpt      <- participantsController.getUser(userId)
        isTeamMember <- userAccountsController.isTeamMember(userId).head
      } userOpt match {
        case Some(user) if user.connection == ACCEPTED || user.expiresAt.isDefined || isTeamMember =>
          import SingleParticipantFragment._
          val tabToOpen = if (forLegalHold) Some(DevicesTab.str) else None
          openUserProfileFragment(newInstance(tabToOpen), Tag)

        case Some(user) if user.connection == PENDING_FROM_USER || user.connection == IGNORED =>
          import PendingConnectRequestFragment._
          openUserProfileFragment(newInstance(userId, UserRequester.PARTICIPANTS), Tag)

        case Some(user) if user.connection == PENDING_FROM_OTHER =>
          import ConnectRequestFragment._
          openUserProfileFragment(newInstance(userId,UserRequester.PARTICIPANTS), Tag)

        case Some(user) if user.connection == BLOCKED =>
          import BlockedUserFragment._
          openUserProfileFragment(newInstance(userId, UserRequester.PARTICIPANTS), Tag)

        case Some(user) if user.connection == CANCELLED || user.connection == UNCONNECTED =>
          import SendConnectRequestFragment._
          openUserProfileFragment(newInstance(userId, UserRequester.PARTICIPANTS), Tag)

        case _ =>
      }
  }

  override def onHideUser(): Unit = if (screenController.isShowingUser) {
    getChildFragmentManager.popBackStack()
  }

  override def onHideOtrClient(): Unit = getChildFragmentManager.popBackStack()

  override def onMoveToFolder(convId: ConvId): Unit = {
    //no-op
  }

}

object ParticipantFragment {
  val TAG: String = classOf[ParticipantFragment].getName
  private val PageToOpenArg = "ARG__FIRST__PAGE"
  private val UserToOpenArg = "ARG__USER"
  private val FromDeepLinkArg = "ARG__FROM__DEEP__LINK"

  def newInstance(page: Option[String]): ParticipantFragment =
    returning(new ParticipantFragment) { f =>
      page.foreach { p =>
        f.setArguments(returning(new Bundle)(_.putString(PageToOpenArg, p)))
      }
    }

  def newInstance(userId: UserId, fromDeepLink: Boolean = false): ParticipantFragment =
    returning(new ParticipantFragment) { f =>
      f.setArguments(returning(new Bundle) { b =>
        b.putString(UserToOpenArg, userId.str)
        b.putBoolean(FromDeepLinkArg, fromDeepLink)
      })
    }

}
