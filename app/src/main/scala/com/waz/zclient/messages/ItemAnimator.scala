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
import android.support.v7.widget.RecyclerView.ViewHolder
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.zclient.utils.RichView

class ItemAnimator extends DefaultItemAnimator {


  //Setting a payload in notifyItemChanged(int pos, Object payload) ensures that the old viewHolder is re-used, allowing
  //us to manually control animations
  override def animateChange(oldHolder: ViewHolder, newHolder: ViewHolder, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean = {
    if (oldHolder == newHolder) {

      //TODO get animations working nicely
      verbose(s"Handling animation manually: fromX: $fromX, fromY: $fromY, toX: $toX, toY: $toY")
//      val holder = oldHolder.asInstanceOf[MessageViewHolder]
//      holder.view.getFooter.foreach { f =>
//        verbose(s"animating footer: alpha: ${f.getAlpha}, footer height: ${f.getHeight}, ${f.getMeasuredHeight}")
//
//        if (!holder.isFocused && !holder.hasLikes) {
//          //animate close
//          val footerAnim = f
//            .animate()
//            .alpha(0)
//            .translationY(-f.getHeight)
//            .setDuration(250)
//
//          footerAnim.setListener(new AnimatorListener {
//            override def onAnimationStart(animator: Animator) = {
//              verbose("Close beginning")
//              dispatchChangeStarting(oldHolder, false)
//            }
//
//            override def onAnimationEnd(animator: Animator) = {
//              verbose("close ended, setting gone")
////              f.setVisible(false)
//              footerAnim.setListener(null)
//              dispatchChangeFinished(oldHolder, false)
//            }
//
//            override def onAnimationCancel(animator: Animator): Unit = {}
//
//            override def onAnimationRepeat(animation: Animator): Unit = {}
//          }).start()
//
//        } else {
//          //animate open
//          val footerAnim = f
//            .animate()
//            .alpha(1)
//            .translationY(0)
//            .setDuration(250)
//
//          footerAnim.setListener(new AnimatorListener {
//            override def onAnimationStart(animator: Animator) = {
//              verbose("Open beginning, setting visible")
////              f.setVisible(true)
//              dispatchChangeStarting(oldHolder, false)
//            }
//
//            override def onAnimationEnd(animator: Animator) = {
//              verbose("Open ended")
//              footerAnim.setListener(null)
//              dispatchChangeFinished(oldHolder, false)
//            }
//
//            override def onAnimationCancel(animator: Animator): Unit = {}
//
//            override def onAnimationRepeat(animation: Animator): Unit = {}
//          }).start()
//        }
//      }
      true
    } else {
      verbose("Letting default animation take place")
      super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)
    }
  }


}

object ItemAnimator {

  trait Payload
  case object FocusChanged extends Payload
  case object LikesChanged extends Payload

}
