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
package com.waz.zclient.conversation

import android.os.Bundle
import android.support.v7.widget.{LinearLayoutManager, RecyclerView, Toolbar}
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.model.{Liking, MessageId}
import com.waz.service.ZMessaging
import com.waz.threading.CancellableFuture
import com.waz.utils.events.{RefreshingSignal, Signal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ScreenController
import com.waz.zclient.pages.main.conversation.ConversationManagerFragment
import com.waz.zclient.{FragmentHelper, R}
import com.waz.content.Likes

class LikesListFragment extends FragmentHelper {

  private lazy val screenController = inject[ScreenController]

  private lazy val toolbar        = view[Toolbar](R.id.t__likes_list__toolbar)
  private lazy val likersListView = view[RecyclerView](R.id.rv__likes_list)
  private lazy val likesAdapter   = returning(new LikesAdapter(getContext)) { adapter =>
    def getLikes(zms: ZMessaging, messageId: MessageId) =
      new RefreshingSignal[Likes, Seq[Liking]](
        CancellableFuture.lift(zms.reactionsStorage.getLikes(messageId)),
        zms.reactionsStorage.onChanged.map(_.filter(_.message == messageId))
      )

    (for {
      z               <- inject[Signal[ZMessaging]]
      Some(messageId) <- screenController.showLikesForMessage
      likes           <- getLikes(z, messageId)
    } yield likes.likers.keySet).onUi(adapter.setLikes)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_likes_list, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    likersListView.foreach { view =>
      view.setLayoutManager(new LinearLayoutManager(getActivity))
      view.setAdapter(likesAdapter)
    }

    toolbar
  }

  override def onResume(): Unit = {
    super.onResume()

    toolbar.foreach(_.setNavigationOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = onBackPressed()
    }))
  }

  override def onPause(): Unit = {
    toolbar.foreach(_.setNavigationOnClickListener(null))

    super.onPause()
  }

  override def onBackPressed(): Boolean = Option(getParentFragment) match {
    case Some(f: ConversationManagerFragment) =>
      screenController.showLikesForMessage ! None
      true
    case _ => false
  }
}

object LikesListFragment {
  val Tag: String = classOf[LikesListFragment].getName
}
