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
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.waz.model.{ConversationRole, UserId}
import com.waz.zclient.common.views.LinkTextView
import com.waz.zclient.participants.ParticipantsController.ClassifiedConversation
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.getColor
import com.wire.signals.{EventStream, SourceStream}
import com.waz.zclient.R

final class UnconnectedParticipantAdapter(userId:      UserId,
                                          isGuest:     Boolean,
                                          isExternal:  Boolean,
                                          isDarkTheme: Boolean,
                                          isGroup:     Boolean,
                                          isWireless:  Boolean,
                                          userName:    String,
                                          userHandle:  String,
                                          isFederated: Boolean,
                                          linkedText:  Option[(String, Int)] = None,
                                          classified:  ClassifiedConversation = ClassifiedConversation.None
                                         )(implicit context: Context)
  extends BaseSingleParticipantAdapter(userId, isGuest, isExternal, isDarkTheme, isGroup, isWireless, isFederated) {
  import BaseSingleParticipantAdapter._
  import UnconnectedParticipantAdapter._

  val onLinkedTextClicked: SourceStream[Unit] = EventStream[Unit]

  def set(timerText:       Option[String],
          participantRole: Option[ConversationRole] = None,
          selfRole:        Option[ConversationRole] = None): Unit = {
    this.timerText       = timerText
    this.participantRole = participantRole
    this.selfRole        = selfRole
    notifyDataSetChanged()
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = viewType match {
    case UserName =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.participant_user_name_row, parent,false)
      UserNameViewHolder(view)
    case LinkedInfo =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.participant_linked_info_row, parent,false)
      LinkedInfoViewHolder(view)
    case _ =>
      super.onCreateViewHolder(parent, viewType)
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = holder match {
    case h: UserNameViewHolder   => h.bind(userName, userHandle, classified)
    case h: LinkedInfoViewHolder => linkedText.foreach { case (text, color) => h.bind(text, color, { onLinkedTextClicked ! (())} ) }
    case _                       => super.onBindViewHolder(holder, position)
  }

  override def getItemCount: Int =
    super.getItemCount + (if (linkedText.isDefined) 2 else 1)

  override def getItemId(position: Int): Long = getItemViewType(position) match {
    case Header     => 0L
    case GroupAdmin => 1L
    case UserName   => 2L
    case LinkedInfo => 3L
  }

  /*
  The order is:
    UserName
    Header
    GroupAdmin (optional)
    LinkedInfo (optional)
   */
  override def getItemViewType(position: Int): Int =
    if (position == 0) UserName
    else if (position == 2 && isGroupAdminViewVisible) GroupAdmin
    else if (linkedText.isDefined && position == getItemCount - 1) LinkedInfo
    else Header
}

object UnconnectedParticipantAdapter {
  final case class UserNameViewHolder(view: View) extends ViewHolder(view) {
    import com.waz.zclient.utils.ContextUtils.getString
    import com.waz.zclient.utils._

    private lazy val userName   = view.findViewById[TypefaceTextView](R.id.user_name)
    private lazy val userHandle = view.findViewById[TypefaceTextView](R.id.user_handle)
    private lazy val classifiedBanner = view.findViewById[FrameLayout](R.id.user_classified_banner)
    private lazy val classifiedBannerText = view.findViewById[TypefaceTextView](R.id.user_classified_banner_text)

    private def setBanner(classified: ClassifiedConversation)(implicit ctx: Context): Unit =
      classified match {
        case ClassifiedConversation.Classified =>
          classifiedBanner.setBackgroundColor(getColor(R.color.background_light))
          classifiedBanner.setVisible(true)
          classifiedBannerText.setTransformedText(getString(R.string.conversation_is_classified))
          classifiedBannerText.setTextColor(getColor(R.color.background_dark))
          classifiedBannerText.setVisible(true)
        case ClassifiedConversation.Unclassified =>
          classifiedBanner.setBackgroundColor(getColor(R.color.background_dark))
          classifiedBanner.setVisible(true)
          classifiedBannerText.setTransformedText(getString(R.string.conversation_is_unclassified))
          classifiedBannerText.setTextColor(getColor(R.color.background_light))
          classifiedBannerText.setVisible(true)
        case ClassifiedConversation.None =>
          classifiedBanner.setVisible(false)
          classifiedBannerText.setVisible(false)
      }

    def bind(userName: String, userHandle: String, classified: ClassifiedConversation)(implicit ctx: Context): Unit = {
      this.userName.setText(userName)
      this.userHandle.setText(userHandle)
      view.setContentDescription(s"User: $userName")
      setBanner(classified)
    }
  }

  final case class LinkedInfoViewHolder(view: View) extends ViewHolder(view) {
    private lazy val linkTextView = view.findViewById[LinkTextView](R.id.participant_linked_info_text_view)

    def bind(text: String, color: Int, action: => Unit): Unit = {
      linkTextView.setTextWithLink(text, color)(action)
    }
  }
}
