/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.calling.views

import android.content.Context
import android.support.v7.widget.LinearLayoutManager.HORIZONTAL
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.util.AttributeSet
import android.view.View.VISIBLE
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view._
import android.widget.{FrameLayout, LinearLayout}
import com.waz.api.impl.AccentColor
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.calling.controllers.CurrentCallController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.views.calling.CallingGainView
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.{R, ViewHelper}
import timber.log.Timber

class AudioParticipantsView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends RecyclerView(context, attrs, defStyleAttr) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  val controller = inject[CurrentCallController]

  val layoutManager = new LinearLayoutManager(context, HORIZONTAL, false)
  setLayoutManager(layoutManager)
  setAdapter(new AudioParticipantsAdapter(context))

  class AudioParticipantsAdapter(context: Context) extends RecyclerView.Adapter[ViewHolder] {

    val oneToOneViewType = 0
    val groupCallViewType = 1

    val largeChatheadWidth = getDimenPx(R.dimen.calling__participants_max_diameter)
    val smallChatheadWidth = getDimenPx(R.dimen.calling__participants_group_call)

    val largeChatheadMargin = getDimenPx(R.dimen.list_padding_top)
    val normalChatheadMargin = getDimenPx(R.dimen.wire__padding__small)

    var participantsToDisplay = Vector.empty[UserId]

    controller.participantIdsToDisplay.on(Threading.Ui) { data =>
      participantsToDisplay = data
      notifyDataSetChanged()
      scrollToCenter(data)
    }

    //TODO doesn't work perfectly, switch to a simpler view like list/galleryView
    def scrollToCenter(data: Vector[UserId]): Unit = if (data.size > 1) {
      val dataSetCenter = (data.size * smallChatheadWidth + (data.size - 1) * (normalChatheadMargin * 2) + 2 * largeChatheadMargin) / 2
      val displayCenter = AudioParticipantsView.this.getWidth / 2
      val amountToScroll = dataSetCenter - displayCenter

      Timber.d(s"performing scroll to center, amountToScroll: $amountToScroll")
      scrollToPosition(0)
      scrollBy(amountToScroll, 0)
    }

    override def getItemCount: Int = {
      participantsToDisplay.size
    }

    override def getItemViewType(position: Int): Int = if (participantsToDisplay.size == 1) oneToOneViewType else groupCallViewType

    override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = {
      val view = holder.itemView.asInstanceOf[AudioParticipantChatheadView]
      participantsToDisplay.lift(position).foreach(view.userId ! _)

      val size = getItemViewType(position) match {
        case this.oneToOneViewType => largeChatheadWidth
        case this.groupCallViewType => smallChatheadWidth
      }

      view.setSize(size, participantsToDisplay.size > 1 && position == 0, participantsToDisplay.size > 1 && position == participantsToDisplay.size - 1)
    }

    override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = {
      new ViewHolder(new AudioParticipantChatheadView(parent.getContext))
    }
  }
}

class ViewHolder(view: AudioParticipantChatheadView) extends RecyclerView.ViewHolder(view)

protected class AudioParticipantChatheadView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  val gainMargin = getDimenPx(R.dimen.calling__chat_head__gain_margin)
  val normalChatheadMargin = getDimenPx(R.dimen.wire__padding__small)
  val largeChatheadMargin = getDimenPx(R.dimen.list_padding_top)

  val controller = inject[CurrentCallController]

  val userId = Signal[UserId]

  LayoutInflater.from(context).inflate(R.layout.calling__participants__chathead, this, true)
  setOrientation(LinearLayout.VERTICAL)

  lazy val nameView: UserNameView = findById(R.id.ttv__calling__participant__name)
  lazy val chatheadView: ChatheadView = findById(R.id.chv__calling__participant__chathead)
  lazy val gainView: CallingGainView = findById(R.id.cgv__calling__participant__gain)

  (for {
    userStorage <- controller.userStorage
    userId <- userId
    userData <- userStorage.signal(userId)
  } yield userData).on(Threading.Ui)(data => gainView.setGainColor(AccentColor(data.accent).getColor()))


  (for (userId <- userId) yield userId).on(Threading.Ui) { userId =>
    Timber.d(s"Setting userId: $userId")
    nameView.setUserId(userId)
    chatheadView.setUserId(userId)
  }

  controller.callEstablished.zip(controller.participantIdsToDisplay).map {
    case (true, otherParticipants) if otherParticipants.size > 1 => VISIBLE
    case _ => View.GONE
  }.on(Threading.Ui)(nameView.setVisibility)

  (for {
    (vcs, convId) <- controller.voiceServiceAndCurrentConvId
    userId <- userId
    volume <- vcs.volumeChanged(convId, userId)
  } yield volume).on(Threading.Ui)(gainView.onGainHasChanged(_))

  def setSize(size: Int, isFirst: Boolean, isLast: Boolean): Unit = {
    val params = new RecyclerView.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
    val leftMargin = if (isFirst) largeChatheadMargin else normalChatheadMargin
    val rightMargin = if (isLast) largeChatheadMargin else normalChatheadMargin
    params.setMargins(leftMargin, normalChatheadMargin, rightMargin, normalChatheadMargin)
    setLayoutParams(params)

    gainView.setLayoutParams(new FrameLayout.LayoutParams(size, size))
    chatheadView.setLayoutParams(new FrameLayout.LayoutParams(size - gainMargin, size - gainMargin, Gravity.CENTER))
    nameView.setLayoutParams(new LinearLayout.LayoutParams(size, WRAP_CONTENT))
  }
}

class UserNameView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends TypefaceTextView(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  val zms = inject[Signal[ZMessaging]]

  def setUserId(userId: UserId): Unit = {
    zms.flatMap(_.usersStorage.optSignal(userId)).on(Threading.Ui) {
      case Some(userData) => setText(userData.getDisplayName)
      case _ => setText("")
    }
  }
}

