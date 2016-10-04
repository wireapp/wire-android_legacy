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
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.utils.ContextUtils.getDimen
import com.waz.zclient.utils.{ContextUtils, RichView}

class ItemChangeAnimator extends DefaultItemAnimator {

  import ItemChangeAnimator._

  private var pendingChanges = Set.empty[ViewHolder]
  private var anims = Set.empty[ValueAnimator]

  private def itemPos(holder: ViewHolder) = holder.itemView.asInstanceOf[MessageView].pos


  override def animateChange(oldHolder: ViewHolder, newHolder: ViewHolder, preInfo: ItemAnimator.ItemHolderInfo, postInfo: ItemAnimator.ItemHolderInfo): Boolean = {
    super.animateChange(oldHolder, newHolder, preInfo, postInfo)
  }

  //Setting a payload in notifyItemChanged(int pos, Object payload) ensures that the old viewHolder is re-used, allowing
  //us to manually control animations
  override def animateChange(oldHolder: ViewHolder, newHolder: ViewHolder, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean = {
    if (oldHolder == newHolder) {
      verbose(s"Handling animation manually on view ${itemPos(oldHolder)}: fromX: $fromX, fromY: $fromY, toX: $toX, toY: $toY")
      pendingChanges += oldHolder
      true
    } else {
      verbose("Letting default animation take place")
      super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)
    }
  }

  override def runPendingAnimations(): Unit = {
    super.runPendingAnimations()

    verbose(s"runPendingAnimations(), changes: $pendingChanges")
    if (pendingChanges.nonEmpty) {
      val changer: Runnable = new Runnable() {
        def run() = {
          for (change <- pendingChanges) {
            animateChangeImpl(change)
          }
        }
      }
      //      if (removalsPending) { //TODO figure out if there are any pending removals...
      //        ViewCompat.postOnAnimationDelayed(pendingChanges.head.viewHolder.itemView, changer, getRemoveDuration)
      //      }
      //      else {
      changer.run()
      //      }
    }
  }

  def animateChangeImpl(viewHolder: ViewHolder): Unit = {
    val holder = viewHolder.asInstanceOf[MessageViewHolder]
    holder.view.getFooter.foreach { footer =>
      verbose(s"animating footer: alpha: ${footer.getAlpha}, footer height: ${footer.getHeight}, ${footer.getMeasuredHeight}")
      returning(new OpenCloseAnimation(footer, !holder.shouldDisplayFooter) { self =>
        override def onStart(): Unit = {
          dispatchChangeStarting(holder, false)
        }

        override def onEnd(): Unit = {
          anims -= self
          dispatchChangeFinished(holder, false)
        }
      }) { anim =>
        pendingChanges -= viewHolder
        anims += anim
        anim.start()
      }
    }
  }

  override def endAnimations(): Unit = {
    anims.foreach(_.end())
    pendingChanges = Set.empty
    anims = Set.empty
    super.endAnimations() //dispatchAnimationsFinished called by super method
  }

  override def isRunning: Boolean = super.isRunning || pendingChanges.nonEmpty || anims.nonEmpty
}

object ItemChangeAnimator {

  trait Payload
  case object FocusChanged extends Payload
  case object LikesChanged extends Payload

  abstract class StartEndAnimatorListener extends AnimatorListener {

    override def onAnimationCancel(animation: Animator): Unit = ()

    override def onAnimationRepeat(animation: Animator): Unit = ()
  }

  abstract class OpenCloseAnimation(view: View, closing: Boolean) extends ValueAnimator with AnimatorListener with ValueAnimator.AnimatorUpdateListener {

    private val startHeight = 0
    private val targetHeight = getDimen(R.dimen.content__footer__height)(view.getContext) //TODO must be a better way

    setFloatValues(0, 1)
    addListener(this)
    addUpdateListener(this)

    def onStart(): Unit

    def onEnd(): Unit

    override def onAnimationStart(animation: Animator): Unit = {
      verbose(s"${if (closing) "Close" else "Open"} beginning")
      if (!closing) view.setVisible(true)
      onStart()
    }

    override def onAnimationEnd(animation: Animator): Unit = {
      verbose(s"${if (closing) "Close" else "Open"} ending")
      if (closing) view.setVisible(false)
      onEnd()
    }

    override def onAnimationUpdate(animation: ValueAnimator): Unit = {
      val fr = animation.getAnimatedFraction
      val newHeight = startHeight + (targetHeight * fr).toInt
      view.getLayoutParams.height = if (closing) view.getHeight - newHeight else newHeight
      //      view.setAlpha( if (closing) 1 - fr else 0 + fr)
//      view.requestLayout()
    }

    override def onAnimationRepeat(animation: Animator): Unit = ()
    override def onAnimationCancel(animation: Animator): Unit = ()
  }

}
