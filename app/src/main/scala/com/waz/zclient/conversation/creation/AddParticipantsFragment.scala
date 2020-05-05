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
package com.waz.zclient.conversation.creation

import android.content.Context
import android.os.Bundle
import android.view._
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import android.widget.{ImageView, TextView}
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.tracking.{OpenSelectParticipants, TrackingService}
import com.waz.threading.Threading
import com.waz.utils.events._
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.common.controllers.{BrowserController, ThemeController, UserAccountsController}
import com.waz.zclient.common.views.{PickableElement, SingleUserRowView}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.paintcode.ManageServicesIcon
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.search.SearchController
import com.waz.zclient.search.SearchController.{AddUserListState, Tab}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.usersearch.views.{PickerSpannableEditText, SearchEditText}
import com.waz.zclient.utils.ContextUtils.getStyledColor
import com.waz.zclient.utils.{ResColor, RichView}

import scala.collection.immutable.Set
import scala.concurrent.Future

class AddParticipantsFragment extends FragmentHelper {

  import AddParticipantsFragment._
  import Threading.Implicits.Background
  implicit def cxt: Context = getContext

  private lazy val zms                = inject[Signal[ZMessaging]]
  private lazy val newConvController  = inject[CreateConversationController]
  private lazy val keyboard           = inject[KeyboardController]
  private lazy val tracking           = inject[TrackingService]
  private lazy val themeController    = inject[ThemeController]
  private lazy val userAccounts       = inject[UserAccountsController]
  private lazy val browserController  = inject[BrowserController]

  private lazy val adapter = AddParticipantsAdapter(newConvController.users, newConvController.integrations)

  private lazy val searchBox = returning(view[SearchEditText](R.id.search_box)) { vh =>
    new FutureEventStream[(Either[UserId, (ProviderId, IntegrationId)], Boolean), (Pickable, Boolean)](adapter.onSelectionChanged, {
      case (Left(userId), selected) =>
        zms.head.flatMap(_.usersStorage.get(userId).collect {
          case Some(u) => (Pickable(userId.str, u.name), selected)
        })
      case (Right((pId, iId)), selected) =>
        zms.head.flatMap(_.integrations.getIntegration(pId, iId).collect {
          case Right(service) => (Pickable(iId.str, service.name), selected)
        })
    }).onUi {
      case (pu, selected) =>
        vh.foreach { v =>
          if (selected) v.addElement(pu) else v.removeElement(pu)
        }
    }
  }

  private lazy val tabs = returning(view[TabLayout](R.id.add_users_tabs)) { vh =>
    vh.foreach { tabs =>
      tabs.setSelectedTabIndicatorColor(themeController.getThemeDependentOptionsTheme.getTextColorPrimary)

      adapter.tab.map(_ == Tab.People).map(if (_) 0 else 1).head.foreach(tabs.getTabAt(_).select())

      tabs.addOnTabSelectedListener(new OnTabSelectedListener {
        override def onTabSelected(tab: TabLayout.Tab): Unit =
          adapter.tab ! (if (tab.getPosition == 0) Tab.People else Tab.Services)

        override def onTabUnselected(tab: TabLayout.Tab): Unit = {}
        override def onTabReselected(tab: TabLayout.Tab): Unit = {}
      })

      tabs.setVisible(false)

      (for {
        false              <- newConvController.convId.map(_.isEmpty)
        isTeamAccount      <- inject[UserAccountsController].isTeam
        isTeamOnlyConv     <- inject[ConversationController].currentConvIsTeamOnly
        currentUserInTeam  <- inject[ParticipantsController].currentUserBelongsToConversationTeam
        _ = verbose(l"should the tabs be visible: (is team account: $isTeamAccount, team only: $isTeamOnlyConv, in team: $currentUserInTeam)")
      } yield isTeamAccount && !isTeamOnlyConv && currentUserInTeam)
        .onUi(tabs.setVisible)
    }
  }

  private lazy val emptyServicesIcon = returning(view[ImageView](R.id.empty_services_icon)) { vh =>
    adapter.searchResults.map {
      case AddUserListState.NoServices => View.VISIBLE
      case _ => View.GONE
    }.onUi(vis => vh.foreach(_.setVisibility(vis)))

    themeController.currentTheme.map(themeController.getTheme).onUi { th =>
      vh.foreach(_.setImageDrawable(ManageServicesIcon(ResColor.fromColor(ColorUtils.setAlphaComponent(getStyledColor(R.attr.wirePrimaryTextColor, th), 52)))))
    }
  }

  private lazy val emptyServicesButton = returning(view[TypefaceTextView](R.id.empty_services_button)) { vh =>
    (for {
      isAdmin <- userAccounts.isAdmin
      res     <- adapter.searchResults
    } yield res match {
      case AddUserListState.NoServices if isAdmin => View.VISIBLE
      case _ => View.GONE
    }).onUi(vis => vh.foreach(_.setVisibility(vis)))

    vh.onClick(_ => browserController.openManageServices())
  }

  private lazy val errorText = returning(view[TypefaceTextView](R.id.empty_search_message)) { vh =>
    adapter.searchResults.map {
      case AddUserListState.Services(_) | AddUserListState.Users(_) => View.GONE
      case _ => View.VISIBLE
    }.onUi(vis => vh.foreach(_.setVisibility(vis)))

    (for {
      isAdmin <- userAccounts.isAdmin
      res     <- adapter.searchResults
    } yield res match {
      case AddUserListState.NoUsers               => R.string.new_conv_no_contacts
      case AddUserListState.NoUsersFound          => R.string.new_conv_no_results
      case AddUserListState.NoServices if isAdmin => R.string.empty_services_list_admin
      case AddUserListState.NoServices            => R.string.empty_services_list
      case AddUserListState.NoServicesFound       => R.string.no_matches_found
      case AddUserListState.LoadingServices       => R.string.loading_services
      case AddUserListState.Error(_)              => R.string.generic_error_header
      case _                                      => R.string.empty_string //TODO more informative header?
    }).onUi(txt => vh.foreach(_.setText(txt)))
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.create_conv_pick_fragment, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    val recyclerView = findById[RecyclerView](R.id.recycler_view)
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext))
    recyclerView.setAdapter(adapter)

    newConvController.fromScreen.head.map { f =>
      tracking.track(OpenSelectParticipants(f))
    }

    searchBox.foreach { v =>
      v.applyDarkTheme(themeController.isDarkTheme)
      v.setCallback(new PickerSpannableEditText.Callback{
        override def onRemovedTokenSpan(element: PickableElement): Unit = {
          newConvController.users.mutate(_ - UserId(element.id))
          newConvController.integrations.mutate(_.filterNot(_._2.str == element.id))
        }
        override def afterTextChanged(s: String): Unit =
          adapter.filter ! s
      })
      v.setOnEditorActionListener(new OnEditorActionListener {
        override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean =
          if (actionId == EditorInfo.IME_ACTION_SEARCH) keyboard.hideKeyboardIfVisible() else false
      })
    }

    (for {
      zms                    <- zms.head
      selectedUserIds        <- newConvController.users.head
      selectedUsers          <- zms.usersStorage.listAll(selectedUserIds)
      selectedIntegrationIds <- newConvController.integrations.head
      selectedIntegrations   <- Future.sequence(selectedIntegrationIds.map {
        case (pId, iId) => zms.integrations.getIntegration(pId, iId).collect {
          case Right(service) => service
        }
      })
    } yield selectedUsers.map(Left(_)) ++ selectedIntegrations.toSeq.map(Right(_)))
      .map(_.foreach {
        case Left(user)     => searchBox.foreach(_.addElement(Pickable(user.id.str, user.name)))
        case Right(service) => searchBox.foreach(_.addElement(Pickable(service.id.str, service.name)))
      })(Threading.Ui)

    //lazy init
    tabs
    errorText
    emptyServicesButton
    emptyServicesIcon
  }

  private def close() = {
    keyboard.hideKeyboardIfVisible()
    getFragmentManager.popBackStack()
  }
}

object AddParticipantsFragment {

  val ShowKeyboardThreshold = 10
  val Tag: String = getClass.getSimpleName

  private case class Pickable(id : String, name: String) extends PickableElement
}

case class AddParticipantsAdapter(usersSelected: SourceSignal[Set[UserId]],
                                  servicesSelected: SourceSignal[Set[(ProviderId, IntegrationId)]])
                                 (implicit context: Context, eventContext: EventContext, injector: Injector)
  extends RecyclerView.Adapter[SelectableRowViewHolder]
    with Injectable
    with DerivedLogTag {
  
  import AddParticipantsAdapter._

  private implicit val ctx = context
  private lazy val themeController = inject[ThemeController]
  private lazy val teamId = inject[Signal[Option[TeamId]]]
  private lazy val currentUserController = inject[UserAccountsController]

  private val searchController = new SearchController()

  val filter = searchController.filter
  val tab    = searchController.tab
  val searchResults = searchController.addUserOrServices

  setHasStableIds(true)

  private var results = Seq.empty[(Either[UserData, IntegrationData], Boolean)]
  private var team    = Option.empty[TeamId]

  val onSelectionChanged = EventStream[(Either[UserId, (ProviderId, IntegrationId)], Boolean)]()

  (for {
    res           <- searchResults
    currentUser   <- currentUserController.currentUser
    usersSelected <- usersSelected
    _teamId       <- teamId
    servsSelected <- servicesSelected
  } yield (_teamId, res, usersSelected, servsSelected, currentUser)).onUi {
    case (teamId, res, usersSelected, servsSelected, currentUser) =>
      team = teamId
      val prev = this.results

      import AddUserListState._

      val (localResults, directoryResults) = res match {
        case AddUserListState.Users(search) => (search.local, search.dir)
        case _ => (Nil, Nil)
      }

      val directoryTeamMembers = currentUser.map(_.teamId) match {
        case Some(_)      => directoryResults.filter(_.teamId == teamId)
        case None         => Nil
      }

      val userResults = (localResults ++ directoryTeamMembers).distinctBy(_.id)
      val integrationResults = res match {
        case Services(ss) => ss
        case _ => Seq.empty
      }

      this.results = userResults.map(u => (Left(u), usersSelected.contains(u.id))) ++ integrationResults.map(i => (Right(i), servsSelected.contains((i.provider, i.id))))
      if (prev.map(_._1).toSet == (userResults ++ integrationResults).toSet) {
        val changedPositions = prev.map {
          case (Left(user), selected) =>
            if (selected && !usersSelected.contains(user.id) || !selected && usersSelected.contains(user.id)) prev.map(_._1).indexOf(Left(user)) else -1
          case (Right(i), selected) =>
            if (selected && !servsSelected.contains((i.provider, i.id)) || !selected && servsSelected.contains((i.provider, i.id))) prev.map(_._1).indexOf(Right(i)) else -1
        }
        changedPositions.filterNot(_ == -1).foreach(notifyItemChanged)
      } else
        notifyDataSetChanged()
  }

  override def getItemCount: Int = results.size

  override def getItemViewType(position: Int) = results(position) match {
    case (Left(_), _)  => USER_ITEM_TYPE
    case (Right(_), _) => INTEGRATION_ITEM_TYPE
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableRowViewHolder = {
    val view = ViewHelper.inflate[SingleUserRowView](R.layout.single_user_row, parent, addToParent = false)
    view.showCheckbox(true)
    view.setTheme(if (themeController.isDarkTheme) Theme.Dark else Theme.Light, background = false)
    view.setBackground(null)
    val viewHolder = SelectableRowViewHolder(view)

    view.onSelectionChanged.onUi { selected =>
      viewHolder.selectable.foreach {
        case Left(user) =>
          onSelectionChanged ! (Left(user.id), selected)
          if (selected)
            usersSelected.mutate(_ + user.id)
          else
            usersSelected.mutate(_ - user.id)

        case Right(i) =>
          val t = (i.provider, i.id)
          onSelectionChanged ! (Right((i.provider, i.id)), selected)
          if (selected)
            servicesSelected.mutate(_ ++ Set((i.provider, i.id)))
          else
            servicesSelected.mutate(_ -- Set((i.provider, i.id)))
      }
    }
    viewHolder
  }


  override def getItemId(position: Int) = results(position) match {
    case (Left(user), _)         => user.id.str.hashCode
    case (Right(integration), _) => integration.id.str.hashCode
  }

  override def onBindViewHolder(holder: SelectableRowViewHolder, position: Int): Unit = results(position) match {
    case (Left(user), selected) => holder.bind(user, team, selected = selected)
    case (Right(integration), selected) => holder.bind(integration, selected = selected)
  }
}

object AddParticipantsAdapter {

  val USER_ITEM_TYPE = 1
  val INTEGRATION_ITEM_TYPE = 2
}

case class SelectableRowViewHolder(v: SingleUserRowView) extends RecyclerView.ViewHolder(v) {

  var selectable: Option[Either[UserData, IntegrationData]] = None

  def bind(user: UserData, teamId: Option[TeamId], selected: Boolean) = {
    this.selectable = Some(Left(user))
    v.setUserData(user, teamId)
    v.setChecked(selected)
  }

  def bind(integration: IntegrationData, selected: Boolean) = {
    this.selectable = Some(Right(integration))
    v.setIntegration(integration)
    v.setChecked(selected)
  }

}

