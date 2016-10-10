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
package com.waz.zclient.messages

import android.animation.Animator.AnimatorListener
import android.animation.{Animator, ValueAnimator}
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.RecyclerView.{ItemAnimator, ViewHolder}
import android.view.View
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.utils.ContextUtils.getDimen
import com.waz.zclient.utils.RichView

class ItemChangeAnimator extends DefaultItemAnimator {

  private var pendingChanges = Set.empty[ViewHolder]
  private var anims = Set.empty[ValueAnimator]

  override def animateChange(oldHolder: ViewHolder, newHolder: ViewHolder, preInfo: ItemAnimator.ItemHolderInfo, postInfo: ItemAnimator.ItemHolderInfo): Boolean = {
    super.animateChange(oldHolder, newHolder, preInfo, postInfo)
  }

  //Setting a payload in notifyItemChanged(int pos, Object payload) ensures that the old viewHolder is re-used, allowing
  //us to manually control animations. If this method returns true, then the animations will be run later on the UI thread
  //when the ReyclerView calls our #runPendingAnimations implementation
  override def animateChange(oldHolder: ViewHolder, newHolder: ViewHolder, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean = {
    if (oldHolder == newHolder) {
      pendingChanges += oldHolder
      true
    } else super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)
  }

  override def runPendingAnimations(): Unit = {
    super.runPendingAnimations()
    if (pendingChanges.nonEmpty) {
      val changer: Runnable = new Runnable() {
        def run() = {
          for (change <- pendingChanges) {
            animateChangeImpl(change)
          }
        }
      }
      changer.run()
    }
  }

  def animateChangeImpl(viewHolder: ViewHolder): Unit = {
    val holder = viewHolder.asInstanceOf[MessageViewHolder]
    holder.view.getFooter.foreach {
      case footer if shouldAnimateFooter(holder) =>
        returning(new FooterAnimator(footer, holder)) { anim =>
          pendingChanges -= viewHolder
          anims += anim
          anim.start()
        }
      case footer if shouldAnimateTimestamp(holder) =>
        returning(new TimestampLikesAnimator(footer, holder)) { anim =>
          pendingChanges -= viewHolder
          anims += anim
          anim.start()
        }
      case _ => //no animation
    }
  }

  def shouldAnimateFooter(h: MessageViewHolder) = h.shouldDisplayFooter && !h.view.getFooter.exists(_.isVisible) ||
    !h.shouldDisplayFooter && h.view.getFooter.exists(_.isVisible)

  def shouldAnimateTimestamp(h: MessageViewHolder) = h.hasLikes && h.isFocused && h.view.getFooter.exists(_.getContentTranslation == 0f) ||
    h.hasLikes && !h.isFocused && h.view.getFooter.exists(_.getContentTranslation > 0f)

  override def endAnimations(): Unit = {
    anims.foreach(_.end())
    pendingChanges = Set.empty
    anims = Set.empty
    super.endAnimations() //dispatchAnimationsFinished called by super method
  }

  override def isRunning: Boolean = super.isRunning || pendingChanges.nonEmpty || anims.nonEmpty

  abstract class StartEndAnimator(holder: ViewHolder) extends ValueAnimator with AnimatorListener with ValueAnimator.AnimatorUpdateListener {
    setFloatValues(0, 1)
    addListener(this)
    addUpdateListener(this)

    override def onAnimationStart(animation: Animator): Unit = {
      dispatchChangeStarting(holder, false)
    }

    override def onAnimationEnd(animation: Animator): Unit = {
      anims -= this
      dispatchChangeFinished(holder, false)
    }

    override def onAnimationRepeat(animation: Animator): Unit = ()

    override def onAnimationCancel(animation: Animator): Unit = onAnimationEnd(animation)
  }

  class FooterAnimator(footer: View, holder: MessageViewHolder) extends StartEndAnimator(holder) {

    private val closing = !holder.shouldDisplayFooter
    private val openHeight = getDimen(R.dimen.content__footer__height)(holder.itemView.getContext).toInt //TODO must be a better way

    setInterpolator(new Expo.EaseInOut)

    override def onAnimationStart(animation: Animator): Unit = {
      if (!closing) footer.setVisible(true)
      super.onAnimationEnd(animation)
    }

    override def onAnimationEnd(animation: Animator): Unit = {
      if (closing) footer.setVisible(false)
      super.onAnimationEnd(animation)
    }

    override def onAnimationUpdate(animation: ValueAnimator): Unit = {
      val heightDelta = (openHeight * animation.getAnimatedFraction).toInt
      footer.getLayoutParams.height = if (closing) openHeight - heightDelta else heightDelta
      footer.requestLayout()
    }
  }

  class TimestampLikesAnimator(footer: Footer, holder: MessageViewHolder) extends StartEndAnimator(holder) {

    private val animateDown = holder.hasLikes && holder.isFocused && footer.getContentTranslation == 0f

    private val translationHeight = getDimen(R.dimen.content__footer__height)(holder.itemView.getContext).toInt //TODO must be a better way

    override def onAnimationStart(animation: Animator): Unit = {
      super.onAnimationStart(animation)
    }

    override def onAnimationEnd(animation: Animator): Unit = {
      footer.setContentTranslationY(if (animateDown) translationHeight else 0)
      super.onAnimationEnd(animation)
    }

    override def onAnimationUpdate(animation: ValueAnimator): Unit = {
      val transDelta = translationHeight * animation.getAnimatedFraction
      footer.setContentTranslationY(if (animateDown) transDelta else translationHeight - transDelta)
    }
  }
}

object ItemChangeAnimator {

  trait Payload
  case object FocusChanged extends Payload
  case object LikesChanged extends Payload

}
