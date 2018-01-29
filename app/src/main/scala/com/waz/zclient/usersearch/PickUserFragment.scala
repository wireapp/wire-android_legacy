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
package com.waz.zclient.usersearch

import android.Manifest.permission.READ_CONTACTS
import android.content.{DialogInterface, Intent}
import android.net.Uri
import android.os.{Bundle, Handler}
import android.support.design.widget.TabLayout
import android.support.design.widget.TabLayout.OnTabSelectedListener
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.{LinearLayoutManager, RecyclerView, Toolbar}
import android.text.TextUtils
import android.view._
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import android.widget._
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api._
import com.waz.content.UserPreferences
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.{SearchState, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.controllers._
import com.waz.zclient.common.views.{ChatheadWithTextFooter, FlatWireButton, PickableElement}
import com.waz.zclient.controllers.currentfocus.IFocusController
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver
import com.waz.zclient.controllers.navigation.{NavigationController, Page}
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.core.stores.network.DefaultNetworkAction
import com.waz.zclient.integrations.{IntegrationDetailsController, IntegrationDetailsFragment}
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.ui.animation.fragment.FadeAnimation
import com.waz.zclient.ui.startui.{ConversationQuickMenu, ConversationQuickMenuCallback}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.usersearch.adapters.PickUsersAdapter
import com.waz.zclient.usersearch.views.{ContactRowView, SearchBoxView, SearchEditText, UserRowView}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.device.DeviceDetector
import com.waz.zclient.utils.{IntentUtils, LayoutSpec, RichView, StringUtils, UiStorage, UserSignal, ViewUtils}
import com.waz.zclient.views._
import com.waz.zclient._

import scala.collection.JavaConverters._
import scala.concurrent.Future

object PickUserFragment {
  val TAG: String = classOf[PickUserFragment].getName
  val ARGUMENT_ADD_TO_CONVERSATION: String = "ARGUMENT_ADD_TO_CONVERSATION"
  val ARGUMENT_CONVERSATION_ID: String = "ARGUMENT_CONVERSATION_ID"
  val NUM_SEARCH_RESULTS_LIST: Int = 30
  val NUM_SEARCH_RESULTS_TOP_USERS: Int = 24
  val NUM_SEARCH_RESULTS_ADD_TO_CONV: Int = 1000
  private val DEFAULT_SELECTED_INVITE_METHOD: Int = 0
  private val SHOW_KEYBOARD_THRESHOLD: Int = 10

  def newInstance(addToConversation: Boolean, conversationId: String): PickUserFragment = {
    val fragment: PickUserFragment = new PickUserFragment
    val args: Bundle = new Bundle
    args.putBoolean(ARGUMENT_ADD_TO_CONVERSATION, addToConversation)
    args.putString(ARGUMENT_CONVERSATION_ID, conversationId)
    fragment.setArguments(args)
    fragment
  }

  trait Container {
    def showIncomingPendingConnectRequest(conv: ConvId): Unit

    def onSelectedUsers(users: java.util.List[UserId], requester: ConversationChangeRequester): Unit

    def getLoadingViewIndicator: LoadingIndicatorView

    def getCurrentPickerDestination: IPickUserController.Destination
  }

}

class PickUserFragment extends BaseFragment[PickUserFragment.Container]
  with FragmentHelper
  with View.OnClickListener
  with KeyboardVisibilityObserver
  with ConversationQuickMenuCallback
  with OnBackPressedListener
  with SearchResultOnItemTouchListener.Callback
  with PickUsersAdapter.Callback {

  private lazy val searchResultAdapter: PickUsersAdapter = new PickUsersAdapter(
    new SearchResultOnItemTouchListener(getActivity, this),
    this,
    searchUserController,
    integrationsController,
    themeController.isDarkTheme || !isAddingToConversation
  )
  // Saves user from which a pending connect request is loaded
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
  private implicit lazy val context   = getContext

  private lazy val zms                    = inject[Signal[ZMessaging]]
  private lazy val self                   = zms.flatMap(z => UserSignal(z.selfUserId))
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val accentColor            = inject[AccentColorController].accentColor.map(_.getColor())
  private lazy val themeController        = inject[ThemeController]
  private lazy val conversationController = inject[ConversationController]
  private lazy val browser                = inject[BrowserController]
  private lazy val integrationsController = inject[IntegrationsController]
  private lazy val pickUserController     = inject[IPickUserController]
  private lazy val convScreenController   = inject[IConversationScreenController]

  private lazy val emptyListButton = returning(view[RelativeLayout](R.id.empty_list_button)){ v =>
    (for {
      zms <- zms
      permissions <- userAccountsController.permissions.orElse(Signal.const(Set.empty[AccountData.Permission]))
      members <- zms.teams.searchTeamMembers().orElse(Signal.const(Set.empty[UserData]))
      searching <- Option(searchUserController).fold(Signal.const(false))(_.searchState.map(!_.empty))
    } yield
      if (zms.teamId.nonEmpty && permissions(AccountData.Permission.AddTeamMember) && !members.exists(_.id != zms.selfUserId) && !isAddingToConversation && !searching)
        View.VISIBLE
      else
        View.GONE)
      .onUi(v.setVisibility(_))

    v.foreach(_.onClick(browser.openUrl(AndroidURIUtil.parse(getString(R.string.pick_user_manage_team_url)))))
  }

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

    override def onFocusChange(hasFocus: Boolean): Unit = setFocusByCurrentPickerDestination()

    override def onClearButton(): Unit = closeStartUI()

    override def afterTextChanged(s: String): Unit = {
      val filter = searchBoxView.getSearchFilter
      searchUserController.setFilter(filter)
      integrationsController.searchQuery ! filter
      onSearchBoxHasNewSearchFilter(filter)
      if (filter.isEmpty && searchBoxView.getElements.isEmpty)
        onSearchBoxIsEmpty()
    }

  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    if (nextAnim == 0 || getContainer == null || getControllerFactory.isTornDown)
      super.onCreateAnimation(transit, enter, nextAnim)
    else if (pickUserController.isHideWithoutAnimations)
      new DefaultPageTransitionAnimation(0, getOrientationIndependentDisplayHeight(getActivity), enter, 0, 0, 1f)
    else if (enter)  // Fade animation in participants dialog on tablet
      if (LayoutSpec.isTablet(getActivity) && isAddingToConversation)
        new FadeAnimation(getInt(R.integer.open_new_conversation__top_conversation__animation_duration), 0f, 1f)
      else
        new DefaultPageTransitionAnimation(0,
          getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
          enter,
          getInt(R.integer.framework_animation_duration_long),
          getInt(R.integer.framework_animation_duration_medium),
          1f)
    else
      new DefaultPageTransitionAnimation(
        0,
        getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
        enter,
        getInt(R.integer.framework_animation_duration_medium),
        0,
        1f)

  }

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View = {
    val rootView: View = inflater.inflate(R.layout.fragment_pick_user, viewContainer, false)
    startUiToolbar = ViewUtils.getView(rootView, R.id.pickuser_toolbar)
    toolbarHeader = ViewUtils.getView(rootView, R.id.ttv__pickuser__add_header)
    conversationToolbar = ViewUtils.getView(rootView, R.id.t_pickuser_toolbar)
    conversationToolbar.setNavigationOnClickListener(new View.OnClickListener() {
      def onClick(v: View): Unit = closeStartUI()
    })

    searchUserController = new SearchUserController(SearchState("", hasSelectedUsers = false, addingToConversation = conversationId))
    searchUserController.setContacts(getStoreFactory.zMessagingApiStore.getApi.getContacts)

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
      conversationToolbar.setVisibility(View.VISIBLE)
      startUiToolbar.setVisibility(View.GONE)

      isGroupConversation.foreach { isGroup =>
        toolbarHeader.setText(
          if (isGroup) getString(R.string.people_picker__toolbar_header__group)
          else getString(R.string.people_picker__toolbar_header__one_to_one)
        )
        userSelectionConfirmationButton.setText(
          if (isGroup) R.string.people_picker__confirm_button_title__add_to_conversation
          else R.string.people_picker__confirm_button_title__create_conversation
        )
      }(Threading.Ui)

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
      onSelectedUserAdded(getSelectedUsersJava, getStoreFactory.pickUserStore.getUser(userId.str))
    }

    searchUserController.onSelectedUserRemoved.on(Threading.Ui) { userId =>
      onSelectedUserRemoved(getSelectedUsersJava, getStoreFactory.pickUserStore.getUser(userId.str))
      if (getSelectedUsers.isEmpty)
        onSearchBoxIsEmpty()
    }

    searchBoxView.setOnEditorActionListener(new OnEditorActionListener {
      override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean = {
        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
          onKeyboardDoneAction()
          true
        } else false
      }
    })

    for {
      z <- zms
      accountData <- z.account.accountData
    } {
      teamPermissions = accountData.selfPermissions
    }

    val tabs = findById[TabLayout](rootView, R.id.pick_user_tabs)
    import Threading.Implicits.Ui
    searchResultAdapter.peopleOrServices.head.map {
      case false => 0
      case true  => 1
    }.foreach(tabs.getTabAt(_).select())

    tabs.addOnTabSelectedListener(new OnTabSelectedListener {
      override def onTabSelected(tab: TabLayout.Tab): Unit = {
        tab.getPosition match {
          case 0 => searchResultAdapter.peopleOrServices ! false
          case 1 => searchResultAdapter.peopleOrServices ! true
        }
        searchBoxView.removeAllElements()
      }
      override def onTabUnselected(tab: TabLayout.Tab): Unit = {
      }
      override def onTabReselected(tab: TabLayout.Tab): Unit = {}
    })

    val internalVersion = BuildConfig.APPLICATION_ID match {
      case "com.wire.internal" | "com.waz.zclient.dev" | "com.wire.x" | "com.wire.qa" => true
      case _ => false
    }

    val shouldShowTabs = for {
      zms <- zms.head
      conv <- conversationId.fold(Future.successful(Option.empty[ConversationData]))(zms.convsStorage.get)
      members <- conversationId.fold(Future.successful(Option.empty[Int]))(cId => zms.membersStorage.getActiveUsers(cId).map(m => Some(m.size)))
    } yield zms.teamId.nonEmpty && internalVersion && conv.forall(_.convType == ConversationType.Group) && members.forall(_ > 2)

    shouldShowTabs.map {
      case true => tabs.setVisibility(View.VISIBLE)
      case _ => tabs.setVisibility(View.GONE)
    } (Threading.Ui)

    if (isAddingToConversation && !themeController.isDarkTheme) {
      tabs.setSelectedTabIndicatorColor(getColor(R.color.light_graphite))
      (0 until tabs.getTabCount).map(tabs.getTabAt)
        .foreach(_.getCustomView.findViewById[TextView](android.R.id.text1)
          .setTextColor(getColorStateList(R.color.tab_text_color_dark)))
      findById[LinearLayout](rootView, R.id.top_background_layout).setBackgroundColor(getColor(R.color.white))
    }

    searchUserController.setFilter("")
    integrationsController.searchQuery ! ""

    rootView
  }


  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    emptyListButton
  }

  override def onStart(): Unit = {
    super.onStart()
    getControllerFactory.getGlobalLayoutController.addKeyboardVisibilityObserver(this)

    isGroupConversation.foreach { isGroup =>
      if (isAddingToConversation && !isGroup) {
        new Handler().post(new Runnable() { // TODO: is this necessary now?
          def run(): Unit = {
            searchUserController.selectedUsers.map(UserSignal(_)).foreach { userSignal =>
              userSignal.head.map(data => searchBoxView.addElement(PickableUser(data)))(Threading.Ui)
            }
          }
        })
      }
    }(Threading.Ui)

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
        def run(): Unit =
          Option(getStoreFactory).filterNot(_.isTornDown).foreach { store =>
            val numberOfActiveConversations: Int = store.conversationStore.numberOfActiveConversations
            Option(searchBoxView).filter(_ => numberOfActiveConversations > PickUserFragment.SHOW_KEYBOARD_THRESHOLD).foreach { v =>
              if (isTeamAccount) {
                v.setFocus()
                KeyboardUtils.showKeyboard(getActivity)
              }
            }
        }
      }, getInt(R.integer.people_picker__keyboard__show_delay))
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

  override def onBackPressed(): Boolean =
    if (isKeyboardVisible) {
      KeyboardUtils.hideKeyboard(getActivity)
      true
    } else if (pickUserController.isShowingUserProfile) {
      pickUserController.hideUserProfile()
      true
    } else false

  override def onKeyboardVisibilityChanged(keyboardIsVisible: Boolean, keyboardHeight: Int, currentFocus: View): Unit = {
    isKeyboardVisible = keyboardIsVisible
    val color =
      if (keyboardIsVisible || !searchBoxIsEmpty)
        ContextCompat.getColor(getContext, R.color.people_picker__loading__color)
      else
        accentColor.currentValue.getOrElse(0)

    if (!isAddingToConversation) {
      getContainer.getLoadingViewIndicator.setColor(color)
      val inviteVisibility =
        if (keyboardIsVisible || searchUserController.selectedUsers.nonEmpty || isTeamAccount) View.GONE
        else View.VISIBLE
      inviteButton.setVisibility(inviteVisibility)
    }
  }

  private def createAndOpenConversation(users: Seq[UserId], requester: ConversationChangeRequester): Unit =
    userAccountsController.createAndOpenConversation(users.toArray, requester, getActivity.asInstanceOf[BaseActivity])

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

    isGroupConversation.foreach { isGroup =>
      val minUsers = if (isAddingToConversation && !isGroup) 2 else 1
      val users = searchUserController.selectedUsers
      if (users.size >= minUsers) {
        KeyboardUtils.hideKeyboard(getActivity)
        getContainer.onSelectedUsers(users.toSeq.asJava, ConversationChangeRequester.START_CONVERSATION)
      }
    }(Threading.Ui)

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
    if (!searchUserController.allDataSignal.currentValue.exists(_._1.isEmpty)) {
      (0 until searchResultRecyclerView.getChildCount).map(searchResultRecyclerView.getChildAt).foreach {
        case view: RecyclerView =>
          changeUserSelectedState(view, user, selected)
        case _ =>
      }
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

  private def getUser(id: UserId) = getStoreFactory.zMessagingApiStore.getApi.getUser(id.str)

  override def onUserClicked(userId: UserId, position: Int, anchorView: View): Unit =
    Option(getUser(userId)).filterNot(_.isMe).foreach { user =>
      UserSignal(userId).head.map { userData =>
        if (userData.connection == ConnectionStatus.Accepted || (isTeamAccount && userAccountsController.isTeamMember(userData.id))) {
          if (anchorView.isSelected) searchUserController.addUser(userId) else searchUserController.removeUser(userId)
          setConversationQuickMenuVisible(searchUserController.selectedUsers.nonEmpty)
        } else if (!anchorView.isInstanceOf[ContactRowView] || (userData.connection != ConnectionStatus.Unconnected)) {
          showUser(user, anchorView)
        }
      }(Threading.Ui)
    }

  override def onUserDoubleClicked(userId: UserId, position: Int, anchorView: View): Future[Unit] =
    if (anchorView.isInstanceOf[ChatheadWithTextFooter] && searchUserController.selectedUsers.isEmpty) {

    Option(getUser(userId)) match {
      case Some(user) if !user.isMe && user.getConnectionStatus == User.ConnectionStatus.ACCEPTED =>
       conversationController.getOrCreateConv(userId).flatMap { conv =>
         verbose(s"onConversationClicked(${conv.id})")
          conversationController.selectConv(Some(conv.id), ConversationChangeRequester.START_CONVERSATION)
        }(Threading.Ui)
      case _ => Future.successful({})
    }
  } else Future.successful({})

  override def onConversationClicked(conversationData: ConversationData, position: Int): Unit = {
    KeyboardUtils.hideKeyboard(getActivity)
    verbose(s"onConversationClicked(${conversationData.id})")
    conversationController.selectConv(Some(conversationData.id), ConversationChangeRequester.START_CONVERSATION)
  }

  override def getSelectedUsers: Set[UserId] = searchUserController.selectedUsers

  def getSelectedAndExcluded: Set[UserId] = searchUserController.selectedUsers ++ searchUserController.excludedUsers.currentValue.getOrElse(Set[UserId]())

  override def onContactListUserClicked(userId: UserId): Unit = {
    Option(getUser(userId)).foreach { user =>
      user.getConnectionStatus match {
        case ConnectionStatus.Unconnected =>
          self.head.map { self =>
            val myName: String = self.getDisplayName
            val message: String = getString(R.string.connect__message, user.getName, myName)
            user.connect(message)
          }(Threading.Ui)
        case _ =>
      }
    }
  }

  override def onContactListContactClicked(contactDetails: ContactDetails): Unit = {
    getStoreFactory.networkStore.doIfHasInternetOrNotifyUser(new DefaultNetworkAction() {
      override def execute(networkMode: NetworkMode): Unit = {
        val contactMethodsCount: Int = contactDetails.getContactMethods.size
        val contactMethods: Array[ContactMethod] = contactDetails.getContactMethods.toArray(new Array[ContactMethod](contactMethodsCount))
        if (contactMethodsCount == 1 && (contactMethods(0).getKind eq ContactMethod.Kind.SMS)) {
          // Launch SMS app directly if contact only has phone number
          val number = contactMethods(0).getStringRepresentation
          sendSMSInvite(number)
        } else {
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
              if (selectedContactMethod != null) {
                if (selectedContactMethod.getKind eq ContactMethod.Kind.SMS) {
                  val number = String.valueOf(itemNames(selected))
                  sendSMSInvite(number)
                }
                else {
                  selectedContactMethod.invite(" ", null)
                  Toast.makeText(getActivity, getResources.getString(R.string.people_picker__invite__sent_feedback), Toast.LENGTH_LONG).show()
                }
              }
            }
          }).setNegativeButton(getResources.getText(R.string.confirmation_menu__cancel), new DialogInterface.OnClickListener() {
            def onClick(dialogInterface: DialogInterface, i: Int): Unit = {
              dialogInterface.cancel()
            }
          }).setSingleChoiceItems(itemNames, PickUserFragment.DEFAULT_SELECTED_INVITE_METHOD, null)
          dialog = builder.create
          dialog.show()
        }
      }
    })
  }

  private def sendSMSInvite(number: String): Unit = {
    val me: User = getStoreFactory.profileStore.getSelfUser
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

  private def sendGenericInvite(fromSearch: Boolean): Unit =
    Option(getControllerFactory).filterNot(_.isTornDown).foreach { _ =>
      self.head.map { self =>
        val username = self.handle.map(_.string).getOrElse("")
        val sharingIntent = IntentUtils.getInviteIntent(getString(R.string.people_picker__invite__share_text__header, self.getDisplayName), getString(R.string.people_picker__invite__share_text__body, StringUtils.formatHandle(username)))
        startActivity(Intent.createChooser(sharingIntent, getString(R.string.people_picker__invite__share_details_dialog)))
      }(Threading.Ui)
    }

  private def showUser(user: User, anchorView: View): Unit = {
    user.getConnectionStatus match {
      case ConnectionStatus.Accepted =>
        if (isAddingToConversation) {
          getContainer.onSelectedUsers(Seq(new UserId(user.getId)).asJava, ConversationChangeRequester.START_CONVERSATION)
        } else {
          val convId = Option(user.getConversation).map(c => ConvId(c.getId))
          if (convId != null) {
            KeyboardUtils.hideKeyboard(getActivity)
            verbose(s"showUser $convId")
            conversationController.selectConv(convId, ConversationChangeRequester.START_CONVERSATION)
          }
        }
      case ConnectionStatus.PendingFromUser |
           ConnectionStatus.Blocked |
           ConnectionStatus.Ignored |
           ConnectionStatus.Cancelled |
           ConnectionStatus.Unconnected =>
        KeyboardUtils.hideKeyboard(getActivity)
        convScreenController.setPopoverLaunchedMode(DialogLaunchMode.SEARCH)
        pickUserController.showUserProfile(new UserId(user.getId), anchorView)
      case ConnectionStatus.PendingFromOther =>
        KeyboardUtils.hideKeyboard(getActivity)
        getContainer.showIncomingPendingConnectRequest(new ConvId(user.getConversation.getId))
      case _ =>
    }
  }

  private def setConversationQuickMenuVisible(show: Boolean): Unit = {
    if (getView != null && getControllerFactory != null && !getControllerFactory.isTornDown) {
      if (isAddingToConversation) {
        userSelectionConfirmationButton.setVisibility(if (getSelectedUsers.nonEmpty) View.VISIBLE else View.GONE)
      } else {
        val visible: Boolean = show || searchUserController.selectedUsers.nonEmpty
        conversationQuickMenu.setVisibility(if (visible) View.VISIBLE else View.GONE)
        inviteButton.setVisibility(if (visible || isKeyboardVisible || isTeamAccount) View.GONE else View.VISIBLE)
      }
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

        isGroupConversation.foreach { isGroup =>
          val users = if (isGroup) searchUserController.selectedUsers else getSelectedAndExcluded
          getContainer.onSelectedUsers(users.toSeq.asJava, ConversationChangeRequester.START_CONVERSATION)
        }(Threading.Ui)

      case R.id.invite_button =>
        sendGenericInvite(false)
      case R.id.ll_pickuser__error_invite =>
        sendGenericInvite(true)
    }
  }

  private def setFocusByCurrentPickerDestination(): Unit =
    // Don't trigger setting focus in closed split mode on tablet portrait, search is not visible then
    if (!(LayoutSpec.isTablet(getActivity) && isInPortrait(getActivity)) || getControllerFactory.getNavigationController.getPagerPosition != NavigationController.SECOND_PAGE)
      if ((getCurrentPickerDestination == IPickUserController.Destination.CONVERSATION_LIST) && (LayoutSpec.isTablet(getActivity) || getControllerFactory.getNavigationController.getPagerPosition == NavigationController.FIRST_PAGE))
        getControllerFactory.getFocusController.setFocus(IFocusController.CONVERSATION_LIST_SEARCHBOX)
      else if (getCurrentPickerDestination == IPickUserController.Destination.PARTICIPANTS)
        getControllerFactory.getFocusController.setFocus(IFocusController.PARTICIPANTS_SEARCHBOX)

  private lazy val isGroupConversation: Future[Boolean] =
    conversationId.fold(Future.successful(false))(conversationController.isGroup)

  private lazy val isAddingToConversation: Boolean =
    getArguments.getBoolean(PickUserFragment.ARGUMENT_ADD_TO_CONVERSATION)

  private lazy val conversationId: Option[ConvId] =
    Option(getArguments.getString(PickUserFragment.ARGUMENT_CONVERSATION_ID)).map(ConvId(_))

  private def isPrivateAccount: Boolean = !isTeamAccount

  private def isTeamAccount: Boolean = userAccountsController.isTeamAccount
  private def closeStartUI(): Unit = {
    KeyboardUtils.hideKeyboard(getActivity)
    searchUserController.setFilter("")
    integrationsController.searchQuery ! ""
    searchResultAdapter.peopleOrServices ! false
    pickUserController.hidePickUser(getCurrentPickerDestination)
  }

  private def getSelectedUsersJava: java.util.List[User] = {
    searchUserController.selectedUsers.map(uid => getStoreFactory.pickUserStore.getUser(uid.str)).toList.asJava
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
          inject[PermissionsService].requestAllPermissions(Set(READ_CONTACTS)).map {
            case true =>
              updateShareContacts(true)

            case _ =>
              updateShareContacts(false)
              val showRationale = shouldShowRequestPermissionRationale(READ_CONTACTS)
              if (!showRationale && getControllerFactory != null && !getControllerFactory.isTornDown) {
                getControllerFactory.getUserPreferencesController.setPerformedAction(
                  IUserPreferencesController.DO_NOT_SHOW_SHARE_CONTACTS_DIALOG)
              }
          } (Threading.Ui)
        }
      }).setNegativeButton(R.string.people_picker__share_contacts__nah, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          if (getControllerFactory != null && !getControllerFactory.isTornDown && checkedItems.size > 0) getControllerFactory.getUserPreferencesController.setPerformedAction(IUserPreferencesController.DO_NOT_SHOW_SHARE_CONTACTS_DIALOG)
        }
      }).create
      dialog.show()
    }
  }

  private def updateShareContacts(share: Boolean): Unit ={
    zms.head.flatMap(_.userPrefs.preference(UserPreferences.ShareContacts).update(share)) (Threading.Background)
  }

  override def onIntegrationClicked(data: IntegrationData): Unit = {
    KeyboardUtils.hideKeyboard(getActivity)
    verbose(s"onIntegrationClicked(${data.id})")

    val detailsController = inject[IntegrationDetailsController]
    conversationId.fold(detailsController.setPicking())(detailsController.setAdding)
    if (isAddingToConversation) {
      convScreenController.showIntegrationDetails(data.provider, data.id)
    } else {
      import IntegrationDetailsFragment._
      getFragmentManager.beginTransaction
        .setCustomAnimations(
          R.anim.slide_in_from_bottom_pick_user,
          R.anim.open_new_conversation__thread_list_out,
          R.anim.open_new_conversation__thread_list_in,
          R.anim.slide_out_to_bottom_pick_user)
        .replace(R.id.fl__conversation_list_main, newInstance(data.provider, data.id), Tag)
        .addToBackStack(Tag)
        .commit()

      getControllerFactory.getNavigationController.setLeftPage(Page.INTEGRATION_DETAILS, PickUserFragment.TAG)
    }
  }
}
