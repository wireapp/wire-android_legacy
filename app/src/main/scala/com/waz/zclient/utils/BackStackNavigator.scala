/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.utils

import android.content.Context
import android.view.{LayoutInflater, View, ViewGroup, ViewPropertyAnimator}
import com.waz.utils.events.Signal
import com.waz.zclient.Injectable

import scala.collection.mutable

trait ViewState {
  def name: String
  def layoutId: Int
  def onViewAttached(v: View): Unit
  def onViewDetached(): Unit
}

class BackStackNavigator extends Injectable {

  private val stack = mutable.Stack[ViewState]()

  val currentState = Signal[ViewState]()

  //TODO: Options?
  private var root: ViewGroup = null
  private var inflater: LayoutInflater = null

  private var inAnimation = (view: View, root: View, forward: Boolean) => {
    view.setAlpha(0.0f)
    if (forward)
      view.setTranslationX(root.getWidth)
    else
      view.setTranslationX(-root.getWidth)
    view.animate().alpha(1.0f).translationX(0)
  }
  private var outAnimation = (view: View, root: View, forward: Boolean) => {
    view.setAlpha(1.0f)
    if (forward)
      view.animate().alpha(0.0f).translationX(-root.getWidth)
    else
      view.animate().alpha(0.0f).translationX(root.getWidth)
  }

  def setup(context: Context, root: ViewGroup): Unit = {
    this.root = root
    this.inflater = LayoutInflater.from(context)
    stack.clear()
  }

  def goTo(viewState: ViewState): Unit = {
    stack.lastOption.foreach(state => detachView(state, forward = true))
    createAndAttachView(stack.push(viewState).top, forward = true)
    currentState ! stack.top
  }

  def back(): Boolean = {
    if (stack.length > 1) {
      detachView(stack.pop, forward = false)
      createAndAttachView(stack.top, forward = false)
      currentState ! stack.top
      true
    } else {
      false
    }
  }

  def createAndAttachView(viewState: ViewState, forward: Boolean): Unit ={
    val view = inflater.inflate(viewState.layoutId, root, false)
    root.addView(view)
    inAnimation(view, root, forward)
    viewState.onViewAttached(view)
  }

  def detachView(viewState: ViewState, forward: Boolean): Unit ={
    viewState.onViewDetached()
    val removedView = root.getChildAt(root.getChildCount - 1)
    disableView(removedView)
    outAnimation(removedView, root, forward).withEndAction(new Runnable {
      override def run() = root.removeView(removedView)
    })
  }

  def setInAnimation(animation: (View, View, Boolean) => ViewPropertyAnimator): Unit ={
    inAnimation = animation
  }

  def setOutAnimation(animation: (View, View, Boolean) => ViewPropertyAnimator): Unit ={
    outAnimation = animation
  }

  def disableView(view: View): Unit = {
    view.setEnabled(false)
    view match {
      case vg: ViewGroup =>
        (0 until vg.getChildCount).map(vg.getChildAt).foreach(disableView)
      case _ =>
    }
  }
}
