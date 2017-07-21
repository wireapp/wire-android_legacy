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
package com.waz.zclient.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.content.{DialogInterface, Intent}
import android.net.Uri
import android.os.{Bundle, Handler}
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.{LinearLayoutManager, RecyclerView, Toolbar}
import android.text.TextUtils
import android.view._
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import android.widget._
import com.waz.ZLog
import com.waz.api._
import com.waz.content.UserPreferences
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.service.{SearchState, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.adapters.{PickUsersAdapter, SearchResultOnItemTouchListener}
import com.waz.zclient.controllers.currentfocus.IFocusController
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver
import com.waz.zclient.controllers.navigation.NavigationController
import com.waz.zclient.controllers.permission.RequestPermissionsObserver
import com.waz.zclient.controllers.tracking.events.connect.{EnteredSearchEvent, OpenedConversationEvent, OpenedGenericInviteMenuEvent, SentConnectRequestEvent}
import com.waz.zclient.controllers.tracking.screens.ApplicationScreen
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.controllers.{SearchUserController, ThemeController, UserAccountsController}
import com.waz.zclient.core.controllers.tracking.attributes.ConversationType
import com.waz.zclient.core.stores.conversation.{ConversationChangeRequester, InboxLoadRequester, OnInboxLoadedListener}
import com.waz.zclient.core.stores.network.DefaultNetworkAction
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.ui.animation.fragment.FadeAnimation
import com.waz.zclient.ui.startui.{ConversationQuickMenu, ConversationQuickMenuCallback}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.device.DeviceDetector
import com.waz.zclient.utils.{IntentUtils, LayoutSpec, PermissionUtils, StringUtils, TrackingUtils, UiStorage, UserSignal, ViewUtils}
import com.waz.zclient.views._
import com.waz.zclient.views.pickuser.{ContactRowView, SearchBoxView, UserRowView}
import com.waz.zclient.{BaseActivity, FragmentHelper, OnBackPressedListener, R}

import scala.collection.JavaConverters._

object PickUserFragment {
  val TAG: String = classOf[PickUserFragment].getName
  val ARGUMENT_ADD_TO_CONVERSATION: String = "ARGUMENT_ADD_TO_CONVERSATION"
  val ARGUMENT_GROUP_CONVERSATION: String = "ARGUMENT_GROUP_CONVERSATION"
  val ARGUMENT_CONVERSATION_ID: String = "ARGUMENT_CONVERSATION_ID"
  val NUM_SEARCH_RESULTS_LIST: Int = 30
  val NUM_SEARCH_RESULTS_TOP_USERS: Int = 24
  val NUM_SEARCH_RESULTS_ADD_TO_CONV: Int = 1000
  private val DEFAULT_SELECTED_INVITE_METHOD: Int = 0
  private val SHOW_KEYBOARD_THRESHOLD: Int = 10

  def newInstance(addToConversation: Boolean, conversationId: String): PickUserFragment = {
    newInstance(addToConversation, groupConversation = false, conversationId)
  }

  def newInstance(addToConversation: Boolean, groupConversation: Boolean, conversationId: String): PickUserFragment = {
    val fragment: PickUserFragment = new PickUserFragment
    val args: Bundle = new Bundle
    args.putBoolean(ARGUMENT_ADD_TO_CONVERSATION, addToConversation)
    args.putString(ARGUMENT_CONVERSATION_ID, conversationId)
    args.putBoolean(ARGUMENT_GROUP_CONVERSATION, groupConversation)
    fragment.setArguments(args)
    fragment
  }

  trait Container {
    def showIncomingPendingConnectRequest(conversation: IConversation): Unit

    def onSelectedUsers(users: java.util.List[User], requester: ConversationChangeRequester): Unit

    def getLoadingViewIndicator: LoadingIndicatorView

    def getCurrentPickerDestination: IPickUserController.Destination
  }

}

class PickUserFragment extends BaseFragment[PickUserFragment.Container]
  with FragmentHelper
  with View.OnClickListener
  with OnInboxLoadedListener
  with KeyboardVisibilityObserver
  with ConversationQuickMenuCallback
  with OnBackPressedListener
  with SearchResultOnItemTouchListener.Callback
  with PickUsersAdapter.Callback
  with RequestPermissionsObserver {

  private var searchResultAdapter: PickUsersAdapter = null
  // Saves user from which a pending connect request is loaded
  private var pendingFromUser: User = null
  private var isKeyboardVisible: Boolean = false
  private var searchBoxIsEmpty: Boolean = true
  private var showLoadingBarDelay: Long = 0L
  private var lastInputIsKeyboardDoneAction: Boolean = false
  private var dialog: AlertDialog = null
  private var searchUserController: SearchUserController = null

  private var searchResultRecyclerView: RecyclerView = null
  private var conversationToolbar: Toolbar = null
  private var startUiToolbar: Toolbar = null
  private var toolbarHeader: TextView = null
  private var divider: View = null
  private var errorMessageViewHeader: TypefaceTextView = null
  private var errorMessageViewSendInvite: LinearLayout = null
  private var errorMessageViewBody: TypefaceTextView = null
  private var errorMessageViewContainer: LinearLayout = null
  private var conversationQuickMenu: ConversationQuickMenu = null
  private var userSelectionConfirmationButton: FlatWireButton = null
  private var inviteButton: FlatWireButton = null
  private var searchBoxView: SearchEditText = null
  private var toolbarTitle: TypefaceTextView = null

  private var teamPermissions = Set[AccountData.Permission]()

  private implicit lazy val uiStorage = inject[UiStorage]
  private implicit lazy val logTag = ZLog.logTagFor[PickUserFragment]
  private implicit lazy val context = getContext
  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val self = zms.flatMap(z => UserSignal(z.selfUserId))
  private lazy val trackingController = inject[GlobalTrackingController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.getColor())
  private lazy val themeController = inject[ThemeController]

  private case class PickableUser(userId : UserId, userName: String) extends PickableElement {
    def id: String = userId.str
    def name: String = userName
  }

  private object PickableUser {
    def apply(user: User): PickableUser = new PickableUser(UserId(user.getId), user.getDisplayName)
    def apply(userData: UserData): PickableUser = new PickableUser(userData.id, userData.getDisplayName)
  }

  final val searchBoxViewCallback: SearchBoxView.Callback = new SearchBoxView.Callback() {
    override def onRemovedTokenSpan(element: PickableElement): Unit = {
      searchUserController.removeUser(UserId(element.id))
      if (isAddingToConversation) {
        setConversationQuickMenuVisible(false)
      }
    }

    override def onKeyboardDoneAction(): Unit = getControllerFactory.getPickUserController.notifyKeyboardDoneAction()

    override def onFocusChange(hasFocus: Boolean): Unit = setFocusByCurrentPickerDestination()

    override def onClearButton(): Unit = closeStartUI()

    override def afterTextChanged(s: String): Unit = {
      val filter = searchBoxView.getSearchFilter
      searchUserController.setFilter(filter)
      onSearchBoxHasNewSearchFilter(filter)
      if (filter.isEmpty && searchBoxView.getElements.isEmpty)
        onSearchBoxIsEmpty()
      if (filter.nonEmpty)
        trackingController.tagEvent(new EnteredSearchEvent(isAddingToConversation, searchBoxView.getSearchFilter))
    }

  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    if (nextAnim == 0 || getContainer == null || getControllerFactory.isTornDown) {
      return super.onCreateAnimation(transit, enter, nextAnim)
    }
    if (getControllerFactory.getPickUserController.isHideWithoutAnimations) {
      return new DefaultPageTransitionAnimation(0, ViewUtils.getOrientationIndependentDisplayHeight(getActivity), enter, 0, 0, 1f)
    }
    if (enter) {
      // Fade animation in participants dialog on tablet
      if (LayoutSpec.isTablet(getActivity) && isAddingToConversation) {
        return new FadeAnimation(getResources.getInteger(R.integer.open_new_conversation__top_conversation__animation_duration), 0f, 1f)
      }
      return new DefaultPageTransitionAnimation(0, getResources.getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance), enter, getResources.getInteger(R.integer.framework_animation_duration_long), getResources.getInteger(R.integer.framework_animation_duration_medium), 1f)
    }
    new DefaultPageTransitionAnimation(0, getResources.getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance), enter, getResources.getInteger(R.integer.framework_animation_duration_medium), 0, 1f)
  }

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View = {
    val rootView: View = inflater.inflate(R.layout.fragment_pick_user, viewContainer, false)
    divider = ViewUtils.getView(rootView, R.id.v__pickuser__divider)
    startUiToolbar = ViewUtils.getView(rootView, R.id.pickuser_toolbar)
    toolbarHeader = ViewUtils.getView(rootView, R.id.ttv__pickuser__add_header)
    conversationToolbar = ViewUtils.getView(rootView, R.id.t_pickuser_toolbar)
    conversationToolbar.setNavigationOnClickListener(new View.OnClickListener() {
      def onClick(v: View): Unit = {
        closeStartUI()
      }
    })

    searchUserController = new SearchUserController(SearchState("", hasSelectedUsers = false, addingToConversation = addingToConversation))
    searchUserController.setContacts(getStoreFactory.getZMessagingApiStore.getApi.getContacts)
    searchResultAdapter = new PickUsersAdapter(new SearchResultOnItemTouchListener(getActivity, this), this, searchUserController, themeController.isDarkTheme || !isAddingToConversation)
    searchResultRecyclerView = ViewUtils.getView(rootView, R.id.rv__pickuser__header_list_view)
    searchResultRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity))
    searchResultRecyclerView.setAdapter(searchResultAdapter)
    searchResultRecyclerView.addOnItemTouchListener(new SearchResultOnItemTouchListener(getActivity, this))
    if (isAddingToConversation) {
      searchResultRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit = {
          if (newState == RecyclerView.SCROLL_STATE_DRAGGING && getControllerFactory.getGlobalLayoutController.isKeyboardVisible) {
            KeyboardUtils.hideKeyboard(getActivity)
          }
        }
      })
    }
    searchBoxView = ViewUtils.getView(rootView, R.id.sbv__search_box)
    searchBoxView.setCallback(searchBoxViewCallback)
    conversationQuickMenu = ViewUtils.getView(rootView, R.id.cqm__pickuser__quick_menu)
    conversationQuickMenu.setCallback(this)
    conversationQuickMenu.setVisibility(View.GONE)
    userSelectionConfirmationButton = ViewUtils.getView(rootView, R.id.confirmation_button)
    userSelectionConfirmationButton.setGlyph(R.string.glyph__add_people)
    userSelectionConfirmationButton.setVisibility(View.GONE)
    inviteButton = ViewUtils.getView(rootView, R.id.invite_button)
    inviteButton.setText(R.string.pref_invite_title)
    inviteButton.setGlyph(R.string.glyph__invite)
    // Error message
    errorMessageViewContainer = ViewUtils.getView(rootView, R.id.fl_pickuser__error_message_container)
    errorMessageViewContainer.setVisibility(View.GONE)
    errorMessageViewHeader = ViewUtils.getView(rootView, R.id.ttv_pickuser__error_header)
    errorMessageViewBody = ViewUtils.getView(rootView, R.id.ttv_pickuser__error_body)
    errorMessageViewSendInvite = ViewUtils.getView(rootView, R.id.ll_pickuser__error_invite)
    showLoadingBarDelay = getResources.getInteger(R.integer.people_picker__loading_bar__show_delay)
    if (isAddingToConversation) {
      inviteButton.setVisibility(View.GONE)
      divider.setVisibility(View.VISIBLE)
      conversationToolbar.setVisibility(View.VISIBLE)
      startUiToolbar.setVisibility(View.GONE)
      toolbarHeader.setText(if (getArguments.getBoolean(PickUserFragment.ARGUMENT_GROUP_CONVERSATION)) getString(R.string.people_picker__toolbar_header__group)
      else getString(R.string.people_picker__toolbar_header__one_to_one))
      userSelectionConfirmationButton.setText(if (getArguments.getBoolean(PickUserFragment.ARGUMENT_GROUP_CONVERSATION)) R.string.people_picker__confirm_button_title__add_to_conversation
      else R.string.people_picker__confirm_button_title__create_conversation)
      ViewUtils.setHeight(searchBoxView, getResources.getDimensionPixelSize(R.dimen.searchbox__height__with_toolbar))
      searchBoxView.applyDarkTheme(ThemeUtils.isDarkTheme(getContext))
    } else {
      inviteButton.setVisibility(if (isPrivateAccount) View.VISIBLE else View.GONE)
      // Use constant style for left side start ui
      val textColor: Int = ContextCompat.getColor(getContext, R.color.text__primary_dark)
      errorMessageViewHeader.setTextColor(textColor)
      errorMessageViewBody.setTextColor(textColor)
      val errorMessageIcon: TextView = ViewUtils.getView(rootView, R.id.gtv_pickuser__error_icon)
      errorMessageIcon.setTextColor(textColor)
      val errorMessageSublabel: TextView = ViewUtils.getView(rootView, R.id.ttv_pickuser__error_sublabel)
      errorMessageSublabel.setTextColor(textColor)
      conversationToolbar.setVisibility(View.GONE)
      startUiToolbar.setVisibility(View.VISIBLE)
      divider.setVisibility(View.GONE)
      toolbarTitle = ViewUtils.getView(rootView, R.id.pickuser_title)
      searchBoxView.applyDarkTheme(true)
      startUiToolbar.inflateMenu(R.menu.toolbar_close_white)
      startUiToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
        override def onMenuItemClick(item: MenuItem): Boolean = {
          item.getItemId match {
            case R.id.close =>
              closeStartUI()
          }
          false
        }
      })
      if (isPrivateAccount)
        userAccountsController.currentUser.on(Threading.Ui) {
          case Some(userData) =>
            toolbarTitle.setText(userData.getDisplayName)
          case _ =>
        }
      else
        (for {
          z <- zms
          t <- z.teams.selfTeam
        } yield t.map(_.name).getOrElse("")).onUi {
          toolbarTitle.setText
        }
    }

    accentColor.on(Threading.Ui) { color =>
      conversationQuickMenu.setAccentColor(color)
      searchBoxView.setCursorColor(color)
    }

    searchUserController.onSelectedUserAdded.on(Threading.Ui) { userId =>
      onSelectedUserAdded(getSelectedUsersJava, getStoreFactory.getPickUserStore.getUser(userId.str))
    }

    searchUserController.onSelectedUserRemoved.on(Threading.Ui) { userId =>
      onSelectedUserRemoved(getSelectedUsersJava, getStoreFactory.getPickUserStore.getUser(userId.str))
      if (getSelectedUsers.isEmpty)
        onSearchBoxIsEmpty()
    }

    searchBoxView.setOnEditorActionListener(new OnEditorActionListener {
      override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean = {
        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
          onKeyboardDoneAction()
          return true
        }
        false
      }
    })

    for {
      z <- zms
      accountData <- z.account.accountData
    } {
      teamPermissions = accountData.selfPermissions
    }

    rootView
  }

  override def onStart(): Unit = {
    super.onStart()
    getControllerFactory.getGlobalLayoutController.addKeyboardVisibilityObserver(this)
    getControllerFactory.getRequestPermissionsController.addObserver(this)
    if (isAddingToConversation && !getArguments.getBoolean(PickUserFragment.ARGUMENT_GROUP_CONVERSATION)) {
      new Handler().post(new Runnable() {
        def run(): Unit = {
          searchUserController.selectedUsers.map(UserSignal(_)).foreach{ userSignal =>
            userSignal.head.map(data => searchBoxView.addElement(PickableUser(data)))(Threading.Ui)
          }
        }
      })
    }
    else {
      searchUserController.setFilter("")
    }
    if (!isAddingToConversation && isPrivateAccount){
      implicit val ec = Threading.Ui
      zms.head.flatMap(_.userPrefs.preference(UserPreferences.ShareContacts).apply()).map{ showShareContactsDialog }
    }
  }

  override def onResume(): Unit = {
    super.onResume()
    inviteButton.setOnClickListener(this)
    userSelectionConfirmationButton.setOnClickListener(this)
    errorMessageViewSendInvite.setOnClickListener(this)
    if (!isAddingToConversation) {
      new Handler().postDelayed(new Runnable() {
        def run(): Unit = {
          if (getStoreFactory == null || getStoreFactory.isTornDown) {
            return
          }
          val numberOfActiveConversations: Int = getStoreFactory.getConversationStore.getNumberOfActiveConversations
          if (searchBoxView == null || numberOfActiveConversations <= PickUserFragment.SHOW_KEYBOARD_THRESHOLD) {
            return
          }
          if (isTeamAccount) {
            searchBoxView.setFocus()
            KeyboardUtils.showKeyboard(getActivity)
          }
        }
      }, getResources.getInteger(R.integer.people_picker__keyboard__show_delay))
    }
  }

  override def onPause(): Unit = {
    inviteButton.setOnClickListener(null)
    userSelectionConfirmationButton.setOnClickListener(null)
    errorMessageViewSendInvite.setOnClickListener(null)
    super.onPause()
  }

  override def onStop(): Unit = {
    getContainer.getLoadingViewIndicator.hide()
    getControllerFactory.getGlobalLayoutController.removeKeyboardVisibilityObserver(this)
    getControllerFactory.getRequestPermissionsController.removeObserver(this)
    super.onStop()
  }

  override def onDestroyView(): Unit = {
    errorMessageViewHeader = null
    errorMessageViewSendInvite = null
    errorMessageViewBody = null
    errorMessageViewContainer = null
    searchResultRecyclerView = null
    conversationQuickMenu = null
    userSelectionConfirmationButton = null
    inviteButton = null
    searchBoxView = null
    conversationToolbar = null
    toolbarHeader = null
    super.onDestroyView()
  }

  override def onBackPressed: Boolean = {
    if (isKeyboardVisible) {
      KeyboardUtils.hideKeyboard(getActivity)
    }
    else if (getControllerFactory.getPickUserController.isShowingUserProfile) {
      getControllerFactory.getPickUserController.hideUserProfile()
      return true
    }
    isKeyboardVisible
  }

  override def onConnectRequestInboxConversationsLoaded(conversations: java.util.List[IConversation], inboxLoadRequester: InboxLoadRequester): Unit = {
    (0 until conversations.size()).map(conversations.get).foreach{ conversation =>
      if (conversation.getId == pendingFromUser.getConversation.getId) {
        getContainer.showIncomingPendingConnectRequest(conversation)
        return
      }
    }
  }

  override def onKeyboardVisibilityChanged(keyboardIsVisible: Boolean, keyboardHeight: Int, currentFocus: View): Unit = {
    isKeyboardVisible = keyboardIsVisible
    val color: Int =
      if (keyboardIsVisible || !searchBoxIsEmpty)
        ContextCompat.getColor(getContext, R.color.people_picker__loading__color)
      else
        accentColor.currentValue.getOrElse(0)

    getContainer.getLoadingViewIndicator.setColor(color)
    if (isAddingToConversation) {
      return
    }
    val inviteVisibility: Int =
      if (keyboardIsVisible || searchUserController.selectedUsers.nonEmpty || isTeamAccount)
        View.GONE
      else
        View.VISIBLE
    inviteButton.setVisibility(inviteVisibility)
  }

  private def createAndOpenConversation(users: Seq[UserId], requester: ConversationChangeRequester): Unit = {
    userAccountsController.createAndOpenConversation(users.toArray, requester, getActivity.asInstanceOf[BaseActivity])
    getControllerFactory.getPickUserController.hidePickUser(getCurrentPickerDestination, true)
  }

  override def onConversationButtonClicked(): Unit = {
    KeyboardUtils.hideKeyboard(getActivity)
    val users = if (isAddingToConversation) getSelectedAndExcluded else getSelectedUsers
    createAndOpenConversation(users.toSeq, ConversationChangeRequester.START_CONVERSATION)
  }

  override def onVideoCallButtonClicked(): Unit = {
    KeyboardUtils.hideKeyboard(getActivity)
    val users = if (isAddingToConversation) getSelectedAndExcluded else getSelectedUsers
    if (users.size > 1) {
      throw new IllegalStateException("A video call cannot be started with more than one user. The button should not be visible " + "if multiple users are selected.")
    }
    createAndOpenConversation(users.toSeq, ConversationChangeRequester.START_CONVERSATION_FOR_VIDEO_CALL)
  }

  override def onCallButtonClicked(): Unit = {
    KeyboardUtils.hideKeyboard(getActivity)
    val users = if (isAddingToConversation) getSelectedAndExcluded else getSelectedUsers
    createAndOpenConversation(users.toSeq, ConversationChangeRequester.START_CONVERSATION_FOR_CALL)
  }

  override def onCameraButtonClicked(): Unit = {
    KeyboardUtils.hideKeyboard(getActivity)
    val users = if (isAddingToConversation) getSelectedAndExcluded else getSelectedUsers
    createAndOpenConversation(users.toSeq, ConversationChangeRequester.START_CONVERSATION_FOR_CAMERA)
  }

  def onSearchBoxIsEmpty(): Unit = {
    searchBoxIsEmpty = true
    lastInputIsKeyboardDoneAction = false
    setConversationQuickMenuVisible(false)
  }

  def onSearchBoxHasNewSearchFilter(filter: String): Unit = {
    searchBoxIsEmpty = filter.isEmpty
    lastInputIsKeyboardDoneAction = false
  }

  def onKeyboardDoneAction(): Unit = {
    lastInputIsKeyboardDoneAction = true
    val users = getSelectedUsersJava
    var minUsers: Int = 1
    if (isAddingToConversation && !getArguments.getBoolean(PickUserFragment.ARGUMENT_GROUP_CONVERSATION)) {
      minUsers = 2
    }
    if (users.size >= minUsers) {
      KeyboardUtils.hideKeyboard(getActivity)
      getContainer.onSelectedUsers(users, ConversationChangeRequester.START_CONVERSATION)
    }
    if (searchResultRecyclerView != null && searchResultRecyclerView.getVisibility != View.VISIBLE && errorMessageViewContainer.getVisibility != View.VISIBLE) {
      showErrorMessage()
    }
  }

  def onSelectedUserAdded(selectedUsers: java.util.List[User], addedUser: User): Unit = {
    changeUserSelectedState(addedUser, selected = true)
    updateConversationButtonLabel()
    conversationQuickMenu.showVideoCallButton(shouldVideoCallButtonBeDisplayed)
    searchBoxView.addElement(PickableUser(addedUser))
  }

  private def shouldVideoCallButtonBeDisplayed: Boolean = {
    getSelectedUsers.size == 1 && DeviceDetector.isVideoCallingEnabled
  }

  def onSelectedUserRemoved(selectedUsers: java.util.List[User], removedUser: User): Unit = {
    changeUserSelectedState(removedUser, selected = false)
    updateConversationButtonLabel()
    conversationQuickMenu.showVideoCallButton(shouldVideoCallButtonBeDisplayed)
    searchBoxView.removeElement(PickableUser(removedUser))
  }

  private def changeUserSelectedState(user: User, selected: Boolean): Unit = {
    changeUserSelectedState(searchResultRecyclerView, user, selected)
    if (searchUserController.allDataSignal.currentValue.exists(_._1.isEmpty)) // TODO: maybe change to checking searchResultAdapter for top users
      return
    (0 until searchResultRecyclerView.getChildCount).map(searchResultRecyclerView.getChildAt).foreach{
      case view: RecyclerView =>
        changeUserSelectedState(view, user, selected)
      case _ =>
    }
  }

  private def changeUserSelectedState(rv: RecyclerView, user: User, selected: Boolean): Unit = {
    (0 until rv.getChildCount).map(rv.getChildAt).foreach{
      case userRowView: UserRowView =>
        if (userRowView.getUser != null && userRowView.getUser == user) {
          userRowView.setSelected(selected)
        }
      case _ =>
    }
  }

  override def onUserClicked(userId: UserId, position: Int, anchorView: View): Unit = {

    val user: User = getStoreFactory.getZMessagingApiStore.getApi.getUser(userId.str)
    if (user == null || user.isMe || getControllerFactory == null || getControllerFactory.isTornDown) {
      return
    }

    TrackingUtils.onUserSelectedInStartUI(trackingController, user, anchorView.isInstanceOf[ChatheadWithTextFooter], isAddingToConversation, position, searchResultAdapter)

    UserSignal(userId).head.map{ userData =>
      if (userData.connection == ConnectionStatus.Accepted || (isTeamAccount && userAccountsController.isTeamMember(userData.id))) {
        if (anchorView.isSelected) searchUserController.addUser(userId) else searchUserController.removeUser(userId)
        setConversationQuickMenuVisible(searchUserController.selectedUsers.nonEmpty)
      } else if (!anchorView.isInstanceOf[ContactRowView] || (userData.connection != ConnectionStatus.Unconnected)) {
        showUser(user, anchorView)
      }
    }(Threading.Ui)
  }

  override def onUserDoubleClicked(userId: UserId, position: Int, anchorView: View): Unit = {
    if (!anchorView.isInstanceOf[ChatheadWithTextFooter]) {
      return
    }
    val user: User = getStoreFactory.getZMessagingApiStore.getApi.getUser(userId.str)
    if (user == null || user.isMe || (user.getConnectionStatus ne User.ConnectionStatus.ACCEPTED) || searchUserController.selectedUsers.nonEmpty) {
      return
    }
    trackingController.tagEvent(new OpenedConversationEvent(ConversationType.ONE_TO_ONE_CONVERSATION.name, OpenedConversationEvent.Context.TOPUSER_DOUBLETAP, position + 1))
    getStoreFactory.getConversationStore.setCurrentConversation(user.getConversation, ConversationChangeRequester.START_CONVERSATION)
  }

  override def onConversationClicked(conversationData: ConversationData, position: Int): Unit = {
    val conversation: IConversation = getStoreFactory.getConversationStore.getConversation(conversationData.id.str)
    KeyboardUtils.hideKeyboard(getActivity)
    trackingController.tagEvent(new OpenedConversationEvent(ConversationType.GROUP_CONVERSATION.name, OpenedConversationEvent.Context.SEARCH, 0))
    getStoreFactory.getConversationStore.setCurrentConversation(conversation, ConversationChangeRequester.START_CONVERSATION)
  }

  override def getSelectedUsers: Set[UserId] = searchUserController.selectedUsers

  def getSelectedAndExcluded: Set[UserId] = searchUserController.selectedUsers ++ searchUserController.excludedUsers.currentValue.getOrElse(Set[UserId]())

  override def onContactListUserClicked(userId: UserId): Unit = {
    val user: User = getStoreFactory.getZMessagingApiStore.getApi.getUser(userId.str)
    if (user == null) {
      return
    }

    user.getConnectionStatus match {
      case ConnectionStatus.Unconnected =>
        self.head.map { self =>
          val myName: String = self.getDisplayName
          val message: String = getString(R.string.connect__message, user.getName, myName)
          user.connect(message)
          trackingController.tagEvent(new SentConnectRequestEvent(SentConnectRequestEvent.EventContext.INVITE_CONTACT_LIST))
        }(Threading.Ui)
      case _ =>
    }
  }

  override def onContactListContactClicked(contactDetails: ContactDetails): Unit = {
    getStoreFactory.getNetworkStore.doIfHasInternetOrNotifyUser(new DefaultNetworkAction() {
      override def execute(networkMode: NetworkMode): Unit = {
        val contactMethodsCount: Int = contactDetails.getContactMethods.size
        val contactMethods: Array[ContactMethod] = contactDetails.getContactMethods.toArray(new Array[ContactMethod](contactMethodsCount))
        if (contactMethodsCount == 1 && (contactMethods(0).getKind eq ContactMethod.Kind.SMS)) {
          // Launch SMS app directly if contact only has phone number
          val number = contactMethods(0).getStringRepresentation
          sendSMSInvite(number)
          trackingController.tagEvent(new OpenedGenericInviteMenuEvent(OpenedGenericInviteMenuEvent.EventContext.ADDRESSBOOK))
          return
        }
        val itemNames: Array[CharSequence] = new Array[CharSequence](contactMethodsCount)

        (0 until contactMethodsCount).foreach{i =>
          val contactMethod: ContactMethod = contactMethods(i)
          itemNames(i) = contactMethod.getStringRepresentation
        }

        val builder: AlertDialog.Builder = new AlertDialog.Builder(getActivity)
        builder.setTitle(getResources.getString(R.string.people_picker__contact_list__invite_dialog__title)).setPositiveButton(getResources.getText(R.string.confirmation_menu__confirm_done), new DialogInterface.OnClickListener() {
          def onClick(dialogInterface: DialogInterface, i: Int): Unit = {
            val lv: ListView = dialog.getListView
            val selected: Int = lv.getCheckedItemPosition
            var selectedContactMethod: ContactMethod = null
            if (selected >= 0) {
              selectedContactMethod = contactMethods(selected)
            }
            if (selectedContactMethod == null) {
              return
            }
            if (selectedContactMethod.getKind eq ContactMethod.Kind.SMS) {
              val number = String.valueOf(itemNames(selected))
              sendSMSInvite(number)
              trackingController.tagEvent(new OpenedGenericInviteMenuEvent(OpenedGenericInviteMenuEvent.EventContext.ADDRESSBOOK))
            }
            else {
              selectedContactMethod.invite(" ", null)
              Toast.makeText(getActivity, getResources.getString(R.string.people_picker__invite__sent_feedback), Toast.LENGTH_LONG).show()
              val fromSearch: Boolean = searchUserController.searchState.currentValue.exists(_.filter.nonEmpty)
              TrackingUtils.tagSentInviteToContactEvent(trackingController, selectedContactMethod.getKind, contactDetails.hasBeenInvited, fromSearch)
            }
          }
        }).setNegativeButton(getResources.getText(R.string.confirmation_menu__cancel), new DialogInterface.OnClickListener() {
          def onClick(dialogInterface: DialogInterface, i: Int): Unit = {
            dialogInterface.cancel()
          }
        }).setSingleChoiceItems(itemNames, PickUserFragment.DEFAULT_SELECTED_INVITE_METHOD, null)
        dialog = builder.create
        dialog.show()
        trackingController.onApplicationScreen(ApplicationScreen.SEND_PERSONAL_INVITE_MENU)
      }
    })
  }

  private def sendSMSInvite(number: String): Unit = {
    val me: User = getStoreFactory.getProfileStore.getSelfUser
    if (me != null) {
      var smsBody: String = null
      val username: String = me.getUsername
      if (TextUtils.isEmpty(username)) {
        smsBody = getString(R.string.people_picker__invite__share_text__body, me.getName)
      }
      else {
        smsBody = getString(R.string.people_picker__invite__share_text__body, StringUtils.formatHandle(username))
      }
      val intent: Intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("sms", number, ""))
      intent.putExtra("sms_body", smsBody)
      startActivity(intent)
    }
  }

  override def getDestination: Int = if(isAddingToConversation) IPickUserController.CONVERSATION else IPickUserController.STARTUI

  private def showErrorMessage(): Unit = {
    errorMessageViewContainer.setVisibility(View.VISIBLE)
    // Set isClickable as ListView continues to receive click events with GONE visibility
    searchResultRecyclerView.setClickable(false)
    searchResultRecyclerView.setVisibility(View.GONE)
  }

  private def hideErrorMessage(): Unit = {
    errorMessageViewContainer.setVisibility(View.GONE)
    searchResultRecyclerView.setClickable(true)
    searchResultRecyclerView.setVisibility(View.VISIBLE)
    errorMessageViewHeader.setText("")
  }

  private def getCurrentPickerDestination: IPickUserController.Destination = {
    getContainer.getCurrentPickerDestination
  }

  private def sendGenericInvite(fromSearch: Boolean): Unit = {
    if (getControllerFactory == null || getControllerFactory.isTornDown) {
      return
    }
    self.head.map { self =>
      val name: String = self.getDisplayName
      val username: String = self.handle.map(_.string).getOrElse("")
      val sharingIntent = IntentUtils.getInviteIntent(getString(R.string.people_picker__invite__share_text__header, name), getString(R.string.people_picker__invite__share_text__body, StringUtils.formatHandle(username)))
      startActivity(Intent.createChooser(sharingIntent, getString(R.string.people_picker__invite__share_details_dialog)))
      val eventContext =
        if (fromSearch)
          OpenedGenericInviteMenuEvent.EventContext.NO_RESULTS
        else
          OpenedGenericInviteMenuEvent.EventContext.BANNER
      trackingController.tagEvent(new OpenedGenericInviteMenuEvent(eventContext))
      trackingController.onApplicationScreen(ApplicationScreen.SEND_GENERIC_INVITE_MENU)
    }(Threading.Ui)

  }

  private def showUser(user: User, anchorView: View): Unit = {
    user.getConnectionStatus match {
      case ConnectionStatus.Accepted =>
        if (isAddingToConversation) {
          val users: java.util.ArrayList[User] = new java.util.ArrayList[User]
          users.add(user)
          getContainer.onSelectedUsers(users, ConversationChangeRequester.START_CONVERSATION)
        } else {
          val conversation: IConversation = user.getConversation
          if (conversation != null) {
            KeyboardUtils.hideKeyboard(getActivity)
            getStoreFactory.getConversationStore.setCurrentConversation(conversation, ConversationChangeRequester.START_CONVERSATION)
          }
        }
      case ConnectionStatus.PendingFromUser |
           ConnectionStatus.Blocked |
           ConnectionStatus.Ignored |
           ConnectionStatus.Cancelled |
           ConnectionStatus.Unconnected =>
        KeyboardUtils.hideKeyboard(getActivity)
        getControllerFactory.getConversationScreenController.setPopoverLaunchedMode(DialogLaunchMode.SEARCH)
        getControllerFactory.getPickUserController.showUserProfile(user, anchorView)
      case ConnectionStatus.PendingFromOther =>
        KeyboardUtils.hideKeyboard(getActivity)
        pendingFromUser = user
        getStoreFactory.getConversationStore.loadConnectRequestInboxConversations(this, InboxLoadRequester.INBOX_SHOW_SPECIFIC)
      case _ =>
    }
  }

  private def setConversationQuickMenuVisible(show: Boolean): Unit = {
    if (getView == null || getControllerFactory == null || getControllerFactory.isTornDown) {
      return
    }
    if (isAddingToConversation) {
      userSelectionConfirmationButton.setVisibility(if (getSelectedUsers.nonEmpty) View.VISIBLE else View.GONE)
    } else {
      val visible: Boolean = show || searchUserController.selectedUsers.nonEmpty
      conversationQuickMenu.setVisibility(if (visible) View.VISIBLE else View.GONE)
      inviteButton.setVisibility(if (visible || isKeyboardVisible || isTeamAccount) View.GONE else View.VISIBLE)
    }
  }

  private def updateConversationButtonLabel(): Unit = {
    val label: String = if (searchUserController.selectedUsers.size > 1) getString(R.string.conversation_quick_menu__conversation_button__group_label)
    else getString(R.string.conversation_quick_menu__conversation_button__single_label)
    conversationQuickMenu.setConversationButtonText(label)
    val hasPermissions = isPrivateAccount || searchUserController.selectedUsers.size == 1 || teamPermissions.contains(AccountData.Permission.CreateConversation)
    conversationQuickMenu.setVisibility(if (hasPermissions && !isAddingToConversation) View.VISIBLE else View.GONE)
  }

  override def onClick(view: View): Unit = {
    view.getId match {
      case R.id.confirmation_button =>
        KeyboardUtils.hideKeyboard(getActivity)
        val users = if (getArguments.getBoolean(PickUserFragment.ARGUMENT_GROUP_CONVERSATION)) getSelectedUsersJava else getSelectedAndExcludedUsersJava
        getContainer.onSelectedUsers(users, ConversationChangeRequester.START_CONVERSATION)
      case R.id.invite_button =>
        sendGenericInvite(false)
      case R.id.ll_pickuser__error_invite =>
        sendGenericInvite(true)
    }
  }

  private def setFocusByCurrentPickerDestination(): Unit = {
    // Don't trigger setting focus in closed split mode on tablet portrait, search is not visible then
    if (LayoutSpec.isTablet(getActivity) && ViewUtils.isInPortrait(getActivity) && getControllerFactory.getNavigationController.getPagerPosition == NavigationController.SECOND_PAGE) {
      return
    }
    if ((getCurrentPickerDestination eq IPickUserController.Destination.CONVERSATION_LIST) && (LayoutSpec.isTablet(getActivity) || getControllerFactory.getNavigationController.getPagerPosition == NavigationController.FIRST_PAGE)) {
      getControllerFactory.getFocusController.setFocus(IFocusController.CONVERSATION_LIST_SEARCHBOX)
    }
    else if (getCurrentPickerDestination eq IPickUserController.Destination.PARTICIPANTS) {
      getControllerFactory.getFocusController.setFocus(IFocusController.PARTICIPANTS_SEARCHBOX)
    }
  }

  private def isAddingToConversation: Boolean = {
    getArguments.getBoolean(PickUserFragment.ARGUMENT_ADD_TO_CONVERSATION)
  }

  private def addingToConversation: Option[ConvId] = {
    Option(getArguments.getString(PickUserFragment.ARGUMENT_CONVERSATION_ID)).map(ConvId(_))
  }

  private def isPrivateAccount: Boolean = !isTeamAccount

  private def isTeamAccount: Boolean = userAccountsController.isTeamAccount
  private def closeStartUI(): Unit = {
    KeyboardUtils.hideKeyboard(getActivity)
    searchUserController.setFilter("")
    getControllerFactory.getPickUserController.hidePickUser(getCurrentPickerDestination, true)
  }

  override def onRequestPermissionsResult(requestCode: Int, grantResults: Array[Int]): Unit = {
    if (requestCode == PermissionUtils.REQUEST_READ_CONTACTS) {
      updateShareContacts(false)
      if (grantResults.length > 0) {
        if (grantResults(0) == PackageManager.PERMISSION_GRANTED) {
          //Changing the value of the shareContacts seems to be the
          //only way to trigger a refresh on the sync engine...
          updateShareContacts(true)
        } else if (grantResults(0) == PackageManager.PERMISSION_DENIED) {
          val showRationale = shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)
          if (!showRationale && getControllerFactory != null && !getControllerFactory.isTornDown) {
            getControllerFactory.getUserPreferencesController.setPerformedAction(
              IUserPreferencesController.DO_NOT_SHOW_SHARE_CONTACTS_DIALOG)
          }
        }
      }
    }
  }

  private def getSelectedUsersJava: java.util.List[User] = {
    searchUserController.selectedUsers.map(uid => getStoreFactory.getPickUserStore.getUser(uid.str)).toList.asJava
  }

  private def getSelectedAndExcludedUsersJava: java.util.List[User] = {
    getSelectedAndExcluded.map(uid => getStoreFactory.getPickUserStore.getUser(uid.str)).toList.asJava
  }

  // XXX Only show contact sharing dialogs for PERSONAL START UI
  private def showShareContactsDialog(hasShareContactsEnabled: Boolean): Unit = {
    val prefController = getControllerFactory.getUserPreferencesController
    // Doesn't have _our_ contact sharing setting enabled, maybe show dialog
    if (!hasShareContactsEnabled && !prefController.hasPerformedAction(IUserPreferencesController.DO_NOT_SHOW_SHARE_CONTACTS_DIALOG)) {
      // show initial dialog
      val checkBoxView= View.inflate(getContext, R.layout.dialog_checkbox, null)
      val checkBox = checkBoxView.findViewById(R.id.checkbox).asInstanceOf[CheckBox]
      val checkedItems = new java.util.HashSet[Integer]
      checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit =  {
          if (isChecked)
            checkedItems.add(1)
          else
            checkedItems.remove(1)
        }
      })
      checkBox.setText(R.string.people_picker__share_contacts__nevvah)
      val dialog = new AlertDialog.Builder(getContext).setTitle(R.string.people_picker__share_contacts__title).setMessage(R.string.people_picker__share_contacts__message).setView(checkBoxView).setPositiveButton(R.string.people_picker__share_contacts__yay, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          requestShareContactsPermissions()
        }
      }).setNegativeButton(R.string.people_picker__share_contacts__nah, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          if (getControllerFactory != null && !getControllerFactory.isTornDown && checkedItems.size > 0) getControllerFactory.getUserPreferencesController.setPerformedAction(IUserPreferencesController.DO_NOT_SHOW_SHARE_CONTACTS_DIALOG)
        }
      }).create
      dialog.show()
    }
  }

  private def requestShareContactsPermissions(): Unit = {
    if (getControllerFactory == null || getControllerFactory.isTornDown || userAccountsController.isTeamAccount) {
      return
    }

    if (PermissionUtils.hasSelfPermissions(getContext, Manifest.permission.READ_CONTACTS)) {
      updateShareContacts(true)
    }
    else {
      ActivityCompat.requestPermissions(getActivity, Array[String](Manifest.permission.READ_CONTACTS), PermissionUtils.REQUEST_READ_CONTACTS)
    }
  }

  private def updateShareContacts(share: Boolean): Unit ={
    zms.head.flatMap(_.userPrefs.preference(UserPreferences.ShareContacts).update(share)) (Threading.Background)
  }
}
