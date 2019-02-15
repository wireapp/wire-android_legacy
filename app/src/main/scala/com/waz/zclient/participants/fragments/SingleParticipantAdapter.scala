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
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.ViewHolder
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ImageView, LinearLayout, TextView}
import com.waz.model.{Availability, UserData, UserField, UserId}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.paintcode.GuestIcon
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils._
import com.waz.zclient.views.ShowAvailabilityView
import com.waz.zclient.{Injectable, Injector, R}

class SingleParticipantAdapter(user: UserData,
                               isGuest: Boolean,
                               availability: Signal[Option[Availability]],
                               timerText: Signal[Option[String]],
                               readReceipts: Option[String],
                               isDarkTheme: Boolean)
                              (implicit context: Context, injector: Injector, eventContext: EventContext)
  extends RecyclerView.Adapter[ViewHolder] with Injectable {
  import SingleParticipantAdapter._


  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = viewType match {
    case Header =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.participant_header_row, parent, false)
      ParticipantHeaderRowViewHolder(view)
    case CustomField =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.participant_custom_field_row, parent,false)
      CustomFieldRowViewHolder(view)
    case Footer =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.participant_footer_row, parent, false)
      ParticipantFooterRowViewHolder(view)
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = (holder, position) match {
    case (h: ParticipantHeaderRowViewHolder, _) =>
      h.bind(user.id, isGuest, availability, timerText, isDarkTheme)
    case (h: ParticipantFooterRowViewHolder, _) =>
      h.bind(readReceipts)
    case (h: CustomFieldRowViewHolder, _) =>
      h.bind(user.fields(position - 1))
  }

  override def getItemCount: Int = user.fields.size + 2

  override def getItemId(position: Int): Long =
    if (position == 0) 0L
    else if (position == getItemCount - 1) 1L
    else user.fields(position - 1).key.hashCode.toLong

  setHasStableIds(true)

  override def getItemViewType(position: Int): Int =
    if (position == 0) Header
    else if (position == getItemCount - 1) Footer
    else CustomField
}

object SingleParticipantAdapter {
  val CustomField = 0
  val Header = 1
  val Footer = 2

  case class ParticipantHeaderRowViewHolder(view: View) extends ViewHolder(view) {
    private lazy val imageView           = view.findViewById[ChatheadView](R.id.chathead)
    private lazy val guestIndication     = view.findViewById[LinearLayout](R.id.guest_indicator)
    private lazy val userAvailability    = view.findViewById[ShowAvailabilityView](R.id.availability)
    private lazy val guestIndicatorTimer = view.findViewById[TypefaceTextView](R.id.expiration_time)
    private lazy val guestIndicatorIcon  = view.findViewById[ImageView](R.id.guest_indicator_icon)

    private var userId = Option.empty[UserId]

    def bind(userId: UserId, isGuest: Boolean, availability: Signal[Option[Availability]], timerText: Signal[Option[String]], isDarkTheme: Boolean)
            (implicit context: Context, ec: EventContext): Unit =
      if (!this.userId.contains(userId)){
        this.userId = Some(userId)

        imageView.setUserId(userId)
        guestIndication.setVisible(isGuest)

        val color = if (isDarkTheme) R.color.wire__text_color_primary_dark_selector else R.color.wire__text_color_primary_light_selector
        guestIndicatorIcon.setImageDrawable(GuestIcon(color))

        availability.onUi {
          case Some(av) =>
            userAvailability.setVisible(true)
            userAvailability.set(av)
          case None =>
            userAvailability.setVisible(false)
        }

        timerText.onUi {
          case Some(text) =>
            guestIndicatorTimer.setVisible(true)
            guestIndicatorTimer.setText(text)
          case None =>
            guestIndicatorTimer.setVisible(false)
        }
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

  case class ParticipantFooterRowViewHolder(view: View) extends ViewHolder(view) {
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