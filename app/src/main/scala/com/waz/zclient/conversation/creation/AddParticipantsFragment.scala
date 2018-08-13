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
import android.support.design.widget.TabLayout
import android.support.design.widget.TabLayout.OnTabSelectedListener
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.view._
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.tracking.{OpenSelectParticipants, TrackingService}
import com.waz.threading.Threading
import com.waz.utils.events._
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.common.views.{PickableElement, SingleUserRowView}
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.usersearch.views.{PickerSpannableEditText, SearchEditText}
import com.waz.zclient.utils.RichView

import scala.collection.immutable.Set
import scala.concurrent.Future

class AddParticipantsFragment extends FragmentHelper {

  import AddParticipantsFragment._
  import Threading.Implicits.Background
  implicit def cxt: Context = getContext

  private lazy val zms               = inject[Signal[ZMessaging]]
  private lazy val newConvController = inject[CreateConversationController]
  private lazy val keyboard          = inject[KeyboardController]
  private lazy val tracking          = inject[TrackingService]
  private lazy val themeController   = inject[ThemeController]

  private lazy val searchFilter = Signal("")

  private val peopleOrServices = Signal(false)

  private lazy val userResults = for {
    zms      <- zms
    filter   <- searchFilter
    convId   <- newConvController.convId
    teamOnly <- newConvController.teamOnly
    results  <- convId match {
      case Some(cId) => zms.userSearch.usersToAddToConversation(filter, cId)
      case None      => zms.userSearch.usersForNewConversation(filter, teamOnly)
    }
  } yield results

  private lazy val integrationsResults = for {
    zms          <- zms
    filter       <- searchFilter
    integrations <- zms.integrations.searchIntegrations(filter).map(Option(_)).orElse(Signal.const(None))
  } yield integrations.getOrElse(Seq.empty)

  private lazy val adapter = AddParticipantsAdapter(userResults, newConvController.users, integrationsResults, newConvController.integrations, peopleOrServices)

  private lazy val searchBox = returning(view[SearchEditText](R.id.search_box)) { vh =>
    new FutureEventStream[(Either[UserId, (ProviderId, IntegrationId)], Boolean), (Pickable, Boolean)](adapter.onSelectionChanged, {
      case (Left(userId), selected) =>
        zms.head.flatMap(_.usersStorage.get(userId).collect { case Some(u) => (Pickable(userId.str, u.name), selected) })
      case (Right((pId, iId)), selected) =>
        zms.head.flatMap(_.integrations.getIntegration(pId, iId).map { integration => (Pickable(iId.str, integration.name), selected) })
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

      peopleOrServices.map(if (_) 1 else 0).head.foreach(tabs.getTabAt(_).select())

      tabs.addOnTabSelectedListener(new OnTabSelectedListener {
        override def onTabSelected(tab: TabLayout.Tab): Unit =
          tab.getPosition match {
            case 0 => peopleOrServices ! false
            case 1 => peopleOrServices ! true
          }

        override def onTabUnselected(tab: TabLayout.Tab): Unit = {}
        override def onTabReselected(tab: TabLayout.Tab): Unit = {}
      })

      (for {
        isCreateConvFlow   <- newConvController.convId.map(_.isEmpty)
        isTeamAccount      <- inject[UserAccountsController].isTeam
        isTeamOnlyConv     <- newConvController.teamOnly
        isCurrentUserGuest <- inject[ParticipantsController].isCurrentUserGuest
        _ = verbose(s"should the tabs be visible: (is create conv flow: $isCreateConvFlow, is team account: $isTeamAccount, team only: $isTeamOnlyConv, is guest: $isCurrentUserGuest)")
      } yield !isCreateConvFlow && isTeamAccount && !isTeamOnlyConv && !isCurrentUserGuest)
        .onUi(tabs.setVisible)
    }
  }

  private val errorTextState = for {
    searchFilter <- searchFilter
    results      <- userResults
  } yield (results.isEmpty, searchFilter.isEmpty) match {
    case (true, true)  => (true, R.string.new_conv_no_contacts)
    case (true, false) => (true, R.string.new_conv_no_results)
    case _             => (false, R.string.empty_string)
  }

  private lazy val errorText = returning(view[TypefaceTextView](R.id.empty_search_message)) { vh =>
    errorTextState.onUi { case (visible, text) =>
      vh.foreach { errorText =>
        errorText.setVisible(visible)
        errorText.setText(text)
      }
    }
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
          searchFilter ! s
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
      selectedIntegrations   <- Future.sequence(selectedIntegrationIds.map { case (pId, iId) => zms.integrations.getIntegration(pId, iId)})
    } yield selectedUsers.map(Left(_)) ++ selectedIntegrations.toSeq.map(Right(_)))
      .map(_.foreach {
        case Left(user)         => searchBox.foreach(_.addElement(Pickable(user.id.str, user.name)))
        case Right(integration) => searchBox.foreach(_.addElement(Pickable(integration.id.str, integration.name)))
      })(Threading.Ui)

    //lazy init
    tabs
    errorText
  }

  private def close() = {
    keyboard.hideKeyboardIfVisible()
    getFragmentManager.popBackStack()
  }
}

object AddParticipantsFragment {

  val ShowKeyboardThreshold = 10
  val Tag = implicitLogTag

  private case class Pickable(id : String, name: String) extends PickableElement
}

case class AddParticipantsAdapter(userResults: Signal[IndexedSeq[UserData]],
                                  usersSelected: SourceSignal[Set[UserId]],
                                  integrationResults: Signal[Seq[IntegrationData]],
                                  integrationsSelected: SourceSignal[Set[(ProviderId, IntegrationId)]],
                                  peopleOrServices: Signal[Boolean]
                                 )
                                 (implicit context: Context, eventContext: EventContext, injector: Injector)
  extends RecyclerView.Adapter[SelectableRowViewHolder] with Injectable {
  import AddParticipantsAdapter._

  private implicit val ctx = context
  private lazy val themeController = inject[ThemeController]

  private var results = Seq.empty[(Either[UserData, IntegrationData], Boolean)]
  private var team = Option.empty[TeamId]

  val onSelectionChanged = EventStream[(Either[UserId, (ProviderId, IntegrationId)], Boolean)]()

  setHasStableIds(true)

  (for {
    tId                  <- inject[Signal[ZMessaging]].map(_.teamId) //TODO - we should use the conversation's teamId when available...
    pos                  <- peopleOrServices
    userResults          <- if (!pos) userResults else Signal.const(IndexedSeq.empty[UserData])
    usersSelected        <- usersSelected
    integrationResults   <- if (pos) integrationResults else Signal.const(Seq.empty[IntegrationData])
    integrationsSelected <- integrationsSelected
  } yield (tId, userResults, usersSelected, integrationResults, integrationsSelected)).onUi {
    case (tId, userResults, usersSelected, integrationResults, integrationsSelected) =>
      team = tId
      val prev = this.results
      this.results = userResults.map(u => (Left(u), usersSelected.contains(u.id))) ++ integrationResults.map(i => (Right(i), integrationsSelected.contains((i.provider, i.id))))
      if (prev.map(_._1).toSet == (userResults ++ integrationResults).toSet) {
        val changedPositions = prev.map {
          case (Left(user), selected) =>
            if (selected && !usersSelected.contains(user.id) || !selected && usersSelected.contains(user.id)) prev.map(_._1).indexOf(Left(user)) else -1
          case (Right(i), selected) =>
            if (selected && !integrationsSelected.contains((i.provider, i.id)) || !selected && integrationsSelected.contains((i.provider, i.id))) prev.map(_._1).indexOf(Right(i)) else -1
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
            integrationsSelected.mutate(_ ++ Set((i.provider, i.id)))
          else
            integrationsSelected.mutate(_ -- Set((i.provider, i.id)))
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

