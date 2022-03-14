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
import android.widget.{CompoundButton, ImageView, LinearLayout}
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConversationRole, UserId}
import com.wire.signals.{EventStream, SourceStream}
import com.waz.zclient.common.views.ChatHeadView
import com.waz.zclient.paintcode.GuestIcon
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils._
import com.waz.zclient.{Injectable, R}

class BaseSingleParticipantAdapter(userId:      UserId,
                                   isGuest:     Boolean,
                                   isExternal:  Boolean,
                                   isDarkTheme: Boolean,
                                   isGroup:     Boolean,
                                   isWireless:  Boolean,
                                   isFederated: Boolean
                                  )(implicit context: Context)
  extends RecyclerView.Adapter[ViewHolder] with Injectable with DerivedLogTag {
  import BaseSingleParticipantAdapter._

  protected var timerText:       Option[String] = None
  protected var participantRole: Option[ConversationRole] = None
  protected var selfRole:        Option[ConversationRole] = None

  protected def isGroupAdminViewVisible: Boolean = isGroup && !isWireless && selfRole.exists(_.canModifyOtherMember)
  protected def hasInformation: Boolean          = false

  val onParticipantRoleChange = EventStream[ConversationRole]

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = viewType match {
    case Header =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.participant_header_row, parent, false)
      ParticipantHeaderRowViewHolder(view)
    case GroupAdmin =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.group_admin_row, parent, false)
      GroupAdminViewHolder(view)
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = holder match {
    case h: ParticipantHeaderRowViewHolder =>
      h.bind(
        userId, isGuest, isExternal,
        isGroup && participantRole.contains(ConversationRole.AdminRole),
        timerText, isDarkTheme, hasInformation, isFederated
      )
    case h: GroupAdminViewHolder =>
      h.bind(onParticipantRoleChange, participantRole.contains(ConversationRole.AdminRole), isFederated = isFederated)
  }

  override def getItemCount: Int = if (isGroupAdminViewVisible) 2 else 1

  override def getItemId(position: Int): Long = getItemViewType(position) match {
    case Header     => 0L
    case GroupAdmin => 1L
  }

  setHasStableIds(true)

  override def getItemViewType(position: Int): Int =
    if (position == 1 && isGroupAdminViewVisible) GroupAdmin else Header
}

object BaseSingleParticipantAdapter {
  val CustomField  = 0
  val Header       = 1
  val GroupAdmin   = 2
  val ReadReceipts = 3
  val UserName     = 4
  val LinkedInfo   = 5

  final case class ParticipantHeaderRowViewHolder(view: View) extends ViewHolder(view) {
    private lazy val imageView            = view.findViewById[ChatHeadView](R.id.chathead)
    private lazy val guestIndication      = view.findViewById[LinearLayout](R.id.guest_indicator)
    private lazy val federatedIndication  = view.findViewById[LinearLayout](R.id.federated_indicator)
    private lazy val guestIndicatorIcon   = view.findViewById[ImageView](R.id.guest_indicator_icon)
    private lazy val externalIndication   = view.findViewById[LinearLayout](R.id.external_indicator)
    private lazy val groupAdminIndication = view.findViewById[LinearLayout](R.id.group_admin_indicator)
    private lazy val guestIndicatorTimer  = view.findViewById[TypefaceTextView](R.id.expiration_time)
    private lazy val informationText      = view.findViewById[TypefaceTextView](R.id.information)

    private var userId = Option.empty[UserId]

    def bind(userId:         UserId,
             isGuest:        Boolean,
             isExternal:     Boolean,
             isGroupAdmin:   Boolean,
             timerText:      Option[String],
             isDarkTheme:    Boolean,
             hasInformation: Boolean,
             isFederated:    Boolean
            )(implicit context: Context): Unit = {
      this.userId = Some(userId)

      imageView.loadUser(userId)
      guestIndication.setVisible(isGuest)
      externalIndication.setVisible(isExternal)
      groupAdminIndication.setVisible(isGroupAdmin)

      federatedIndication.setVisible(isFederated)

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

  final case class GroupAdminViewHolder(view: View) extends ViewHolder(view) with DerivedLogTag {
    private implicit val ctx: Context = view.getContext

    private val switch                   = view.findViewById[SwitchCompat](R.id.participant_group_admin_toggle)
    private var groupAdmin               = Option.empty[Boolean]
    private var onParticipantRoleChanged = Option.empty[SourceStream[ConversationRole]]

    switch.setOnCheckedChangeListener(new OnCheckedChangeListener {
      override def onCheckedChanged(buttonView: CompoundButton, groupAdminEnabled: Boolean): Unit =
        if (!groupAdmin.contains(groupAdminEnabled)) {
          groupAdmin = Some(groupAdminEnabled)
          onParticipantRoleChanged.foreach(_ ! (if (groupAdminEnabled) ConversationRole.AdminRole else ConversationRole.MemberRole))
        }
    })

    def bind(onParticipantRoleChanged: SourceStream[ConversationRole],
             groupAdminEnabled:        Boolean,
             isFederated:              Boolean
            ): Unit = {
      if (!this.onParticipantRoleChanged.contains(onParticipantRoleChanged))
        this.onParticipantRoleChanged = Some(onParticipantRoleChanged)
      if (!groupAdmin.contains(groupAdminEnabled)) switch.setChecked(groupAdminEnabled)
      switch.setEnabled(!isFederated)
      view.setContentDescription(s"Group Admin: $groupAdminEnabled")
    }
  }
}
