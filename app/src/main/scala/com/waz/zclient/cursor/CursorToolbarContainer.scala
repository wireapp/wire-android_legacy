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
package com.waz.zclient.cursor

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.animation.{Animator, AnimatorListenerAdapter, ValueAnimator}
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.ViewHelper
import com.waz.zclient.R
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.utils._
import com.waz.ZLog.ImplicitTag._

class CursorToolbarContainer(context: Context, attrs: AttributeSet, defStyleAttr: Int)
    extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) {this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  val controller = inject[CursorController]

  val heightExpanded = getResources.getDimensionPixelSize(R.dimen.new_cursor_height)

  lazy val mainToolbar: View         = findById(R.id.c__cursor__main)
  lazy val secondaryToolbar: View    = findById(R.id.c__cursor__secondary)
  lazy val editToolbar: View         = findById(R.id.emct__edit_message__toolbar)

  val editVisible = controller.isEditingMessage

  def updateToolbarVisibility() = {
    import Threading.Implicits.Ui

    for {
      edit <- editVisible.head
      secondary <- controller.secondaryToolbarVisible.head
    } {
      mainToolbar.setVisible(!edit && !secondary)
      secondaryToolbar.setVisible(!edit && secondary)
      editToolbar.setVisible(edit)
    }
  }

  val toolbarSwitchAnimator = returning(ValueAnimator.ofFloat(0, 1)) { anim =>
    anim.setInterpolator(new Expo.EaseInOut)
    anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener {
      override def onAnimationUpdate(animation: ValueAnimator): Unit = {
        val offset = animation.getAnimatedValue.asInstanceOf[java.lang.Float]
        mainToolbar.setTranslationY(offset * -heightExpanded)
        secondaryToolbar.setTranslationY(heightExpanded + offset * -heightExpanded)
      }
    })

    anim.addListener(new AnimatorListenerAdapter {
      override def onAnimationStart(animation: Animator): Unit = {
        mainToolbar.setVisible(true)
        secondaryToolbar.setVisible(true)
      }
      override def onAnimationEnd(animation: Animator): Unit = updateToolbarVisibility()
    })
  }

  val editAnimator = returning(ValueAnimator.ofFloat(0, 1)) { anim =>
    anim.setInterpolator(new Expo.EaseInOut)
    anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener {
      override def onAnimationUpdate(animation: ValueAnimator): Unit = {
        val offset = animation.getAnimatedValue.asInstanceOf[java.lang.Float]
        editToolbar.setTranslationY(heightExpanded + offset * -heightExpanded)
      }
    })

    anim.addListener(new AnimatorListenerAdapter {
      override def onAnimationStart(animation: Animator): Unit = editToolbar.setVisible(true)
      override def onAnimationEnd(animation: Animator): Unit = updateToolbarVisibility()
    })
  }


  override def onFinishInflate(): Unit = {
    super.onFinishInflate()

    updateToolbarVisibility()

    controller.secondaryToolbarVisible.onChanged.on(Threading.Ui) {
      case true =>
        toolbarSwitchAnimator.cancel()
        toolbarSwitchAnimator.start()
      case false =>
        toolbarSwitchAnimator.cancel()
        toolbarSwitchAnimator.reverse()
    }

    editVisible.onChanged.on(Threading.Ui) {
      case true =>
        editAnimator.cancel()
        editAnimator.start()
      case false =>
        editAnimator.cancel()
        editAnimator.reverse()
    }
  }
}
