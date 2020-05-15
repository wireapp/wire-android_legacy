/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.{LayoutInflater, MenuItem, View, ViewGroup}
import android.widget.FrameLayout
import androidx.annotation.Nullable
import androidx.fragment.app.{Fragment, FragmentManager}
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.waz.api.ErrorType
import com.waz.api.SyncState._
import com.waz.content.{UserPreferences, UsersStorage}
import com.waz.model._
import com.waz.model.sync.SyncCommand._
import com.waz.service.ZMessaging
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{Signal, Subscription}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.navigation.{INavigationController, NavigationControllerObserver, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.folders.moveto.MoveToFolderActivity
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.main.conversation.controller.{ConversationScreenControllerObserver, IConversationScreenController}
import com.waz.zclient.pages.main.pickuser.controller.{IPickUserController, PickUserControllerScreenObserver}
import com.waz.zclient.participants.ConversationOptionsMenuController.Mode
import com.waz.zclient.participants.fragments.{BlockedUserFragment, ConnectRequestFragment, PendingConnectRequestFragment, SendConnectRequestFragment}
import com.waz.zclient.participants.{ConversationOptionsMenuController, OptionsMenu, UserRequester}
import com.waz.zclient.ui.animation.interpolators.penner.{Expo, Quart}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.usersearch.SearchUIFragment
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.utils.extensions.BottomNavigationUtil
import com.waz.zclient.views.LoadingIndicatorView
import com.waz.zclient.views.LoadingIndicatorView.{InfiniteLoadingBar, Spinner}
import com.waz.zclient.views.menus.ConfirmationMenu
import com.waz.zclient.{ErrorsController, FragmentHelper, R}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

class ConversationListManagerFragment extends Fragment
  with FragmentHelper
  with PickUserControllerScreenObserver
  with SearchUIFragment.Container
  with NavigationControllerObserver
  with ConversationListFragment.Container
  with ConversationScreenControllerObserver
  with BottomNavigationView.OnNavigationItemSelectedListener {

  import ConversationListManagerFragment._
  import Threading.Implicits.Ui

  implicit lazy val context = getContext

  private lazy val convController       = inject[ConversationController]
  private lazy val pickUserController   = inject[IPickUserController]
  private lazy val navController        = inject[INavigationController]
  private lazy val convScreenController = inject[IConversationScreenController]
  private lazy val convListController   = inject[ConversationListController]
  private lazy val errorsController     = inject[ErrorsController]

  private var startUiLoadingIndicator: LoadingIndicatorView = _
  private var listLoadingIndicator: LoadingIndicatorView = _
  private var mainContainer: FrameLayout = _
  private var confirmationMenu: ConfirmationMenu = _

  private lazy val bottomNavigationBorder = view[View](R.id.fragment_conversation_list_manager_view_bottom_border)

  protected var subs = Set.empty[Subscription]

  private lazy val bottomNavigationView = returning(view[BottomNavigationView](R.id.fragment_conversation_list_manager_bottom_navigation)) { vh =>
    subs += convListController.hasConversationsAndArchive.onUi { case (_, hasArchive) =>
      vh.foreach(view => BottomNavigationUtil.setItemVisible(view, R.id.navigation_archive, hasArchive))
    }
  }

  lazy val zms = inject[Signal[ZMessaging]]

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
    inflater.inflate(R.layout.fragment_conversation_list_manager, container, false)

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    mainContainer = findById(view, R.id.fl__conversation_list_main)
    startUiLoadingIndicator = findById(view, R.id.liv__conversations__loading_indicator)
    listLoadingIndicator = findById(view, R.id.lbv__conversation_list__loading_indicator)
    confirmationMenu = returning(findById[ConfirmationMenu](view, R.id.cm__confirm_action_light)) { v =>
      v.setVisible(false)
      v.resetFullScreenPadding()
    }

    bottomNavigationView.foreach(_.setOnNavigationItemSelectedListener(ConversationListManagerFragment.this))
    bottomNavigationBorder

    if (savedInstanceState == null) {
      val fm = getChildFragmentManager
      // When re-starting app to open into specific page, child fragments may exist despite savedInstanceState == null
      if (pickUserController.isShowingUserProfile) pickUserController.hideUserProfile()
      if (pickUserController.isShowingPickUser()) {
        pickUserController.hidePickUser()
        Option(fm.findFragmentByTag(SearchUIFragment.TAG)).foreach { _ =>
          fm.popBackStack(SearchUIFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
      }

      selectDefaultConversationType()
    }

    (for {
      z        <- inject[Signal[ZMessaging]]
      syncSate <- z.syncRequests.syncState(z.selfUserId, SyncMatchers)
      animType <- inject[ConversationListController].establishedConversations.map(_.nonEmpty).map {
        case true => InfiniteLoadingBar
        case _    => Spinner
      }
    } yield (syncSate, animType)).onUi { case (state, animType) =>
      state match {
        case SYNCING | WAITING => listLoadingIndicator.show(animType)
        case _                 => listLoadingIndicator.hide()
      }
    }

    subs += convController.convChanged.map(_.requester).onUi {
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

    subs += inject[AccentColorController].accentColor.map(_.color).onUi { c =>
      Option(startUiLoadingIndicator).foreach(_.setColor(c))
      Option(listLoadingIndicator).foreach(_.setColor(c))
      setUpBottomNavigationTintColors(c)
    }

    subs += zms.flatMap(_.errors.getErrors).onUi {
      _.foreach(err => if (err.errType == ErrorType.CANNOT_DELETE_GROUP_CONVERSATION) handleGroupConvError(err))
    }
  }

  override def onDestroyView(): Unit = {
    super.onDestroyView()
    bottomNavigationView.foreach(_.setOnNavigationItemSelectedListener(null))
  }

  override def onDestroy(): Unit = {
    subs.foreach(_.destroy())
    subs = Set.empty
    super.onDestroy()
  }

  private def setUpBottomNavigationTintColors(color: Int): Unit = {
    import android.content.res.ColorStateList
    import android.graphics.Color

    val states = Array[Array[Int]](Array[Int](android.R.attr.state_checked), Array[Int]())
    val colors = Array[Int](color, Color.WHITE)

    val colorStateList = new ColorStateList(states, colors)
    bottomNavigationView.foreach(_.setItemIconTintList(colorStateList))
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
      case SEND_CONNECT_REQUEST | PENDING_CONNECT_REQUEST =>
        pickUserController.hideUserProfile()
        hide()
      case PICK_USER | INTEGRATION_DETAILS => hide()
      case _ => //
    }

    navController.setLeftPage(Page.CONVERSATION_LIST, Tag)
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == MoveToFolderActivity.REQUEST_CODE_MOVE_CREATE) {
      if (resultCode == Activity.RESULT_OK) {
        val movedConvId = data.getSerializableExtra(MoveToFolderActivity.KEY_CONV_ID).asInstanceOf[ConvId]
        //TODO: scroll to conv w/ this id
      }
    } else {
      getChildFragmentManager.getFragments.asScala.foreach(_.onActivityResult(requestCode, resultCode, data))
    }
  }

  override def onShowUserProfile(userId: UserId, fromDeepLink: Boolean) =
    if (!pickUserController.isShowingUserProfile) {
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

      (for {
        usersStorage  <- inject[Signal[UsersStorage]].head
        user          <- usersStorage.get(userId)
        userRequester =  if (fromDeepLink) UserRequester.DEEP_LINK else UserRequester.SEARCH
      } yield (user, userRequester)).foreach { case (Some(userData), userRequester) =>
        import com.waz.api.ConnectionStatus._
        userData.connection match {
          case CANCELLED | UNCONNECTED =>
              show(SendConnectRequestFragment.newInstance(userId, userRequester), SendConnectRequestFragment.Tag)
              navController.setLeftPage(Page.SEND_CONNECT_REQUEST, Tag)

          case PENDING_FROM_OTHER  =>
            show(
              ConnectRequestFragment.newInstance(userId, userRequester),
              ConnectRequestFragment.Tag
            )
            navController.setLeftPage(Page.PENDING_CONNECT_REQUEST, Tag)

          case PENDING_FROM_USER | IGNORED =>
            show(
              PendingConnectRequestFragment.newInstance(userId, userRequester),
              PendingConnectRequestFragment.Tag
            )
            navController.setLeftPage(Page.PENDING_CONNECT_REQUEST, Tag)

          case BLOCKED =>
            show(
              BlockedUserFragment.newInstance(userId, userRequester),
              BlockedUserFragment.Tag
            )
            navController.setLeftPage(Page.PENDING_CONNECT_REQUEST, Tag)
          case _ => //
        }
      case _ => //
      }
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
    verbose(l"showIncomingPendingConnectRequest $conv")
    pickUserController.hidePickUser()
    convController.selectConv(conv, ConversationChangeRequester.INBOX) //todo stop doing this!!!
  }

  override def getLoadingViewIndicator =
    startUiLoadingIndicator

  override def onPageVisible(page: Page) = {
    if (page != Page.ARCHIVE) closeArchive()

    val conversationsVisible = page == Page.START || page == Page.CONVERSATION_LIST
    bottomNavigationView.foreach(_.setVisible(conversationsVisible))
    bottomNavigationBorder.foreach(_.setVisible(conversationsVisible))
    if (conversationsVisible) {
      selectDefaultConversationType()
    }
  }

  private def showArchive() = {
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

  override def onConversationsLoadingStarted(): Unit =
    bottomNavigationView.foreach(_.setAlpha(0.5f))

  override def onConversationsLoadingFinished(): Unit = {
    bottomNavigationView.foreach(_.animate().alpha(1f).setDuration(500))
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
      case PENDING_CONNECT_REQUEST | SEND_CONNECT_REQUEST =>
        togglePeoplePicker(false)
      case _ => //
    }
  }

  override def onBackPressed() = {
    withBackstackHead {
      case Some(f: FragmentHelper) if f.onBackPressed() => true
      case _ if pickUserController.isShowingPickUser() =>
        pickUserController.hidePickUser()
        true
      case _ => false
    }
  }

  override def onShowConversationMenu(inConvList: Boolean, convId: ConvId): Unit =
    if (inConvList) {
      OptionsMenu(getContext, new ConversationOptionsMenuController(convId, Mode.Normal(inConvList))).show()
    }

  override def onHideUser() = {}

  override def onHideOtrClient() = {}

  override def onNavigationItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case R.id.navigation_search =>
        pickUserController.showPickUser()
        false
      case R.id.navigation_conversations => replaceConversationFragment(
        NormalConversationFragment.TAG,
        ConversationListType.RECENTS)
        true
      case R.id.navigation_folders => replaceConversationFragment(
        ConversationFolderListFragment.TAG,
        ConversationListType.FOLDERS)
        true
      case R.id.navigation_archive =>
        showArchive()
        false
    }
  }

  private def replaceConversationFragment(tag: String, @ConversationListType listType: Int): Unit = {
    val fragment = Option(getChildFragmentManager.findFragmentByTag(tag)).getOrElse {
      if (tag == NormalConversationFragment.TAG) ConversationListFragment.newNormalInstance()
      else if (tag == ConversationFolderListFragment.TAG) ConversationListFragment.newFoldersInstance()
      else {
        error(l"Unexpected Fragment tag: $tag, defaulting to the normal instance")
        ConversationListFragment.newNormalInstance()
      }
    }

    getChildFragmentManager.beginTransaction
      .replace(R.id.fl__conversation_list_main, fragment, tag)
      .addToBackStack(tag)
      .commit
    setConversationListType(listType)

    if (navController.getCurrentLeftPage != Page.START) navController.setLeftPage(Page.CONVERSATION_LIST, Tag)
  }

  private def selectDefaultConversationType(): Unit = bottomNavigationView.foreach { view =>
    getConversationListType().map {
      case ConversationListType.FOLDERS => R.id.navigation_folders
      case _ => R.id.navigation_conversations
    }.foreach(view.setSelectedItemId)
  }

  private def setConversationListType(@ConversationListType listType: Int): Unit =
    for {
      userPrefs <- zms.map(_.userPrefs).head
      convListTypePreference = userPrefs.preference(UserPreferences.ConversationListType)
    } yield {
      convListTypePreference.update(listType)
    }

  private def getConversationListType(): Future[Int] =
    for {
      userPrefs <- zms.map(_.userPrefs).head
      convListTypePreference = userPrefs.preference(UserPreferences.ConversationListType)
      convListType <- convListTypePreference.apply()
    } yield {
      convListType
    }

  override def onMoveToFolder(convId: ConvId): Unit = {
    startActivityForResult(
      MoveToFolderActivity.newIntent(requireContext(), convId),
      MoveToFolderActivity.REQUEST_CODE_MOVE_CREATE
    )
  }

  private def handleGroupConvError(errorData: ErrorData) = {
    errorsController.dismissSyncError(errorData.id)
    errorData.convId.fold(showDefaultGroupConvDeleteError())(cId =>
      convController.conversationData(cId).head.flatMap {
        case Some(data) if data.name.nonEmpty => showErrorDialog(
          getString(R.string.delete_group_conversation_error_title),
          getString(R.string.delete_group_conversation_error_message_with_group_name, data.displayName))
          Future.successful(())
        case _ => showDefaultGroupConvDeleteError()
          Future.successful(())
      }.recoverWith {
        case ex: Exception =>
          error(l"Error while fetching deleted conversation name. Conv id: ${errorData.convId}", ex)
          Future.successful(())
      }
    )
  }

  private def showDefaultGroupConvDeleteError(): Unit = {
    showErrorDialog(
      R.string.delete_group_conversation_error_title,
      R.string.delete_group_conversation_error_message
    )
  }
}

object ConversationListManagerFragment {
  lazy val SyncMatchers = Seq(SyncConversations, SyncSelf, SyncConnections)

  lazy val ConvListUpdateThrottling = 250.millis

  val Tag = ConversationListManagerFragment.getClass.getSimpleName

  def newInstance() = new ConversationListManagerFragment()
}
