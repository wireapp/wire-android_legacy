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
import com.waz.model.{Availability, UserField, UserId}
import com.waz.zclient.common.views.ChatHeadView
import com.waz.zclient.paintcode.GuestIcon
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils._
import com.waz.zclient.views.ShowAvailabilityView
import com.waz.zclient.{Injectable, R}

class SingleParticipantAdapter(userId: UserId,
                               isGuest: Boolean,
                               isDarkTheme: Boolean,
                               private var fields: Seq[UserField] = Seq.empty,
                               private var availability: Option[Availability] = None,
                               private var timerText: Option[String] = None,
                               private var readReceipts: Option[String] = None
                              )(implicit context: Context)
  extends RecyclerView.Adapter[ViewHolder] with Injectable {
  import SingleParticipantAdapter._

  def set(fields: Seq[UserField], availability: Option[Availability], timerText: Option[String], readReceipts: Option[String]): Unit = {
    this.fields = fields
    this.availability = availability
    this.timerText = timerText
    this.readReceipts = readReceipts
    notifyDataSetChanged()
  }

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

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = holder match {
    case h: ParticipantHeaderRowViewHolder =>
      h.bind(userId, isGuest, availability, timerText, isDarkTheme, fields.nonEmpty)
    case h: ParticipantFooterRowViewHolder =>
      h.bind(readReceipts)
    case h: CustomFieldRowViewHolder =>
      h.bind(fields(position - 1))
  }

  override def getItemCount: Int = fields.size + 2

  override def getItemId(position: Int): Long =
    if (position == 0) 0L
    else if (position == getItemCount - 1) 1L
    else fields(position - 1).key.hashCode.toLong

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
    private lazy val imageView           = view.findViewById[ChatHeadView](R.id.chathead)
    private lazy val guestIndication     = view.findViewById[LinearLayout](R.id.guest_indicator)
    private lazy val userAvailability    = view.findViewById[ShowAvailabilityView](R.id.availability)
    private lazy val guestIndicatorTimer = view.findViewById[TypefaceTextView](R.id.expiration_time)
    private lazy val guestIndicatorIcon  = view.findViewById[ImageView](R.id.guest_indicator_icon)
    private lazy val informationText     = view.findViewById[TypefaceTextView](R.id.information)

    private var userId = Option.empty[UserId]

    def bind(userId: UserId,
             isGuest: Boolean,
             availability: Option[Availability],
             timerText: Option[String],
             isDarkTheme: Boolean,
             hasInformation: Boolean
            )(implicit context: Context): Unit = {
      this.userId = Some(userId)

      imageView.loadUser(userId)
      guestIndication.setVisible(isGuest)

      val color = if (isDarkTheme) R.color.wire__text_color_primary_dark_selector else R.color.wire__text_color_primary_light_selector
      guestIndicatorIcon.setImageDrawable(GuestIcon(color))

      availability match {
        case Some(av) =>
          userAvailability.setVisible(true)
          userAvailability.set(av)
        case None =>
          userAvailability.setVisible(false)
      }

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
