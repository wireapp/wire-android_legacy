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
package com.waz.zclient.cursor

import androidx.recyclerview.widget.RecyclerView
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.model.{TeamId, UserData}
import com.waz.utils.events.{EventStream, SourceStream}
import com.waz.zclient.R
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.views.SingleUserRowView

class MentionCandidatesAdapter extends RecyclerView.Adapter[MentionCandidateViewHolder] {

  private var _data = Seq[UserData]()
  private var _teamId = Option.empty[TeamId]
  private var _theme: Theme = Theme.Light

  val onUserClicked: SourceStream[UserData] = EventStream()

  def setData(data: Seq[UserData], teamId: Option[TeamId], theme: Theme): Unit = {
    _data = data
    _teamId = teamId
    _theme = theme

    notifyDataSetChanged()
  }

  private def getItem(pos: Int): UserData = _data(pos)

  override def getItemCount: Int = _data.size

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): MentionCandidateViewHolder = {
    val view = LayoutInflater.from(parent.getContext).inflate(R.layout.single_user_row, parent, false).asInstanceOf[SingleUserRowView]
    view.showArrow(false)
    view.setTheme(_theme, background = false)
    view.setSeparatorVisible(false)
    new MentionCandidateViewHolder(view, { onUserClicked ! _ })
  }

  override def onBindViewHolder(holder: MentionCandidateViewHolder, position: Int): Unit = {
    holder.bind(getItem(position), _teamId)
  }

  override def getItemId(position: Int): Long = getItem(position).id.str.hashCode
}

class MentionCandidateViewHolder(v: View, onUserClick: UserData => Unit) extends RecyclerView.ViewHolder(v) {
  private var userData = Option.empty[UserData]

  v.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = userData.foreach(onUserClick(_))
  })

  def bind(userData: UserData, teamId: Option[TeamId]): Unit = {
    this.userData = Some(userData)
    v.asInstanceOf[SingleUserRowView].setUserData(userData, teamId)
  }
}
