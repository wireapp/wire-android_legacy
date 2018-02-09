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
import android.view.{ContextThemeWrapper, LayoutInflater, View, ViewGroup}
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.model.{UserData, UserId}
import com.waz.service.{SearchState, ZMessaging}
import com.waz.utils.events.{EventContext, Signal, SourceSignal}
import com.waz.zclient.common.views.{PickableElement, PickerSpannableEditText}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.usersearch.views.{SearchEditText, SearchResultUserRowView}
import com.waz.zclient.{FragmentHelper, R, ViewHelper}
import com.waz.zclient.utils.RichView

import scala.collection.immutable.Set

class NewConversationPickFragment extends Fragment with FragmentHelper {

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val newConvController = inject[NewConversationController]

  private lazy val searchFilter = Signal("")
  private lazy val adapter = NewConvAdapter(getContext, newConvController.users)

  private lazy val toolbar = view[Toolbar](R.id.toolbar)
  private lazy val nextButton = view[TypefaceTextView](R.id.confirmation_button)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    val searchResults = for {
      zms <- zms
      searchFilter <- searchFilter
      convId <- newConvController.convId
      searchState = SearchState(searchFilter, hasSelectedUsers = false, convId)
      results <- zms.userSearch.search(searchState, Set.empty[UserId])
    } yield results.localResults

    searchResults.onUi(_.foreach(adapter.setData))

    newConvController.convId.map(_.nonEmpty).onUi { hasConv =>
      toolbar.foreach(_.setVisibility(if (hasConv) View.VISIBLE else View.GONE))
    }

    newConvController.users.map(_.nonEmpty).onUi { hasUsers =>
      nextButton.foreach(_.setEnabled(hasUsers))
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.cloneInContext(new ContextThemeWrapper(getActivity, R.style.Theme_Light))
      .inflate(R.layout.create_conv_pick_fragment, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    val recyclerView = findById[RecyclerView](R.id.recycler_view)
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext))
    recyclerView.setAdapter(adapter)

    val searchBox = findById[SearchEditText](R.id.search_box)
    searchBox.setCallback(new PickerSpannableEditText.Callback{
      override def onRemovedTokenSpan(element: PickableElement): Unit = {}
      override def afterTextChanged(s: String): Unit = searchFilter ! s
    })
    searchBox.applyDarkTheme(false)

    toolbar.setVisibility(if (newConvController.convId.currentValue.flatten.nonEmpty) View.VISIBLE else View.GONE)
    nextButton.setEnabled(newConvController.users.currentValue.exists(_.nonEmpty))

    nextButton.foreach(_.onClick{
      newConvController.addUsersToConversation()
      getFragmentManager.popBackStack()
    })
  }
}

object NewConversationPickFragment {
  val Tag = ZLog.ImplicitTag.implicitLogTag
}


case class NewConvUserViewHolder(v: SearchResultUserRowView) extends RecyclerView.ViewHolder(v) {
  def bind(userData: UserData, selected: Boolean) = {
    v.setUser(userData)
    v.setChecked(selected)
  }
}

case class NewConvAdapter(context: Context, selectedUsers: SourceSignal[Set[UserId]])(implicit eventContext: EventContext) extends RecyclerView.Adapter[NewConvUserViewHolder]{
  private implicit val ctx = context

  private var data = Seq.empty[UserData]

  def setData(data: Seq[UserData]): Unit = {
    this.data = data
    notifyDataSetChanged()
  }

  override def getItemCount: Int = data.size

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): NewConvUserViewHolder = {
    val view = ViewHelper.inflate[SearchResultUserRowView](R.layout.startui_user, parent, addToParent = false)
    view.setIsAddingPeople(true)
    view.onSelectionChanged.onUi { selected =>
      view.getUser.foreach { user =>
        if (selected)
          selectedUsers.mutate(_ + user)
        else
          selectedUsers.mutate(_ - user)
      }
    }
    NewConvUserViewHolder(view)
  }

  override def onBindViewHolder(holder: NewConvUserViewHolder, position: Int): Unit = {
    val user = data(position)
    holder.bind(user, selectedUsers.currentValue.exists(_.contains(user.id)))
  }
}
