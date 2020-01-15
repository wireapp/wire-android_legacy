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
package com.waz.zclient.participants.fragments

import android.content.Context
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.waz.model.{ConversationRole, UserField, UserId}
import com.waz.zclient.R
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils._

final class SingleParticipantAdapter(userId: UserId,
                               isGuest: Boolean,
                               isExternal: Boolean,
                               isDarkTheme: Boolean,
                               isGroup: Boolean,
                               isWireless: Boolean
                              )(implicit context: Context)
  extends BaseSingleParticipantAdapter(userId, isGuest, isExternal, isDarkTheme, isGroup, isWireless) {
  import BaseSingleParticipantAdapter._

  private var fields:       Seq[UserField] = Seq.empty
  private var readReceipts: Option[String] = None

  override protected def hasInformation: Boolean = fields.nonEmpty

  def set(fields:          Seq[UserField],
          timerText:       Option[String],
          readReceipts:    Option[String],
          participantRole: ConversationRole,
          selfRole:        ConversationRole
         ): Unit = {
    this.fields          = fields
    this.timerText       = timerText
    this.readReceipts    = readReceipts
    this.participantRole = Some(participantRole)
    this.selfRole        = Some(selfRole)
    notifyDataSetChanged()
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = viewType match {
    case SingleParticipantAdapter.CustomField =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.participant_custom_field_row, parent,false)
      SingleParticipantAdapter.CustomFieldRowViewHolder(view)
    case SingleParticipantAdapter.ReadReceipts =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.participant_footer_row, parent, false)
      SingleParticipantAdapter.ReadReceiptsRowViewHolder(view)
    case _ =>
      super.onCreateViewHolder(parent, viewType)
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = holder match {
    case h: SingleParticipantAdapter.ReadReceiptsRowViewHolder =>
      h.bind(readReceipts)
    case h: SingleParticipantAdapter.CustomFieldRowViewHolder =>
      h.bind(fields(position - (if(isGroupAdminViewVisible) 2 else 1)))
    case _ =>
      super.onBindViewHolder(holder, position)
  }

  override def getItemCount: Int =
    if (isGroupAdminViewVisible) fields.size + 3 else fields.size + 2

  override def getItemId(position: Int): Long = getItemViewType(position) match {
    case Header                        => 0L
    case GroupAdmin                    => 1L
    case SingleParticipantAdapter.ReadReceipts => 2L
    case _  if isGroupAdminViewVisible => fields(position - 2).key.hashCode.toLong
    case _                             => fields(position - 1).key.hashCode.toLong
  }

  override def getItemViewType(position: Int): Int =
    if (position == 0) Header
    else if (position == 1 && isGroupAdminViewVisible) GroupAdmin
    else if (position == getItemCount - 1) SingleParticipantAdapter.ReadReceipts
    else SingleParticipantAdapter.CustomField
}


object SingleParticipantAdapter {
  val CustomField = 0
  val ReadReceipts = 3

  case class CustomFieldRowViewHolder(view: View) extends ViewHolder(view) {
    private lazy val name  = view.findViewById[TextView](R.id.custom_field_name)
    private lazy val value = view.findViewById[TextView](R.id.custom_field_value)

    def bind(field: UserField): Unit = {
      name.setText(field.key)
      value.setText(field.value)
    }
  }

  case class ReadReceiptsRowViewHolder(view: View) extends ViewHolder(view) {
    private lazy val readReceiptsInfoTitle = view.findViewById[TypefaceTextView](R.id.read_receipts_info_title)
    private lazy val readReceiptsInfo1     = view.findViewById[TypefaceTextView](R.id.read_receipts_info_1)
    private lazy val readReceiptsInfo2     = view.findViewById[TypefaceTextView](R.id.read_receipts_info_2)

    def bind(title: Option[String]): Unit = {
      readReceiptsInfoTitle.setVisible(title.isDefined)
      readReceiptsInfo1.setVisible(title.isDefined)
      readReceiptsInfo2.setVisible(title.isDefined)
      title.foreach(readReceiptsInfoTitle.setText)
    }
  }
}
