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
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.{CompoundButton, ImageView, LinearLayout, TextView}
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConversationRole, UserField, UserId}
import com.waz.utils.events.{EventStream, SourceStream}
import com.waz.zclient.common.views.ChatHeadView
import com.waz.zclient.paintcode.GuestIcon
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils._
import com.waz.zclient.{Injectable, R}

class SingleParticipantAdapter(userId: UserId,
                               isGuest: Boolean,
                               isExternal: Boolean,
                               isDarkTheme: Boolean,
                               isGroup: Boolean,
                               isWireless: Boolean,
                               private var fields:          Seq[UserField] = Seq.empty,
                               private var timerText:       Option[String] = None,
                               private var readReceipts:    Option[String] = None,
                               private var participantRole: ConversationRole = ConversationRole.MemberRole,
                               private var selfRole:        ConversationRole = ConversationRole.MemberRole
                              )(implicit context: Context)
  extends RecyclerView.Adapter[ViewHolder] with Injectable with DerivedLogTag {
  import SingleParticipantAdapter._

  def set(fields:          Seq[UserField],
          timerText:       Option[String],
          readReceipts:    Option[String],
          participantRole: ConversationRole,
          selfRole:        ConversationRole
         ): Unit = {
    this.fields          = fields
    this.timerText       = timerText
    this.readReceipts    = readReceipts
    this.participantRole = participantRole
    this.selfRole        = selfRole
    notifyDataSetChanged()
  }

  private def isGroupAdminViewVisible: Boolean = isGroup && !isWireless && selfRole.canModifyOtherMember

  val onParticipantRoleChange = EventStream[ConversationRole]

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = viewType match {
    case Header =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.participant_header_row, parent, false)
      ParticipantHeaderRowViewHolder(view)
    case GroupAdmin =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.group_admin_row, parent, false)
      GroupAdminViewHolder(view)
    case CustomField =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.participant_custom_field_row, parent,false)
      CustomFieldRowViewHolder(view)
    case ReadReceipts =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.participant_footer_row, parent, false)
      ReadReceiptsRowViewHolder(view)
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = holder match {
    case h: ParticipantHeaderRowViewHolder =>
      h.bind(userId, isGuest, isExternal, isGroup && participantRole == ConversationRole.AdminRole, timerText, isDarkTheme, fields.nonEmpty)
    case h: GroupAdminViewHolder =>
      h.bind(onParticipantRoleChange, participantRole == ConversationRole.AdminRole)
    case h: ReadReceiptsRowViewHolder =>
      h.bind(readReceipts)
    case h: CustomFieldRowViewHolder =>
      h.bind(fields(position - (if(isGroupAdminViewVisible) 2 else 1)))
  }

  override def getItemCount: Int =
    if (isGroupAdminViewVisible) fields.size + 3 else fields.size + 2

  override def getItemId(position: Int): Long = getItemViewType(position) match {
    case Header                        => 0L
    case GroupAdmin                    => 1L
    case ReadReceipts                  => 2L
    case _  if isGroupAdminViewVisible => fields(position - 2).key.hashCode.toLong
    case _                             => fields(position - 1).key.hashCode.toLong
  }

  setHasStableIds(true)

  override def getItemViewType(position: Int): Int =
    if (position == 0) Header
    else if (position == 1 && isGroupAdminViewVisible) GroupAdmin
    else if (position == getItemCount - 1) ReadReceipts
    else CustomField
}

object SingleParticipantAdapter {
  val CustomField = 0
  val Header = 1
  val GroupAdmin = 2
  val ReadReceipts = 3

  case class ParticipantHeaderRowViewHolder(view: View) extends ViewHolder(view) {
    private lazy val imageView            = view.findViewById[ChatHeadView](R.id.chathead)
    private lazy val guestIndication      = view.findViewById[LinearLayout](R.id.guest_indicator)
    private lazy val guestIndicatorIcon  = view.findViewById[ImageView](R.id.guest_indicator_icon)
    private lazy val externalIndication   = view.findViewById[LinearLayout](R.id.external_indicator)
    private lazy val groupAdminIndication = view.findViewById[LinearLayout](R.id.group_admin_indicator)
    private lazy val guestIndicatorTimer  = view.findViewById[TypefaceTextView](R.id.expiration_time)
    private lazy val informationText      = view.findViewById[TypefaceTextView](R.id.information)

    private var userId = Option.empty[UserId]

    def bind(userId: UserId,
             isGuest: Boolean,
             isExternal: Boolean,
             isGroupAdmin: Boolean,
             timerText: Option[String],
             isDarkTheme: Boolean,
             hasInformation: Boolean
            )(implicit context: Context): Unit = {
      this.userId = Some(userId)

      imageView.loadUser(userId)
      guestIndication.setVisible(isGuest)
      externalIndication.setVisible(isExternal)
      groupAdminIndication.setVisible(isGroupAdmin)

      val color = if (isDarkTheme) R.color.wire__text_color_primary_dark_selector else R.color.wire__text_color_primary_light_selector
      guestIndicatorIcon.setImageDrawable(GuestIcon(color))

      timerText match {
        case Some(text) =>
          guestIndicatorTimer.setVisible(true)
          guestIndicatorTimer.setText(text)
        case None =>
          guestIndicatorTimer.setVisible(false)
      }

      informationText.setVisible(hasInformation)
    }
  }

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

  case class GroupAdminViewHolder(view: View) extends ViewHolder(view) with DerivedLogTag {
    private implicit val ctx = view.getContext

    private val switch = view.findViewById[SwitchCompat](R.id.participant_group_admin_toggle)
    private var groupAdmin = Option.empty[Boolean]
    private var onParticipantRoleChanged = Option.empty[SourceStream[ConversationRole]]

    view.setId(R.id.participant_group_admin_toggle)

    switch.setOnCheckedChangeListener(new OnCheckedChangeListener {
      override def onCheckedChanged(buttonView: CompoundButton, groupAdminEnabled: Boolean): Unit =
        if (!groupAdmin.contains(groupAdminEnabled)) {
          groupAdmin = Some(groupAdminEnabled)
          onParticipantRoleChanged.foreach(_ ! (if (groupAdminEnabled) ConversationRole.AdminRole else ConversationRole.MemberRole))
        }
    })

    def bind(onParticipantRoleChanged: SourceStream[ConversationRole], groupAdminEnabled: Boolean): Unit = {
      if (!this.onParticipantRoleChanged.contains(onParticipantRoleChanged))
        this.onParticipantRoleChanged = Some(onParticipantRoleChanged)
      if (!groupAdmin.contains(groupAdminEnabled)) switch.setChecked(groupAdminEnabled)
    }
  }
}
