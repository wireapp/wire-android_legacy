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
package com.waz.zclient.messages.parts

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.{LinearLayout, TextView}
import com.waz.api.Message
import com.waz.api.Message.Type.MEMBER_JOIN
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.MessageContent
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.events.Signal
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.messages._
import com.waz.zclient.paintcode.ConversationIcon
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class MemberChangePartView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with MessageViewPart
    with ViewHelper
    with DerivedLogTag {
  
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.MemberChange

  setOrientation(LinearLayout.VERTICAL)

  inflate(R.layout.message_member_change_content)

  private lazy val zMessaging = inject[Signal[ZMessaging]]
  private lazy val users      = inject[UsersController]
  private lazy val participantsController = inject[ParticipantsController]

  val messageView: SystemMessageView  = findById(R.id.smv_header)
  val warningText: TextView  = findById(R.id.service_warning_text)
  val position = Signal[Int]()

  val iconGlyph: Signal[Either[Int, Drawable]] = message map { msg =>
    msg.msgType match {
      case Message.Type.MEMBER_JOIN if msg.firstMessage => Right(ConversationIcon(R.color.light_graphite))
      case Message.Type.MEMBER_JOIN =>                     Left(R.string.glyph__plus)
      case _ =>                                            Left(R.string.glyph__minus)
    }
  }


  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]) = {
    super.set(msg, part, opts)
    opts.foreach(position ! _.position)

  }

  val senderName = message.map(_.userId).flatMap(users.displayName)

  private val linkText = for {
    zms         <- zMessaging
    msg         <- message
    displayName <- senderName
    names <- users.getMemberNamesSplit(msg.members, zms.selfUserId)
    namesListString = users.membersNamesString(names.main, separateLast = names.others.isEmpty && !names.andYou, boldNames = true)
  } yield {
    import Message.Type._
    val me = zms.selfUserId
    val userId = msg.userId
    val shorten = names.others.nonEmpty
    val othersCount = names.others.size

    (msg.msgType, displayName, msg.members.toSeq) match {
        //Create Conv
      case (MEMBER_JOIN, Me, _)       if msg.firstMessage && msg.name.isDefined && shorten => getQuantityString(R.plurals.content__system__with_others_only, othersCount, namesListString, othersCount.toString)
      case (MEMBER_JOIN, Me, _)       if msg.firstMessage && msg.name.isDefined            => getString(R.string.content__system__with_list_only, namesListString)
      case (MEMBER_JOIN, Other(_), Seq(`me`)) if msg.firstMessage && msg.name.isDefined    => getString(R.string.content__system__with_you_only)
      case (MEMBER_JOIN, Other(_), _) if msg.firstMessage && msg.name.isDefined && shorten => getQuantityString(R.plurals.content__system__with_others_and_you, othersCount, namesListString, othersCount.toString)
      case (MEMBER_JOIN, Other(_), _) if msg.firstMessage && msg.name.isDefined            => getString(R.string.content__system__with_list_and_you, namesListString)

      //Add
      case (MEMBER_JOIN, Me, Seq(`me`)) if userId == me                                    => getString(R.string.content__system__you_joined).toUpperCase
      case (MEMBER_JOIN, Me, others) if shorten && others.nonEmpty && !others.contains(me) => getQuantityString(R.plurals.content__system__you_added_people_with_others, othersCount, namesListString, othersCount.toString)
      case (MEMBER_JOIN, Me, others) if others.nonEmpty && !others.contains(me)            => getString(R.string.content__system__you_added_people, namesListString)
      case (MEMBER_JOIN, Other(name), Seq(`me`))                                           => getString(R.string.content__system__someone_added_you, name)
      case (MEMBER_JOIN, Other(name), Seq(other)) if userId == other                       => getString(R.string.content__system__other_joined, name)
      case (MEMBER_JOIN, Other(name), others) if shorten && others.contains(me)            => getQuantityString(R.plurals.content__system__someone_added_people_and_you_with_others, othersCount, name, namesListString, othersCount.toString)
      case (MEMBER_JOIN, Other(name), _) if shorten                                        => getQuantityString(R.plurals.content__system__someone_added_people_with_others, othersCount, name, namesListString, othersCount.toString)
      case (MEMBER_JOIN, Other(name), _)                                                   => getString(R.string.content__system__someone_added_people, name, namesListString)

        //Remove
      case (MEMBER_LEAVE, Me, Seq(`me`))                                                   => getString(R.string.content__system__you_left)
      case (MEMBER_LEAVE, Me, _)                                                           => getString(R.string.content__system__you_removed_other, namesListString)
      case (MEMBER_LEAVE, Other(name), Seq(`me`))                                          => getString(R.string.content__system__other_removed_you, name)
      case (MEMBER_LEAVE, Other(name), Seq(`userId`))                                      => getString(R.string.content__system__other_left, name)
      case (MEMBER_LEAVE, Other(name), _)                                                  => getString(R.string.content__system__other_removed_other, name, namesListString)

      case _ =>
        verbose(l"Unexpected system message format: (${msg.msgType} from $displayName with ${msg.members.toSeq})")
        ""
    }
  }

  private val servicesPresentWarning = for {
    zms <- zMessaging
    msg <- message
    members <- users.users(msg.members)
    convMembersIds <- zms.membersStorage.activeMembers(msg.convId)
    convMembers <- users.users(convMembersIds)
  } yield {
    if ((members.exists(_.isWireBot) && msg.msgType == MEMBER_JOIN) ||
      (members.exists(_.id == zms.selfUserId) && convMembers.exists(_.isWireBot) && msg.msgType == MEMBER_JOIN))
      Some(R.string.generic_service_warning)
    else
      None
  }


  message.map(m => if (m.firstMessage && m.name.nonEmpty) Some(16) else None)
    .map(_.map(toPx))
    .onUi(_.foreach(this.setMarginTop))

  iconGlyph {
    case Left(i) => messageView.setIconGlyph(i)
    case Right(d) => messageView.setIcon(d)
  }

  linkText.onUi { text =>
    messageView.setTextWithLink(text, getColor(R.color.accent_blue)) {
      participantsController.onShowParticipants ! None
    }
  }

  servicesPresentWarning.onUi { text =>
    warningText.setVisible(text.isDefined)
    text.foreach(warningText.setText(_))
  }

}
