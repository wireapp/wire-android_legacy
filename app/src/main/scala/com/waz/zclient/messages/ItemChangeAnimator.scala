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

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.RecyclerView.{ItemAnimator, ViewHolder}
import android.view.View
import android.view.animation.Animation
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.zclient.utils.RichView

class ItemChangeAnimator extends DefaultItemAnimator {

  import ItemChangeAnimator._

  private var changes = Set.empty[ChangeInfo]

  private def itemPos(holder: ViewHolder) = holder.itemView.asInstanceOf[MessageView].pos

  //Setting a payload in notifyItemChanged(int pos, Object payload) ensures that the old viewHolder is re-used, allowing
  //us to manually control animations
  override def animateChange(oldHolder: ViewHolder, newHolder: ViewHolder, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean = {
    if (oldHolder == newHolder) {
      verbose(s"Handling animation manually on view ${itemPos(oldHolder)}: fromX: $fromX, fromY: $fromY, toX: $toX, toY: $toY")
      changes += ChangeInfo(oldHolder, pending = true)
      true
    } else {
      verbose("Letting default animation take place")
      super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)
    }
  }
  
  override def runPendingAnimations(): Unit = {
    super.runPendingAnimations()

    verbose(s"runPendingAnimations(), changes: $changes")
    if (changes.exists(_.pending)) {
      val changer: Runnable = new Runnable() {
        def run() = {
          for (change <- changes) {
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

  def animateChangeImpl(changeInfo: ChangeInfo): Unit = {
    val holder = changeInfo.viewHolder.asInstanceOf[MessageViewHolder]

    def onAnimStart(f: => Unit) = {
      changeInfo.pending = false
      dispatchChangeStarting(holder, false)
      f
    }

    def onAnimEnd(f: => Unit): Unit = {
      f
      changes -= changeInfo
      dispatchChangeFinished(holder, false)
    }

    holder.view.getFooter.foreach { f =>
      verbose(s"animating footer: alpha: ${f.getAlpha}, footer height: ${f.getHeight}, ${f.getMeasuredHeight}")

      if (!holder.shouldDisplayFooter) { //animate close
        val footerAnim = f
          .animate()
          .alpha(0)
          .translationY(-f.getHeight)
          .setDuration(250)

        footerAnim.setListener(new StartEndAnimatorListener {
          override def onAnimationStart(animator: Animator) = onAnimStart {
            verbose("Close beginning")
          }

          override def onAnimationEnd(animator: Animator) = onAnimEnd {
            verbose("close ended, setting gone")
            f.setVisible(false)
            footerAnim.setListener(null)
          }
        }).start()

      } else {
        //animate open
        val footerAnim = f
          .animate()
          .alpha(1)
          .translationY(0)
          .setDuration(250)

        footerAnim.setListener(new StartEndAnimatorListener {
          override def onAnimationStart(animator: Animator) = onAnimStart {
            verbose("Open beginning, setting visible")
            f.setVisible(true)
          }

          override def onAnimationEnd(animator: Animator) = onAnimEnd {
            verbose("Open ended")
            footerAnim.setListener(null)
          }
        }).start()
      }
    }
  }

  override def endAnimations(): Unit = {
    changes.filterNot(_.pending).foreach{ ch =>
      val view = ch.viewHolder.itemView
      view.animate().cancel()
      view.setTranslationY(0)
      view.setAlpha(1)
      view.setVisible(true)
    }
    changes = Set.empty

    //dispatchAnimationsFinished called by super method
    super.endAnimations()
  }

  override def isRunning: Boolean = super.isRunning || changes.nonEmpty
}

object ItemChangeAnimator {

  case class ChangeInfo(viewHolder: ViewHolder, var pending: Boolean)

  trait Payload
  case object FocusChanged extends Payload
  case object LikesChanged extends Payload

  abstract class StartEndAnimatorListener extends AnimatorListener {

    override def onAnimationCancel(animation: Animator): Unit = ()

    override def onAnimationRepeat(animation: Animator): Unit = ()
  }

}
