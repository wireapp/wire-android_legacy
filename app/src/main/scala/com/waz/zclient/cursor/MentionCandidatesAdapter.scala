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

import android.support.v7.widget.RecyclerView
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import com.waz.model.{Handle, UserId}
import com.waz.utils.events.{EventStream, SourceStream}
import com.waz.zclient.R
import com.waz.zclient.common.views.ChatheadView

class MentionCandidatesAdapter extends RecyclerView.Adapter[MentionCandidateViewHolder] {

  private var _data = Seq[MentionCandidateInfo]()

  setHasStableIds(true)

  val onUserClicked: SourceStream[MentionCandidateInfo] = EventStream()

  def setData(data: Seq[MentionCandidateInfo]): Unit = {
    _data = data
    notifyDataSetChanged()
  }

  private def getItem(pos: Int): MentionCandidateInfo = _data(pos)

  override def getItemCount: Int = _data.size

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): MentionCandidateViewHolder = {
    val view = LayoutInflater.from(parent.getContext).inflate(R.layout.mention_candidate_view, parent, false)
    new MentionCandidateViewHolder(view, { onUserClicked ! _ })
  }

  override def onBindViewHolder(holder: MentionCandidateViewHolder, position: Int): Unit = {
    holder.bind(getItem(position))
  }

  override def getItemId(position: Int): Long = getItem(position).userId.str.hashCode
}

class MentionCandidateViewHolder(v: View, onUserClick: MentionCandidateInfo => Unit) extends RecyclerView.ViewHolder(v) {
  private val nameTextView = v.findViewById[TextView](R.id.name)
  private val handleTextView = v.findViewById[TextView](R.id.handle)
  private val chathead = v.findViewById[ChatheadView](R.id.chathead)

  private var userId = Option.empty[MentionCandidateInfo]

  v.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = userId.foreach(onUserClick(_))
  })

  def bind(info: MentionCandidateInfo): Unit = {
    userId = Some(info)
    nameTextView.setText(info.name)
    handleTextView.setText(info.name)
    chathead.setUserId(info.userId)
  }
}

case class MentionCandidateInfo(userId: UserId, name: String, handle: Handle)
