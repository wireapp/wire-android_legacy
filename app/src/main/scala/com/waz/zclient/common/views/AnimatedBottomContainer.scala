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
package com.waz.zclient.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.waz.utils.events.Signal
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.utils.ViewUtils


class AnimatedBottomContainer(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style){
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val isExpanded = Signal[Boolean](false)

  def openAnimated(): Unit = {
    setTranslationY(ViewUtils.toPx(getContext, 160))
    animate.translationY(0).setDuration(150).setInterpolator(new Expo.EaseOut).withStartAction(new Runnable() {
      def run(): Unit = {
        setVisibility(View.VISIBLE)
      }
    }).withEndAction(new Runnable() {
      def run(): Unit = {
        isExpanded ! true
      }
    })
  }

  def closedAnimated(): Unit = {
    setTranslationY(0)
    animate.translationY(ViewUtils.toPx(getContext, 160)).setDuration(150).setInterpolator(new Expo.EaseOut).withEndAction(new Runnable() {
      def run(): Unit = {
        setVisibility(View.GONE)
        isExpanded ! false
      }
    })
  }
}
