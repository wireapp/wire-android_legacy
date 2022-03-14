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

package com.waz.zclient.usersearch

import android.content.Intent
import android.os.Bundle
import android.view._
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import android.widget._
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.waz.content.UsersStorage
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.service.tracking.GroupConversationEvent
import com.waz.service.{BackendConfig, SearchQuery, ZMessaging}
import com.wire.signals.CancellableFuture
import com.waz.threading.Threading
import com.wire.signals.{Signal, Subscription}
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.common.controllers._
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.views.{FlatWireButton, PickableElement}
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.{CreateConversationController, CreateConversationManagerFragment}
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.integrations.IntegrationDetailsFragment
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.paintcode.ManageServicesIcon
import com.waz.zclient.search.SearchController
import com.waz.zclient.search.SearchController.{SearchUserListState, Tab}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.usersearch.domain.RetrieveSearchResults
import com.waz.zclient.usersearch.views.SearchEditText
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{IntentUtils, ResColor, RichView, UiStorage, UserSignal}
import com.waz.zclient.views._

import scala.concurrent.Future
import scala.concurrent.duration._
import com.waz.zclient.usersearch.SearchUIFragment._
import com.waz.threading.Threading._
import com.waz.zclient.participants.ParticipantsController

final class SearchUIFragment extends BaseFragment[Container]
  with FragmentHelper
  with SearchUIAdapter.Callback {

  import Threading.Implicits.Ui

  private implicit lazy val uiStorage: UiStorage = inject[UiStorage]

  private lazy val zms                    = inject[Signal[ZMessaging]]
  private lazy val usersStorage           = inject[Signal[UsersStorage]]
  private lazy val teamId                 = inject[Signal[Option[TeamId]]]
  private lazy val self                   = zms.flatMap(z => UserSignal(z.selfUserId))
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val accentColor            = inject[AccentColorController].accentColor.map(_.color)
  private lazy val conversationController = inject[ConversationController]
  private lazy val browser                = inject[BrowserController]
  private lazy val conversationListController     = inject[ConversationListController]
  private lazy val keyboard               = inject[KeyboardController]
  private lazy val spinner                = inject[SpinnerController]
  private lazy val pickUserController     = inject[IPickUserController]
  private lazy val conversationScreenController   = inject[IConversationScreenController]
  private lazy val navigationController   = inject[INavigationController]

  private lazy val adapter                = new SearchUIAdapter(this)
  private lazy val searchController       = inject[SearchController]
  private lazy val retrieveSearchResults  = new RetrieveSearchResults()
  private lazy val isFederationSupported  = inject[BackendConfig].federationSupport.isSupported

  private lazy val startUiToolbar         = view[Toolbar](R.id.pickuser_toolbar)

  private var subs = Set.empty[Subscription]

  private val conversationCreationInProgress = Signal(false)
  private lazy val guestLinksEnabled = inject[ParticipantsController].areGuestLinksEnabled

  private lazy val inviteButton = returning(view[FlatWireButton](R.id.invite_button)) { vh =>
    subs += userAccountsController.isTeam.flatMap {
      case true => Signal.const(false)
      case _    => keyboard.isKeyboardVisible.map(!_)
    }.onUi(vis => vh.foreach(_.setVisible(vis)))
  }

  private var scheduledSearchQuery = Option.empty[CancellableFuture[Unit]]

  private lazy val searchBox = returning(view[SearchEditText](R.id.sbv__search_box)) { vh =>
    subs += accentColor.onUi(color => vh.foreach(_.setCursorColor(color)))

    vh.foreach(_.setCallback(new SearchEditText.Callback {
      override def onRemovedTokenSpan(element: PickableElement): Unit = {}

      override def onFocusChange(hasFocus: Boolean): Unit = {}

      override def onClearButton(): Unit = closeStartUI()

      override def afterTextChanged(s: String): Unit = {
        scheduledSearchQuery.foreach(_.cancel())
        scheduledSearchQuery = Option(CancellableFuture.delay(PERFORM_SEARCH_DELAY).map { _ =>
          vh.foreach { view =>
            val filter = view.getSearchFilter
            if (filter != "@") searchController.filter! filter
          } // should be safe; if the fragment is destroyed, vh will be None
          scheduledSearchQuery = None
        })
      }
    }))
  }

  private lazy val toolbarTitle = returning(view[TypefaceTextView](R.id.pickuser_title)) { vh =>
    subs += userAccountsController.isTeam.flatMap {
      case false => userAccountsController.currentUser.map(_.map(_.name))
      case _     => userAccountsController.teamData.map(_.map(_.name))
    }.map(_.getOrElse(Name.Empty))
     .onUi(t => vh.foreach(_.setText(t)))
  }

  private lazy val emptyServicesIcon = returning(view[ImageView](R.id.empty_services_icon)) { vh =>
    subs += searchController.searchUserOrServices.map {
      case SearchUserListState.NoServices => View.VISIBLE
      case _ => View.GONE
    }.onUi(vis => vh.foreach(_.setVisibility(vis)))
  }

  private lazy val emptyServicesButton = returning(view[TypefaceTextView](R.id.empty_services_button)) { vh =>
    subs += (for {
      isAdmin <- userAccountsController.isAdmin
      res     <- searchController.searchUserOrServices
    } yield res match {
      case SearchUserListState.NoServices if isAdmin => View.VISIBLE
      case _ => View.GONE
    }).onUi(vis => vh.foreach(_.setVisibility(vis)))

    vh.onClick(_ => onManageServicesClicked())
  }

  private lazy val emptySearchIcon = returning(view[ImageView](R.id.empty_search_icon)) { vh =>
    subs += searchController.searchUserOrServices.map {
      case SearchUserListState.NoUsersFound => View.VISIBLE
      case _ => View.GONE
    }.onUi(vis => vh.foreach(_.setVisibility(vis)))
  }

  private lazy val emptySearchSameDomainText = returning(view[TypefaceTextView](R.id.empty_search_same_domain_text)) { vh =>
    subs += searchController.searchUserOrServices.map {
      case SearchUserListState.NoUsersFound => View.VISIBLE
      case _ => View.GONE
    }.onUi(vis => vh.foreach(_.setVisibility(vis)))
  }

  private lazy val emptySearchOtherDomainsText = returning(view[TypefaceTextView](R.id.empty_search_other_domains_text)) { vh =>
    subs += searchController.searchUserOrServices.zip(userAccountsController.isTeam).map {
      case (SearchUserListState.NoUsersFound, true) => View.VISIBLE
      case _ => View.GONE
    }.onUi(vis => vh.foreach(_.setVisibility(vis)))
  }

  private lazy val emptySearchButton = returning(view[TypefaceTextView](R.id.empty_search_button)) { vh =>
    subs += searchController.searchUserOrServices.map {
      case SearchUserListState.NoUsersFound => View.VISIBLE
      case _ => View.GONE
    }.onUi(vis => vh.foreach(_.setVisibility(vis)))

    vh.onClick(_ => browser.openHelp())
  }

  private lazy val errorMessageView = returning(view[TypefaceTextView](R.id.pickuser__error_text)) { vh =>
    if (isFederationSupported) {
      subs += searchController.searchUserOrServices.map {
        case SearchUserListState.Services(_) |
             SearchUserListState.Users(_) |
             SearchUserListState.NoUsers |
             SearchUserListState.NoUsersFound => View.GONE
        case _ => View.VISIBLE
      }.onUi(vis => vh.foreach(_.setVisibility(vis)))

      subs += (for {
        isAdmin <- userAccountsController.isAdmin
        res     <- searchController.searchUserOrServices
      } yield res match {
        case SearchUserListState.NoServices if isAdmin => R.string.empty_services_list_admin
        case SearchUserListState.NoServices => R.string.empty_services_list
        case SearchUserListState.NoServicesFound => R.string.no_matches_found
        case SearchUserListState.LoadingServices => R.string.loading_services
        case SearchUserListState.Error(_) => R.string.generic_error_header
        case _ => R.string.empty_string //TODO more informative header?
      }).onUi(txt => vh.foreach(_.setText(txt)))
    } else {
      subs += searchController.searchUserOrServices.map {
        case SearchUserListState.Services(_) | SearchUserListState.Users(_) => View.GONE
        case _ => View.VISIBLE
      }.onUi(vis => vh.foreach(_.setVisibility(vis)))

      subs += (for {
        isAdmin <- userAccountsController.isAdmin
        res     <- searchController.searchUserOrServices
      } yield res match {
        case SearchUserListState.NoUsers => R.string.new_conv_no_contacts
        case SearchUserListState.NoUsersFound => R.string.new_conv_no_results
        case SearchUserListState.NoServices if isAdmin => R.string.empty_services_list_admin
        case SearchUserListState.NoServices => R.string.empty_services_list
        case SearchUserListState.NoServicesFound => R.string.no_matches_found
        case SearchUserListState.LoadingServices => R.string.loading_services
        case SearchUserListState.Error(_) => R.string.generic_error_header
        case _ => R.string.empty_string //TODO more informative header?
      }).onUi(txt => vh.foreach(_.setText(txt)))
    }
  }

  private lazy val emptyListButton = returning(view[RelativeLayout](R.id.empty_list_button)) { v =>
    subs += (for {
      zms         <- zms
      permissions <- userAccountsController.selfPermissions.orElse(Signal.const(Set.empty[UserPermissions.Permission]))
      members     <- zms.teams.searchTeamMembers(SearchQuery.Empty).orElse(Signal.const(Set.empty[UserData]))
      searching   <- searchController.filter.map(_.nonEmpty)
     } yield
       zms.teamId.nonEmpty && permissions(UserPermissions.Permission.AddTeamMember) && !members.exists(_.id != zms.selfUserId) && !searching
    ).onUi(visible => v.foreach(_.setVisible(visible)))
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
    val searchResultRecyclerView = view[RecyclerView](R.id.rv__pickuser__header_list_view)
    searchResultRecyclerView.foreach { rv =>
      rv.setLayoutManager(new LinearLayoutManager(getActivity))
      rv.setAdapter(adapter)
    }

    retrieveSearchResults.resultsData.onUi(adapter.updateResults)

    searchBox

    inviteButton.foreach { btn =>
      btn.setText(R.string.pref_invite_title)
      btn.setGlyph(R.string.glyph__invite)
    }

    emptyListButton.foreach(_.onClick(browser.openStartUIManageTeam()))
    errorMessageView
    toolbarTitle
    emptyServicesButton

    if (isFederationSupported) {
      emptySearchIcon
      emptySearchSameDomainText
      emptySearchOtherDomainsText
      emptySearchButton
    }

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
    searchController.tab.map(_ == Tab.People).map(if (_) 0 else 1).head.foreach(tabs.getTabAt(_).select())

    tabs.addOnTabSelectedListener(new OnTabSelectedListener {
      override def onTabSelected(tab: TabLayout.Tab): Unit = {
        tab.getPosition match {
          case 0 => searchController.tab ! Tab.People
          case 1 => searchController.tab ! Tab.Services
        }
        searchBox.foreach(_.removeAllElements())
      }

      override def onTabUnselected(tab: TabLayout.Tab): Unit = {}
      override def onTabReselected(tab: TabLayout.Tab): Unit = {}
    })

    subs += (for {
      isTeam     <- userAccountsController.isTeam
      isExternal <- userAccountsController.isExternal
    } yield isTeam && !isExternal).onUi(tabs.setVisible)

    searchController.filter ! ""

    containerSub = Some((for {
      kb <- keyboard.isKeyboardVisible
      ac <- accentColor
      filterEmpty = !searchBox.flatMap(v => Option(v.getSearchFilter).map(_.isEmpty)).getOrElse(true)
    } yield if (kb || filterEmpty) getColor(R.color.people_picker__loading__color) else ac)
      .onUi(getContainer.getLoadingViewIndicator.setColor))

    emptyServicesIcon.foreach(_.setImageDrawable(ManageServicesIcon(ResColor.fromId(R.color.white_24))))
  }

  override def onResume(): Unit = {
    super.onResume()
    inviteButton.foreach(_.onClick(sendGenericInvite(false)))

    CancellableFuture.delay(getInt(R.integer.people_picker__keyboard__show_delay).millis).map { _ =>

      conversationListController.establishedConversations.head.map(_.size > SHOW_KEYBOARD_THRESHOLD).flatMap {
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

  override def onDestroy(): Unit = {
    containerSub.foreach(_.destroy())
    containerSub = None
    subs.foreach(_.destroy())
    subs = Set.empty
    super.onDestroyView()
  }

  override def onBackPressed(): Boolean =
    if (keyboard.hideKeyboardIfVisible()) true
    else if (pickUserController.isShowingUserProfile) {
      pickUserController.hideUserProfile()
      true
    }
    else false

  override def onUserClicked(user: UserData): Unit = {
    def checkStorageAndThen(doStuff: => Unit) = for {
      storage <- usersStorage.head
      _       <- storage.getOrCreate(user.id, user)
    } yield doStuff

    def tryOpenConversation(): Unit = conversationCreationInProgress.head.foreach {
      case false =>
        checkStorageAndThen {
          spinner.showSpinner(true)
          conversationCreationInProgress ! true
          userAccountsController
            .getOrCreateAndOpenConvFor(user.id)
            .onComplete { _ =>
              spinner.hideSpinner()
              conversationCreationInProgress ! false
            }
        }
      case true =>
    }

    def showUserProfile(): Unit = {
      conversationScreenController.setPopoverLaunchedMode(DialogLaunchMode.SEARCH)
      checkStorageAndThen {
        pickUserController.showUserProfile(user.id, false)
      }
    }

    import ConnectionStatus._
    keyboard.hideKeyboardIfVisible()
    teamId.head.map((_, user.connection)).foreach {
      case (Some(_), Accepted) =>
        tryOpenConversation()
      case (Some(tId), Unconnected) if user.teamId.contains(tId) =>
        tryOpenConversation()
      case (None, Accepted) =>
        tryOpenConversation()
      case (None, PendingFromOther) =>
        checkStorageAndThen {
          getContainer.showIncomingPendingConnectRequest(ConvId(user.id.str))
        }
      case (_, connection) if connectionsForOpenProfile.contains(connection) =>
        showUserProfile()

      case (teamId, connection) =>
        warn(l"Unhandled connection type. The UI shouldn't display such entry. teamId: $teamId, connection type: $connection")
    }
  }

  override def onConversationClicked(conversationData: ConversationData): Unit = {
    keyboard.hideKeyboardIfVisible()
    verbose(l"onConversationClicked(${conversationData.id})")
    conversationController.selectConv(Some(conversationData.id), ConversationChangeRequester.START_CONVERSATION)
  }

  override def onManageServicesClicked(): Unit = browser.openManageServices()

  override def onCreateConversationClicked(): Unit = {
    keyboard.hideKeyboardIfVisible()
    inject[CreateConversationController].setCreateConversation(from = GroupConversationEvent.StartUi)
    getFragmentManager.beginTransaction
      .setCustomAnimations(
        R.anim.slide_in_from_bottom_pick_user,
        R.anim.open_new_conversation__thread_list_out,
        R.anim.open_new_conversation__thread_list_in,
        R.anim.slide_out_to_bottom_pick_user)
      .replace(R.id.fl__conversation_list_main, CreateConversationManagerFragment.newInstance, CreateConversationManagerFragment.Tag)
      .addToBackStack(CreateConversationManagerFragment.Tag)
      .commit()
  }


  override def onCreateGuestRoomClicked(): Unit =
    Signal.zip(conversationCreationInProgress, guestLinksEnabled).head.foreach {
      case (false, true) =>
        conversationCreationInProgress ! true
        spinner.showSpinner(true)
        keyboard.hideKeyboardIfVisible()
        conversationController.createGuestRoom().flatMap { conv =>
          spinner.hideSpinner()
          conversationController.selectConv(Some(conv.id), ConversationChangeRequester.START_CONVERSATION)
        }.onComplete(_ => conversationCreationInProgress ! false)
      case _ =>
    }

  private def sendGenericInvite(fromSearch: Boolean): Unit =
    self.head.map { self =>
      val sharingIntent = IntentUtils.getInviteIntent(
        getString(R.string.people_picker__invite__share_text__header, self.name.str),
        getString(R.string.people_picker__invite__share_text__body,self.displayHandle())
      )
      startActivity(Intent.createChooser(sharingIntent, getString(R.string.people_picker__invite__share_details_dialog)))
    }

  private def closeStartUI(): Unit = {
    keyboard.hideKeyboardIfVisible()
    searchController.filter! ""
    searchController.tab ! Tab.People
    pickUserController.hidePickUser()
  }

  override def onIntegrationClicked(data: IntegrationData): Unit = {
    keyboard.hideKeyboardIfVisible()
    verbose(l"onIntegrationClicked(${data.id})")

    import IntegrationDetailsFragment._
    getFragmentManager.beginTransaction
      .setCustomAnimations(
        R.anim.slide_in_from_bottom_pick_user,
        R.anim.open_new_conversation__thread_list_out,
        R.anim.open_new_conversation__thread_list_in,
        R.anim.slide_out_to_bottom_pick_user)
      .replace(R.id.fl__conversation_list_main, newAddingInstance(data), Tag)
      .addToBackStack(Tag)
      .commit()

    navigationController.setLeftPage(Page.INTEGRATION_DETAILS, TAG)
  }

  override def onContactsExpanded(): Unit = {
    retrieveSearchResults.expandContacts()
  }

  override def onGroupsExpanded(): Unit = {
    retrieveSearchResults.expandGroups()
  }
}

object SearchUIFragment {
  val TAG: String = classOf[SearchUIFragment].getName

  private val SHOW_KEYBOARD_THRESHOLD: Int = 10
  private val PERFORM_SEARCH_DELAY = 500.millis

  import ConnectionStatus._
  private val connectionsForOpenProfile = Set(PendingFromUser, Blocked, BlockedDueToMissingLegalHoldConsent, Ignored, Cancelled, Unconnected)

  def newInstance(): SearchUIFragment = new SearchUIFragment

  trait Container {
    def showIncomingPendingConnectRequest(conv: ConvId): Unit

    def getLoadingViewIndicator: LoadingIndicatorView
  }

}
