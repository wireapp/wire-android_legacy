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
import android.support.v4.app.Fragment
import android.support.v7.widget.{LinearLayoutManager, RecyclerView, Toolbar}
import android.view.View.OnClickListener
import android.view._
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import com.waz.ZLog.ImplicitTag._
import com.waz.model.{UserData, UserId}
import com.waz.service.ZMessaging
import com.waz.service.tracking.{OpenSelectParticipants, TrackingService}
import com.waz.threading.Threading
import com.waz.utils.events._
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.views.PickableElement
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController.Destination
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.usersearch.views.{PickerSpannableEditText, SearchEditText, SearchResultUserRowView}
import com.waz.zclient.utils.ContextUtils.getColor
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R, ViewHelper}

import scala.collection.immutable.Set
import scala.concurrent.Future

class NewConversationPickFragment extends Fragment with FragmentHelper with OnBackPressedListener {

  import NewConversationPickFragment._
  import Threading.Implicits.Background
  implicit def cxt: Context = getContext

  private lazy val zms               = inject[Signal[ZMessaging]]
  private lazy val newConvController = inject[NewConversationController]
  private lazy val keyboard          = inject[KeyboardController]
  private lazy val tracking          = inject[TrackingService]

  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.getColor)

  private lazy val searchFilter = Signal("")

  private lazy val searchResults = for {
    zms     <- zms
    filter  <- searchFilter
    convId  <- newConvController.convId
    results <- zms.userSearch.searchLocal(filter, toConv = convId)
  } yield results

  private lazy val adapter = NewConvAdapter(searchResults, newConvController.users)

  private lazy val toolbarText = returning(view[TypefaceTextView](R.id.header)) { vh =>
    newConvController.users.map(_.size).map {
      case 0 => getString(R.string.add_people_empty_header)
      case x => getString(R.string.add_people_count_header, x.toString)
    }.onUi(t => vh.foreach(_.setText(t)))
  }

  private lazy val toolbar = returning(view[Toolbar](R.id.toolbar)) { vh =>
    newConvController.convId.map(_.nonEmpty).onUi { hasConv =>
      vh.foreach(_.setVisibility(if (hasConv) View.VISIBLE else View.GONE))
    }
  }

  lazy val confButtonEnabled = newConvController.users.map(_.nonEmpty)

  lazy val confButtonColor = confButtonEnabled.flatMap {
    case false => Signal.const(getColor(R.color.teams_inactive_button))
    case _     => accentColor
  }

  private lazy val confButton = returning(view[TypefaceTextView](R.id.confirmation_button)) { vh =>
    confButtonEnabled.onUi(e => vh.foreach(_.setEnabled(e)))
    confButtonColor.onUi(c => vh.foreach(_.setTextColor(c)))
  }

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
    inflater.cloneInContext(new ContextThemeWrapper(getActivity, R.style.Theme_Light))
      .inflate(R.layout.create_conv_pick_fragment, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    val recyclerView = findById[RecyclerView](R.id.recycler_view)
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext))
    recyclerView.setAdapter(adapter)

    newConvController.fromScreen.head.map { f =>
      tracking.track(OpenSelectParticipants(f))
    }

    confButton.foreach { v =>
      v.onClick {
        newConvController.addUsersToConversation()
        close()
      }
      v.setEnabled(newConvController.users.currentValue.exists(_.nonEmpty))
    }

    searchBox.foreach { v =>
      v.applyDarkTheme(false)
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
      selected <- Future.sequence(selectedIds.map(zms.users.getUser))
    } yield selected.flatten).map(_.foreach { user => searchBox.addElement(PickableUser(user.id, user.name)) })(Threading.Ui)

    toolbar.foreach(_.setNavigationOnClickListener(new OnClickListener() {
      override def onClick(v: View): Unit = close()
    }))

    //lazy init
    errorText
    toolbarText
  }

  private def close() = {
    keyboard.hideKeyboardIfVisible()
    inject[IPickUserController].hidePickUser(Destination.PARTICIPANTS)
  }

  override def onBackPressed() =
    keyboard.hideKeyboardIfVisible() ||  { close(); true }
}

object NewConversationPickFragment {

  val ShowKeyboardThreshold = 10
  val Tag = implicitLogTag

  private case class PickableUser(userId : UserId, userName: String) extends PickableElement {
    def id: String = userId.str
    def name: String = userName
  }
}


case class NewConvUserViewHolder(v: SearchResultUserRowView) extends RecyclerView.ViewHolder(v) {
  def bind(userData: UserData, selected: Boolean) = {
    v.setUser(userData)
    v.setChecked(selected)
  }
}

case class NewConvAdapter(searchResults: Signal[IndexedSeq[UserData]], selectedUsers: SourceSignal[Set[UserId]])(implicit context: Context, eventContext: EventContext) extends RecyclerView.Adapter[NewConvUserViewHolder] {
  private implicit val ctx = context

  private var users = Seq.empty[(UserData, /*isSelected: */ Boolean)]

  val onUserSelectionChanged = EventStream[(UserId, Boolean)]()

  setHasStableIds(true)

  (for {
    res <- searchResults
    sel <- selectedUsers
  } yield (res, sel))
    .onUi {
      case (res, sel) =>
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

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): NewConvUserViewHolder = {
    val view = ViewHelper.inflate[SearchResultUserRowView](R.layout.startui_user, parent, addToParent = false)
    view.setIsAddingPeople(true)
    view.onSelectionChanged.onUi { selected =>
      view.getUser.foreach { user =>
        onUserSelectionChanged ! (user, selected)
        if (selected)
          selectedUsers.mutate(_ + user)
        else
          selectedUsers.mutate(_ - user)
      }
    }
    NewConvUserViewHolder(view)
  }


  override def getItemId(position: Int) = users(position)._1.id.str.hashCode

  override def onBindViewHolder(holder: NewConvUserViewHolder, position: Int): Unit = {
    val (user, selected) = users(position)
    holder.bind(user, selected)
  }
}
