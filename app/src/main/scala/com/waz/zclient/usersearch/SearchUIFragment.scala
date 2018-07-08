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
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.design.widget.TabLayout.OnTabSelectedListener
import android.support.v7.app.AlertDialog
import android.support.v7.widget.{LinearLayoutManager, RecyclerView, Toolbar}
import android.view._
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import android.widget._
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.content.UserPreferences
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.service.tracking.{GroupConversationEvent, TrackingEvent, TrackingService}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{Signal, Subscription}
import com.waz.utils.returning
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient._
import com.waz.zclient.common.controllers._
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.views.{FlatWireButton, PickableElement}
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.{CreateConversationController, CreateConversationManagerFragment}
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.integrations.{IntegrationDetailsController, IntegrationDetailsFragment}
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.usersearch.views.SearchEditText
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{IntentUtils, RichView, StringUtils, UiStorage, UserSignal}
import com.waz.zclient.views._

import scala.concurrent.Future
import scala.concurrent.duration._

class SearchUIFragment extends BaseFragment[SearchUIFragment.Container]
  with FragmentHelper
  with SearchUIAdapter.Callback {

  import SearchUIFragment._
  import Threading.Implicits.Ui

  private implicit lazy val uiStorage = inject[UiStorage]

  private implicit def context = getContext

  private lazy val zms                    = inject[Signal[ZMessaging]]
  private lazy val self                   = zms.flatMap(z => UserSignal(z.selfUserId))
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val accentColor            = inject[AccentColorController].accentColor.map(_.getColor())
  private lazy val conversationController = inject[ConversationController]
  private lazy val browser                = inject[BrowserController]
  private lazy val integrationsController = inject[IntegrationsController]
  private lazy val convListController     = inject[ConversationListController]
  private lazy val keyboard               = inject[KeyboardController]
  private lazy val tracking               = inject[TrackingService]

  private lazy val pickUserController     = inject[IPickUserController]
  private lazy val convScreenController   = inject[IConversationScreenController]
  private lazy val navigationController   = inject[INavigationController]

  private lazy val shareContactsPref      = zms.map(_.userPrefs.preference(UserPreferences.ShareContacts))
  private lazy val showShareContactsPref  = zms.map(_.userPrefs.preference(UserPreferences.ShowShareContacts))

  private lazy val adapter = new SearchUIAdapter(this, integrationsController)

  private lazy val searchResultRecyclerView = view[RecyclerView](R.id.rv__pickuser__header_list_view)
  private lazy val startUiToolbar           = view[Toolbar](R.id.pickuser_toolbar)

  private lazy val inviteButton = returning(view[FlatWireButton](R.id.invite_button)) { vh =>
    userAccountsController.isTeam.flatMap {
      case true => Signal.const(false)
      case _    => keyboard.isKeyboardVisible.map(!_)
    }.onUi(vis => vh.foreach(_.setVisible(vis)))
  }

  private lazy val searchBox = returning(view[SearchEditText](R.id.sbv__search_box)) { vh =>
    accentColor.onUi(color => vh.foreach(_.setCursorColor(color)))
  }

  private lazy val toolbarTitle = returning(view[TypefaceTextView](R.id.pickuser_title)) { vh =>
    userAccountsController.isTeam.flatMap {
      case false => userAccountsController.currentUser.map(_.map(_.name))
      case _     => userAccountsController.teamData.map(_.map(_.name))
    }.map(_.getOrElse(""))
     .onUi(t => vh.foreach(_.setText(t)))
  }

  private lazy val errorMessageView = returning(view[TypefaceTextView](R.id.pickuser__error_text)) { vh =>
    (for {
      integrationTab <- adapter.peopleOrServices
      hasSearch      <- integrationsController.searchQuery.map(_.nonEmpty)
      hasResults     <- integrationsController.searchIntegrations.map(_.forall(_.nonEmpty))
    } yield integrationTab && hasSearch && !hasResults).onUi { show =>
      vh.foreach(_.setVisible(show))
    }
  }

  private lazy val emptyListButton = returning(view[RelativeLayout](R.id.empty_list_button)) { v =>
    (for {
      zms <- zms
      permissions <- userAccountsController.selfPermissions.orElse(Signal.const(Set.empty[AccountDataOld.Permission]))
      members <- zms.teams.searchTeamMembers().orElse(Signal.const(Set.empty[UserData]))
      searching <- adapter.filter.map(_.nonEmpty)
     } yield
       zms.teamId.nonEmpty && permissions(AccountDataOld.Permission.AddTeamMember) && !members.exists(_.id != zms.selfUserId) && !searching
    ).onUi(visible => v.foreach(_.setVisible(visible)))
  }

  private val searchBoxViewCallback = new SearchEditText.Callback {
    override def onRemovedTokenSpan(element: PickableElement): Unit = {}

    override def onFocusChange(hasFocus: Boolean): Unit = {}

    override def onClearButton(): Unit = closeStartUI()

    override def afterTextChanged(s: String): Unit = searchBox.foreach { v =>
      val filter = v.getSearchFilter
      adapter.filter ! filter
      integrationsController.searchQuery ! filter
    }
  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    if (nextAnim == 0 || getContainer == null)
      super.onCreateAnimation(transit, enter, nextAnim)
    else if (pickUserController.isHideWithoutAnimations)
      new DefaultPageTransitionAnimation(0, getOrientationIndependentDisplayHeight(getActivity), enter, 0, 0, 1f)
    else if (enter)
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

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_pick_user, viewContainer, false)

  private var containerSub = Option.empty[Subscription] //TODO remove subscription...

  override def onViewCreated(rootView: View, savedInstanceState: Bundle): Unit = {
    searchResultRecyclerView.foreach { rv =>
      rv.setLayoutManager(new LinearLayoutManager(getActivity))
      rv.setAdapter(adapter)
    }

    searchBox.foreach(_.setCallback(searchBoxViewCallback))

    inviteButton.foreach { btn =>
      btn.setText(R.string.pref_invite_title)
      btn.setGlyph(R.string.glyph__invite)
    }

    emptyListButton.foreach(_.onClick(browser.openUrl(AndroidURIUtil.parse(getString(R.string.pick_user_manage_team_url)))))
    errorMessageView
    toolbarTitle

    // Use constant style for left side start ui
    startUiToolbar.foreach(_.setVisibility(View.VISIBLE))
    searchBox.foreach(_.applyDarkTheme(true))
    startUiToolbar.foreach { toolbar =>
      toolbar.inflateMenu(R.menu.toolbar_close_white)
      toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
        override def onMenuItemClick(item: MenuItem): Boolean = {
          if (item.getItemId == R.id.close) closeStartUI()
          false
        }
      })
    }


    searchBox.foreach(_.setOnEditorActionListener(new OnEditorActionListener {
      override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean =
        if (actionId == EditorInfo.IME_ACTION_SEARCH) keyboard.hideKeyboardIfVisible() else false
    }))

    val tabs = findById[TabLayout](rootView, R.id.pick_user_tabs)
    adapter.peopleOrServices.map(if (_) 1 else 0).head.foreach(tabs.getTabAt(_).select())

    tabs.addOnTabSelectedListener(new OnTabSelectedListener {
      override def onTabSelected(tab: TabLayout.Tab): Unit = {
        tab.getPosition match {
          case 0 => adapter.peopleOrServices ! false
          case 1 => adapter.peopleOrServices ! true
        }
        searchBox.foreach(_.removeAllElements())
      }

      override def onTabUnselected(tab: TabLayout.Tab): Unit = {}

      override def onTabReselected(tab: TabLayout.Tab): Unit = {}
    })

    zms.map(_.teamId.nonEmpty && internalVersion).head.foreach(tabs.setVisible)(Threading.Ui)

    adapter.filter ! ""
    integrationsController.searchQuery ! ""

    containerSub = Some((for {
      kb <- keyboard.isKeyboardVisible
      ac <- accentColor
      filterEmpty = !searchBox.flatMap(v => Option(v.getSearchFilter).map(_.isEmpty)).getOrElse(true)
    } yield if (kb || filterEmpty) getColor(R.color.people_picker__loading__color) else ac)
      .onUi(getContainer.getLoadingViewIndicator.setColor))
  }

  override def onStart(): Unit = {
    super.onStart()
    userAccountsController.isTeam.head.map {
      case true => //
      case _    => showShareContactsDialog()
    }
  }

  override def onResume(): Unit = {
    super.onResume()
    inviteButton.foreach(_.onClick(sendGenericInvite(false)))

    CancellableFuture.delay(getInt(R.integer.people_picker__keyboard__show_delay).millis).map { _ =>

      convListController.establishedConversations.head.map(_.size > SearchUIFragment.SHOW_KEYBOARD_THRESHOLD).flatMap {
        case true => userAccountsController.isTeam.head.map {
          case true => searchBox.foreach { v =>
            v.setFocus()
            keyboard.showKeyboardIfHidden()
          }
          case _ => //
        }
        case _ => Future.successful({})
      }
    }
  }

  override def onPause(): Unit = {
    inviteButton.foreach(_.setOnClickListener(null))
    super.onPause()
  }

  override def onDestroyView() = {
    containerSub.foreach(_.destroy())
    containerSub = None
    super.onDestroyView()
  }

  override def onBackPressed(): Boolean =
    if (keyboard.hideKeyboardIfVisible()) true
    else if (pickUserController.isShowingUserProfile) {
      pickUserController.hideUserProfile()
      true
    }
    else false

  override def onUserClicked(userId: UserId): Unit = {
    zms.head.flatMap { z =>
      z.usersStorage.get(userId).map {
        case Some(user) =>
          import ConnectionStatus._
          keyboard.hideKeyboardIfVisible()
          if (user.connection == Accepted || (user.connection == Unconnected && z.teamId.isDefined && z.teamId == user.teamId))
            userAccountsController.getOrCreateAndOpenConvFor(userId)
          else {
            Future { user.connection match {
              case PendingFromUser | Blocked | Ignored | Cancelled | Unconnected =>
                convScreenController.setPopoverLaunchedMode(DialogLaunchMode.SEARCH)
                pickUserController.showUserProfile(userId)
              case ConnectionStatus.PendingFromOther =>
                getContainer.showIncomingPendingConnectRequest(ConvId(userId.str))
              case _ =>
            }}
          }
        case _ =>
      }
    }
  }

  override def onConversationClicked(conversationData: ConversationData): Unit = {
    keyboard.hideKeyboardIfVisible()
    verbose(s"onConversationClicked(${conversationData.id})")
    conversationController.selectConv(Some(conversationData.id), ConversationChangeRequester.START_CONVERSATION)
  }

  override def onCreateConvClicked(): Unit = {
    keyboard.hideKeyboardIfVisible()
    inject[CreateConversationController].setCreateConversation(from = GroupConversationEvent.StartUi)
    getFragmentManager.beginTransaction
      .setCustomAnimations(
        R.anim.slide_in_from_bottom_pick_user,
        R.anim.open_new_conversation__thread_list_out,
        R.anim.open_new_conversation__thread_list_in,
        R.anim.slide_out_to_bottom_pick_user)
      .replace(R.id.fl__conversation_list_main, new CreateConversationManagerFragment, CreateConversationManagerFragment.Tag)
      .addToBackStack(CreateConversationManagerFragment.Tag)
      .commit()
  }


  override def onCreateGuestRoomClicked(): Unit = {
    tracking.track(TrackingEvent("guest_rooms.guest_room_creation"))
    keyboard.hideKeyboardIfVisible()
    conversationController.createGuestRoom().map { conv =>
      conversationController.selectConv(Some(conv.id), ConversationChangeRequester.START_CONVERSATION)
    } (Threading.Ui)
  }

  private def sendGenericInvite(fromSearch: Boolean): Unit =
    self.head.map { self =>
      val sharingIntent = IntentUtils.getInviteIntent(
        getString(R.string.people_picker__invite__share_text__header, self.getDisplayName),
        getString(R.string.people_picker__invite__share_text__body, StringUtils.formatHandle(self.handle.map(_.string).getOrElse(""))))
      startActivity(Intent.createChooser(sharingIntent, getString(R.string.people_picker__invite__share_details_dialog)))
    }

  private def closeStartUI(): Unit = {
    keyboard.hideKeyboardIfVisible()
    adapter.filter ! ""
    integrationsController.searchQuery ! ""
    adapter.peopleOrServices ! false
    pickUserController.hidePickUser()
  }

  // XXX Only show contact sharing dialogs for PERSONAL START UI
  private def showShareContactsDialog(): Unit = {
    (for {
      false <- shareContactsPref.head.flatMap(_.apply())(Threading.Background)
      true  <- showShareContactsPref.head.flatMap(_.apply())(Threading.Background)
    } yield {}).map { _ =>
      val checkBoxView= View.inflate(getContext, R.layout.dialog_checkbox, null)
      val checkBox = checkBoxView.findViewById(R.id.checkbox).asInstanceOf[CheckBox]
      var checked = false

      checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit =
          checked = isChecked
      })
      checkBox.setText(R.string.people_picker__share_contacts__nevvah)

      new AlertDialog.Builder(getContext)
        .setTitle(R.string.people_picker__share_contacts__title)
        .setMessage(R.string.people_picker__share_contacts__message)
        .setView(checkBoxView)
        .setPositiveButton(R.string.people_picker__share_contacts__yay,
          new DialogInterface.OnClickListener() {
            def onClick(dialog: DialogInterface, which: Int): Unit =
              inject[PermissionsService].requestAllPermissions(Set(READ_CONTACTS)).map { granted =>
                shareContactsPref.head.flatMap(_ := granted)
                if (!granted && !shouldShowRequestPermissionRationale(READ_CONTACTS)) showShareContactsPref.head.flatMap(_ := false)
              }
          })
        .setNegativeButton(R.string.people_picker__share_contacts__nah,
          new DialogInterface.OnClickListener() {
            def onClick(dialog: DialogInterface, which: Int): Unit =
              if (checked) showShareContactsPref.head.flatMap(_ := false)
          }).create
        .show()
    }
  }

  override def onIntegrationClicked(data: IntegrationData): Unit = {
    keyboard.hideKeyboardIfVisible()
    verbose(s"onIntegrationClicked(${data.id})")

    val detailsController = inject[IntegrationDetailsController]
    detailsController.setPicking()
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

    navigationController.setLeftPage(Page.INTEGRATION_DETAILS, SearchUIFragment.TAG)
  }
}

object SearchUIFragment {
  val TAG: String = classOf[SearchUIFragment].getName
  private val DEFAULT_SELECTED_INVITE_METHOD: Int = 0
  private val SHOW_KEYBOARD_THRESHOLD: Int = 10

  val internalVersion = BuildConfig.APPLICATION_ID match {
    case "com.wire.internal" | "com.waz.zclient.dev" | "com.wire.x" | "com.wire.qa" => true
    case _ => false
  }

  def newInstance(): SearchUIFragment =
    new SearchUIFragment

  trait Container {
    def showIncomingPendingConnectRequest(conv: ConvId): Unit

    def getLoadingViewIndicator: LoadingIndicatorView
  }

}
