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
package com.waz.zclient.conversationlist.views

import java.math.BigInteger
import java.nio.{ByteBuffer, ByteOrder}
import java.util.UUID

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConvId, UserId}
import com.waz.utils.events.Signal
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.common.views.ImageController.{ImageSource, NoImage}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{R, ViewHelper}

import scala.collection.mutable.ArrayBuffer

class ConversationAvatarView (context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.view_conversation_avatar)
  setLayoutParams(new LayoutParams(getDimenPx(R.dimen.conversation_list__row__avatar_size), getDimenPx(R.dimen.conversation_list__row__avatar_size)))

  private val groupBackgroundDrawable = getDrawable(R.drawable.conversation_group_avatar_background)

  private val avatarStartTop = ViewUtils.getView(this, R.id.conversation_avatar_start_top).asInstanceOf[ChatheadView]
  private val avatarEndTop = ViewUtils.getView(this, R.id.conversation_avatar_end_top).asInstanceOf[ChatheadView]
  private val avatarStartBottom = ViewUtils.getView(this, R.id.conversation_avatar_start_bottom).asInstanceOf[ChatheadView]
  private val avatarEndBottom = ViewUtils.getView(this, R.id.conversation_avatar_end_bottom).asInstanceOf[ChatheadView]

  private val avatarSingle = ViewUtils.getView(this, R.id.avatar_single).asInstanceOf[ChatheadView]
  private val avatarGroup = ViewUtils.getView(this, R.id.avatar_group).asInstanceOf[View]
  private val avatarGroupSingle = ViewUtils.getView(this, R.id.conversation_avatar_single_group).asInstanceOf[ChatheadView]

  private val imageSources = Seq.fill(4)(Signal[ImageSource]())

  private val chatheads = Seq(avatarStartTop, avatarEndTop, avatarStartBottom, avatarEndBottom)

  def setMembers(members: Seq[UserId], convId: ConvId, conversationType: ConversationType): Unit = {
    conversationType match {
      case ConversationType.Group if members.size == 1 =>
        chatheads.foreach(_.clearUser())
        avatarGroupSingle.setUserId(members.head)
      case ConversationType.Group =>
        val shuffledIds = ConversationAvatarView.shuffle(members.sortBy(_.str), convId)
        avatarGroupSingle.clearUser()
        chatheads.map(Some(_)).zipAll(shuffledIds.take(4).map(Some(_)), None, None).foreach{
          case (Some(view), Some(uid)) =>
            view.setUserId(uid)
          case (Some(view), None) =>
            view.clearUser()
          case _ =>
        }
      case ConversationType.OneToOne | ConversationType.WaitForConnection if members.nonEmpty =>
        members.headOption.fold(avatarSingle.clearUser())(avatarSingle.setUserId)
      case _ =>
        imageSources.foreach(_ ! NoImage())
    }
  }

  def setConversationType(conversationType: ConversationType): Unit ={
    conversationType match {
      case ConversationType.Group =>
        avatarGroup.setVisibility(View.VISIBLE)
        avatarSingle.setVisibility(View.GONE)
        setBackground(groupBackgroundDrawable)
      case ConversationType.OneToOne | ConversationType.WaitForConnection =>
        avatarGroup.setVisibility(View.GONE)
        avatarSingle.setVisibility(View.VISIBLE)
        setBackground(null)
      case _ =>
        avatarGroup.setVisibility(View.GONE)
        avatarSingle.setVisibility(View.GONE)
        setBackground(null)
    }
  }

  def clearImages(): Unit ={
    chatheads.foreach(_.clearUser())
    avatarSingle.clearUser()
  }
}

object ConversationAvatarView {

  def longToUnsignedLongLittleEndian(l: Long): BigInt = {
    val value = BigInt(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(l).array())
    if (value.signum < 0) {
      value + BigInteger.ONE.shiftLeft(java.lang.Long.SIZE)
    } else {
      value
    }
  }

  def uuidToBytes(uuid: UUID): Array[Byte] = {
    val byteBuffer = ByteBuffer.wrap(new Array[Byte](16))
    byteBuffer.putLong(uuid.getMostSignificantBits)
    byteBuffer.putLong(uuid.getLeastSignificantBits)
    byteBuffer.array()
  }

  case class RandomGeneratorFromConvId(convId: ConvId) {

    private val uuid = UUID.fromString(convId.str)

    private val leastBits = longToUnsignedLongLittleEndian(uuid.getLeastSignificantBits)
    private val mostBits = longToUnsignedLongLittleEndian(uuid.getMostSignificantBits)

    private var step = 0

    def rand(max: Long): Long = {
      val maxBig = BigInt(max)
      (rand() mod maxBig).longValue()
    }

    def rand(): BigInt = {
      val value =
        if (step % 2 == 0) {
          mostBits
        } else {
          leastBits
        }
      step += 1
      value
    }
  }

  def shuffle[T](seq: Seq[T], convId: ConvId): Seq[T] = {
    val generator = RandomGeneratorFromConvId(convId)
    val input = new ArrayBuffer[T] ++= seq
    val output = new ArrayBuffer[T]

    seq.indices.foreach { _ =>
      val idx = generator.rand(input.size).toInt
      output += input(idx)
      input.remove(idx)
    }

    output
  }
}
