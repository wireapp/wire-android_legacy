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
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.view._
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import com.waz.ZLog.ImplicitTag._
import com.waz.model.{TeamId, UserData, UserId}
import com.waz.service.ZMessaging
import com.waz.service.tracking.{OpenSelectParticipants, TrackingService}
import com.waz.threading.Threading
import com.waz.utils.events._
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.common.views.SingleUserRowView.Theme
import com.waz.zclient.common.views.{PickableElement, SingleUserRowView}
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

  private lazy val searchResults = for {
    zms      <- zms
    filter   <- searchFilter
    convId   <- newConvController.convId
    teamOnly <- newConvController.teamOnly
    results  <- convId match {
      case Some(cId) => zms.userSearch.usersToAddToConversation(filter, cId)
      case None      => zms.userSearch.usersForNewConversation(filter, teamOnly)
    }
  } yield results

  private lazy val adapter = AddParticipantsAdapter(searchResults, newConvController.users)

  private lazy val searchBox = returning(view[SearchEditText](R.id.search_box)) { vh =>
    new FutureEventStream[(UserId, Boolean), (PickableUser, Boolean)](adapter.onUserSelectionChanged, {
      case (userId, selected) =>
        zms.head.flatMap(_.users.getUser(userId).collect { case Some(u) => (PickableUser(userId, u.name), selected) })
    }).onUi {
      case (pu, selected) =>
        vh.foreach { v =>
          if (selected) v.addElement(pu) else v.removeElement(pu)
        }
    }
  }

  private val errorTextState = for {
    searchFilter <- searchFilter
    results      <- searchResults
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
        override def onRemovedTokenSpan(element: PickableElement): Unit =
          newConvController.users.mutate(_ - UserId(element.id))
        override def afterTextChanged(s: String): Unit =
          searchFilter ! s
      })
      v.setOnEditorActionListener(new OnEditorActionListener {
        override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean =
          if (actionId == EditorInfo.IME_ACTION_SEARCH) keyboard.hideKeyboardIfVisible() else false
      })
    }

    (for {
      zms <- zms.head
      selectedIds <- newConvController.users.head
      selected <- Future.traverse(selectedIds)(zms.users.getUser)
    } yield selected.flatten).map(_.foreach { user => searchBox.foreach(_.addElement(PickableUser(user.id, user.name))) })(Threading.Ui)

    //lazy init
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

  private case class PickableUser(userId : UserId, userName: String) extends PickableElement {
    def id: String = userId.str
    def name: String = userName
  }
}

case class AddParticipantsAdapter(searchResults: Signal[IndexedSeq[UserData]], selectedUsers: SourceSignal[Set[UserId]])
                                 (implicit context: Context, eventContext: EventContext, injector: Injector)
  extends RecyclerView.Adapter[SelectableUserRowViewHolder] with Injectable {

  private implicit val ctx = context
  private lazy val themeController = inject[ThemeController]

  private var users = Seq.empty[(UserData, Boolean)]
  private var team = Option.empty[TeamId]

  val onUserSelectionChanged = EventStream[(UserId, Boolean)]()

  setHasStableIds(true)

  (for {
    tId <- inject[Signal[ZMessaging]].map(_.teamId) //TODO - we should use the conversation's teamId when available...
    res <- searchResults
    sel <- selectedUsers
  } yield (tId, res, sel))
    .onUi {
      case (tId, res, sel) =>
        team = tId
        val prev = this.users
        this.users = res.map(u => (u, sel.contains(u.id)))
        if (prev.map(_._1) == res) {
          val changedPositions = prev.map {
            case (user, selected) =>
              if (selected && !sel.contains(user.id) || !selected && sel.contains(user.id)) prev.map(_._1).indexOf(user) else -1
          }
          changedPositions.filterNot(_ == -1).foreach(notifyItemChanged)
        } else
          notifyDataSetChanged()
    }

  override def getItemCount: Int = users.size

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableUserRowViewHolder = {
    val view = ViewHelper.inflate[SingleUserRowView](R.layout.single_user_row, parent, addToParent = false)
    view.showCheckbox(true)
    view.setTheme(if (themeController.isDarkTheme) Theme.Dark else Theme.Light)
    view.setBackground(null)
    val viewHolder = SelectableUserRowViewHolder(view)

    view.onSelectionChanged.onUi { selected =>
      viewHolder.userData.map(_.id).foreach { user =>
        onUserSelectionChanged ! (user, selected)
        if (selected)
          selectedUsers.mutate(_ + user)
        else
          selectedUsers.mutate(_ - user)
      }
    }
    viewHolder
  }


  override def getItemId(position: Int) = users(position)._1.id.str.hashCode

  override def onBindViewHolder(holder: SelectableUserRowViewHolder, position: Int): Unit = {
    val (user, selected) = users(position)
    holder.bind(user, team, selected = selected)
  }
}

case class SelectableUserRowViewHolder(v: SingleUserRowView) extends RecyclerView.ViewHolder(v) {

  var userData: Option[UserData] = None

  def bind(userData: UserData, teamId: Option[TeamId], selected: Boolean) = {
    this.userData = Some(userData)
    v.setUserData(userData, teamId)
    v.setChecked(selected)
  }
}

