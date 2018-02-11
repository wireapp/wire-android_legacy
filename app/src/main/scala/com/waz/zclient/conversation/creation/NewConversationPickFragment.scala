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
import com.waz.ZLog.ImplicitTag._
import com.waz.model.{UserData, UserId}
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, Signal, SourceSignal}
import com.waz.utils.returning
import com.waz.zclient.common.views.PickableElement
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.usersearch.views.{PickerSpannableEditText, SearchEditText, SearchResultUserRowView}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, R, ViewHelper}

import scala.collection.immutable.Set

class NewConversationPickFragment extends Fragment with FragmentHelper {

  import NewConversationPickFragment._
  import com.waz.threading.Threading.Implicits.Background
  implicit def cxt: Context = getContext

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val newConvController = inject[NewConversationController]

  private lazy val searchFilter = Signal("")

  private lazy val searchResults = for {
    zms     <- zms
    filter  <- searchFilter
    convId  <- newConvController.convId
    results <- zms.userSearch.searchLocal(filter, toConv = convId)
  } yield results

  private lazy val adapter = returning(NewConvAdapter(newConvController.users)) { a =>
    searchResults.onUi(a.setData)
  }

  private lazy val toolbar = returning(view[Toolbar](R.id.toolbar)) { vh =>
    newConvController.convId.map(_.nonEmpty).onUi { hasConv =>
      vh.foreach(_.setVisibility(if (hasConv) View.VISIBLE else View.GONE))
    }
  }

  private lazy val nextButton = returning(view[TypefaceTextView](R.id.next_button)) { vh =>
    newConvController.users.map(_.nonEmpty).onUi { hasUsers =>
      vh.foreach(_.setEnabled(hasUsers))
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
  val Tag = implicitLogTag
}


case class NewConvUserViewHolder(v: SearchResultUserRowView) extends RecyclerView.ViewHolder(v) {
  def bind(userData: UserData, selected: Boolean) = {
    v.setUser(userData)
    v.setChecked(selected)
  }
}

case class NewConvAdapter(selectedUsers: SourceSignal[Set[UserId]])(implicit context: Context, eventContext: EventContext) extends RecyclerView.Adapter[NewConvUserViewHolder] {
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
