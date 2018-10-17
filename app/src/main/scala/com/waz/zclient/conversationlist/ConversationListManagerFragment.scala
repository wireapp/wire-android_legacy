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
package com.waz.zclient.conversationlist

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.{Fragment, FragmentManager}
import android.view.{LayoutInflater, ViewGroup}
import android.widget.FrameLayout
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.SyncState._
import com.waz.model._
import com.waz.model.sync.SyncCommand._
import com.waz.service.ZMessaging
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.controllers.{SoundController, ThemeController, UserAccountsController}
import com.waz.zclient.connect.{PendingConnectRequestManagerFragment, SendConnectRequestFragment}
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.confirmation._
import com.waz.zclient.controllers.navigation.{INavigationController, NavigationControllerObserver, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.connect.BlockedUserProfileFragment
import com.waz.zclient.pages.main.conversation.controller.{ConversationScreenControllerObserver, IConversationScreenController}
import com.waz.zclient.pages.main.pickuser.controller.{IPickUserController, PickUserControllerScreenObserver}
import com.waz.zclient.participants.ConversationOptionsMenuController.Mode
import com.waz.zclient.participants.{ConversationOptionsMenuController, OptionsMenu, UserRequester}
import com.waz.zclient.ui.animation.interpolators.penner.{Expo, Quart}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.usersearch.SearchUIFragment
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.views.LoadingIndicatorView
import com.waz.zclient.views.menus.ConfirmationMenu
import com.waz.zclient.{FragmentHelper, R}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class ConversationListManagerFragment extends Fragment
  with FragmentHelper
  with PickUserControllerScreenObserver
  with SearchUIFragment.Container
  with NavigationControllerObserver
  with ConversationListFragment.Container
  with ConversationScreenControllerObserver
  with SendConnectRequestFragment.Container
  with BlockedUserProfileFragment.Container
  with PendingConnectRequestManagerFragment.Container {

  import ConversationListManagerFragment._
  import Threading.Implicits.Background

  implicit lazy val context = getContext

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val userAccounts = inject[UserAccountsController]
  lazy val users        = inject[UsersController]
  lazy val themes       = inject[ThemeController]
  lazy val sounds       = inject[SoundController]
  lazy val convListController = inject[ConversationListController]

  private lazy val convController                 = inject[ConversationController]
  private lazy val accentColor                    = inject[AccentColorController]
  private lazy val pickUserController             = inject[IPickUserController]
  private lazy val navController                  = inject[INavigationController]
  private lazy val convScreenController           = inject[IConversationScreenController]
  private lazy val confirmationController         = inject[IConfirmationController]
  private lazy val cameraController               = inject[ICameraController]
  private lazy val userAccountsController         = inject[UserAccountsController]

  private var startUiLoadingIndicator: LoadingIndicatorView = _
  private var listLoadingIndicator   : LoadingIndicatorView = _
  private var mainContainer          : FrameLayout          = _
  private var confirmationMenu       : ConfirmationMenu     = _

  lazy val hasConvs = convListController.establishedConversations.map(_.nonEmpty)

  import pickUserController._

  lazy val animationType = {
    import LoadingIndicatorView._
    hasConvs.map {
      case true => InfiniteLoadingBar
      case _    => Spinner
    }
  }

  private def stripToConversationList() = {
    pickUserController.hideUserProfile() // Hide possibly open self profile
    if (pickUserController.hidePickUser()) navController.setLeftPage(Page.CONVERSATION_LIST, Tag) // Hide possibly open start ui
  }

  private def animateOnIncomingCall() = {
    Option(getView).foreach {
      _.animate
        .alpha(0)
        .setInterpolator(new Quart.EaseOut)
        .setDuration(getInt(R.integer.calling_animation_duration_medium))
        .start()
    }

    CancellableFuture.delay(getInt(R.integer.calling_animation_duration_long).millis).map { _ =>
      pickUserController.hidePickUserWithoutAnimations()
      Option(getView).foreach(_.setAlpha(1))
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    returning(inflater.inflate(R.layout.fragment_conversation_list_manager, container, false)) { view =>
      mainContainer           = findById(view, R.id.fl__conversation_list_main)
      startUiLoadingIndicator = findById(view, R.id.liv__conversations__loading_indicator)
      listLoadingIndicator    = findById(view, R.id.lbv__conversation_list__loading_indicator)
      confirmationMenu        = returning(findById[ConfirmationMenu](view, R.id.cm__confirm_action_light)) { v =>
        v.setVisible(false)
        v.resetFullScreenPadding()
      }


      if (savedInstanceState == null) {
        val fm = getChildFragmentManager
        // When re-starting app to open into specific page, child fragments may exist despite savedInstanceState == null
        if (isShowingUserProfile) hideUserProfile()
        if (isShowingPickUser()) {
          hidePickUser()
          Option(fm.findFragmentByTag(SearchUIFragment.TAG)).foreach { _ =>
            fm.popBackStack(SearchUIFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
          }
        }

        fm.beginTransaction
          .add(R.id.fl__conversation_list_main, ConversationListFragment.newNormalInstance(), NormalConversationListFragment.TAG)
          .addToBackStack(NormalConversationListFragment.TAG)
          .commit
      }

      (for {
        z        <- zms
        syncSate <- z.syncRequests.syncState(z.selfUserId, SyncMatchers)
        animType <- animationType
      } yield (syncSate, animType)).onUi { case (state, animType) =>
        state match {
          case SYNCING | WAITING => listLoadingIndicator.show(animType)
          case _                 => listLoadingIndicator.hide()
        }
      }

      convController.convChanged.map(_.requester).onUi {
        case ConversationChangeRequester.START_CONVERSATION |
             ConversationChangeRequester.START_CONVERSATION_FOR_CALL |
             ConversationChangeRequester.START_CONVERSATION_FOR_VIDEO_CALL |
             ConversationChangeRequester.START_CONVERSATION_FOR_CAMERA |
             ConversationChangeRequester.INTENT =>
          stripToConversationList()

        case ConversationChangeRequester.INCOMING_CALL =>
          stripToConversationList()
          animateOnIncomingCall()

        case _ => //
      }

      accentColor.accentColor.map(_.color).onUi { c =>
        Option(startUiLoadingIndicator).foreach(_.setColor(c))
        Option(listLoadingIndicator).foreach(_.setColor(c))
      }
    }

  override def onShowPickUser() = {
    import Page._
    navController.getCurrentLeftPage match {
      // TODO: START is set as left page on tablet, fix
      case START | CONVERSATION_LIST =>
        withFragmentOpt(SearchUIFragment.TAG) {
          case Some(_: SearchUIFragment) => // already showing
          case _ =>
            getChildFragmentManager.beginTransaction
              .setCustomAnimations(
                R.anim.slide_in_from_bottom_pick_user,
                R.anim.open_new_conversation__thread_list_out,
                R.anim.open_new_conversation__thread_list_in,
                R.anim.slide_out_to_bottom_pick_user)
              .replace(R.id.fl__conversation_list_main, SearchUIFragment.newInstance(), SearchUIFragment.TAG)
              .addToBackStack(SearchUIFragment.TAG)
              .commit
        }
      case _ => //
    }
    navController.setLeftPage(Page.PICK_USER, Tag)
  }

  override def onHidePickUser() = {
    val page = navController.getCurrentLeftPage
    import Page._

    def hide() = {
      getChildFragmentManager.popBackStackImmediate(SearchUIFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
      KeyboardUtils.hideKeyboard(getActivity)
    }

    page match {
      case SEND_CONNECT_REQUEST | BLOCK_USER | PENDING_CONNECT_REQUEST =>
        pickUserController.hideUserProfile()
        hide()
      case PICK_USER | INTEGRATION_DETAILS => hide()
      case _ => //
    }

    navController.setLeftPage(Page.CONVERSATION_LIST, Tag)
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    super.onActivityResult(requestCode, resultCode, data)
    getChildFragmentManager.getFragments.asScala.foreach(_.onActivityResult(requestCode, resultCode, data))
  }

  override def onShowUserProfile(userId: UserId) =
    if (!pickUserController.isShowingUserProfile) {
      import com.waz.api.User.ConnectionStatus._

      def show(fragment: Fragment, tag: String): Unit = {
        getChildFragmentManager
          .beginTransaction
          .setCustomAnimations(
            R.anim.fragment_animation__send_connect_request__fade_in,
            R.anim.fragment_animation__send_connect_request__zoom_exit,
            R.anim.fragment_animation__send_connect_request__zoom_enter,
            R.anim.fragment_animation__send_connect_request__fade_out)
          .replace(R.id.fl__conversation_list__profile_overlay, fragment, tag)
          .addToBackStack(tag).commit

        togglePeoplePicker(false)
      }

      zms.head.flatMap(_.usersStorage.get(userId)).foreach {
        case Some(userData) => userData.connection match {
          case CANCELLED | UNCONNECTED =>
            if (!userData.isConnected) {
              show(SendConnectRequestFragment.newInstance(userId.str, UserRequester.SEARCH), SendConnectRequestFragment.Tag)
              navController.setLeftPage(Page.SEND_CONNECT_REQUEST, Tag)
            }

          case PENDING_FROM_OTHER | PENDING_FROM_USER | IGNORED =>
            show(
              PendingConnectRequestManagerFragment.newInstance(userId, UserRequester.SEARCH),
              PendingConnectRequestManagerFragment.Tag
            )
            navController.setLeftPage(Page.PENDING_CONNECT_REQUEST, Tag)

          case BLOCKED =>
            show (
              BlockedUserProfileFragment.newInstance(userId.str, UserRequester.SEARCH),
              BlockedUserProfileFragment.Tag
            )
            navController.setLeftPage(Page.PENDING_CONNECT_REQUEST, Tag)
          case _ => //
        }
        case _ => //
      } (Threading.Ui)
    }

  private def togglePeoplePicker(show: Boolean) = {
    if (show)
      mainContainer
        .animate
        .alpha(1)
        .scaleY(1)
        .scaleX(1)
        .setInterpolator(new Expo.EaseOut)
        .setDuration(getInt(R.integer.reopen_profile_source__animation_duration))
        .setStartDelay(getInt(R.integer.reopen_profile_source__delay))
        .start()
    else
      mainContainer
        .animate
        .alpha(0)
        .scaleY(2)
        .scaleX(2)
        .setInterpolator(new Expo.EaseIn)
        .setDuration(getInt(R.integer.reopen_profile_source__animation_duration))
        .setStartDelay(0)
        .start()
  }

  override def onHideUserProfile() = {
    if (pickUserController.isShowingUserProfile) {
      getChildFragmentManager.popBackStackImmediate
      togglePeoplePicker(true)
    }
  }

  override def showIncomingPendingConnectRequest(conv: ConvId) = {
    verbose(s"showIncomingPendingConnectRequest $conv")
    pickUserController.hidePickUser()
    convController.selectConv(conv, ConversationChangeRequester.INBOX) //todo stop doing this!!!
  }

  override def getLoadingViewIndicator =
    startUiLoadingIndicator

  override def onPageVisible(page: Page) =
    if (page != Page.ARCHIVE && page != Page.CONVERSATION_MENU_OVER_CONVERSATION_LIST) closeArchive()

  override def showArchive() = {
    import Page._
    navController.getCurrentLeftPage match {
      case START | CONVERSATION_LIST =>
        withFragmentOpt(ArchiveListFragment.TAG) {
          case Some(_: ArchiveListFragment) => // already showing
          case _ =>
            getChildFragmentManager.beginTransaction
              .setCustomAnimations(
                R.anim.slide_in_from_bottom_pick_user,
                R.anim.open_new_conversation__thread_list_out,
                R.anim.open_new_conversation__thread_list_in,
                R.anim.slide_out_to_bottom_pick_user)
              .replace(R.id.fl__conversation_list_main, ConversationListFragment.newArchiveInstance(), ArchiveListFragment.TAG)
              .addToBackStack(ArchiveListFragment.TAG)
              .commit
        }
      case _ => //
    }
    navController.setLeftPage(ARCHIVE, Tag)
  }

  override def closeArchive() = {
    getChildFragmentManager.popBackStackImmediate(ArchiveListFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    if (navController.getCurrentLeftPage == Page.ARCHIVE) navController.setLeftPage(Page.CONVERSATION_LIST, Tag)
  }

  override def onStart() = {
    super.onStart()
    pickUserController.addPickUserScreenControllerObserver(this)
    convScreenController.addConversationControllerObservers(this)
    navController.addNavigationControllerObserver(this)
  }

  override def onStop() = {
    pickUserController.removePickUserScreenControllerObserver(this)
    convScreenController.removeConversationControllerObservers(this)
    navController.removeNavigationControllerObserver(this)
    super.onStop()
  }

  override def onViewStateRestored(savedInstanceState: Bundle) = {
    super.onViewStateRestored(savedInstanceState)
    import Page._
    navController.getCurrentLeftPage match { // TODO: START is set as left page on tablet, fix
      case PICK_USER =>
        pickUserController.showPickUser()
      case BLOCK_USER | PENDING_CONNECT_REQUEST | SEND_CONNECT_REQUEST | COMMON_USER_PROFILE =>
        togglePeoplePicker(false)
      case _ => //
    }
  }

  override def onBackPressed = {
    withBackstackHead {
      case Some(f: FragmentHelper) if f.onBackPressed() => true
      case _ if pickUserController.isShowingPickUser() =>
        pickUserController.hidePickUser()
        true
      case _ => false
    }
  }

  override def onAcceptedConnectRequest(userId: UserId) = {
    verbose(s"onAcceptedConnectRequest $userId")
    userAccountsController.getConversationId(userId).flatMap { convId =>
      convController.selectConv(convId, ConversationChangeRequester.START_CONVERSATION)
    }
  }

  override def onUnblockedUser(restoredConversationWithUser: ConvId) = {
    pickUserController.hideUserProfile()
    verbose(s"onUnblockedUser $restoredConversationWithUser")
    convController.selectConv(restoredConversationWithUser, ConversationChangeRequester.START_CONVERSATION)
  }

  override def onShowConversationMenu(inConvList: Boolean, convId: ConvId): Unit =
    if (inConvList) {
      OptionsMenu(getContext, new ConversationOptionsMenuController(convId, Mode.Normal(inConvList))).show()
    }

  override def dismissUserProfile() =
    pickUserController.hideUserProfile()

  override def onConnectRequestWasSentToUser() =
    pickUserController.hideUserProfile()

  override def dismissSingleUserProfile() =
    dismissUserProfile()

  override def onHideUser() = {}

  override def onHideOtrClient() = {}

  override def showRemoveConfirmation(userId: UserId) = {}
}

object ConversationListManagerFragment {
  lazy val SyncMatchers = Seq(SyncConversations, SyncSelf, SyncConnections)

  lazy val ConvListUpdateThrottling = 250.millis

  val Tag = ConversationListManagerFragment.getClass.getSimpleName

  def newInstance() = new ConversationListManagerFragment()
}
