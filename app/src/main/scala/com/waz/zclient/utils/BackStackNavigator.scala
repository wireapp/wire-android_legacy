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
package com.waz.zclient.utils

import java.util

import android.content.Context
import android.os.{Bundle, Parcel, Parcelable}
import android.view.{LayoutInflater, View, ViewGroup, ViewPropertyAnimator}
import com.wire.signals.Signal
import com.waz.zclient.Injectable
import BackStackNavigator._

import scala.collection.mutable

abstract class BackStackKey(args: Bundle) extends Parcelable {
  def nameId: Int
  def layoutId: Int
  def onViewAttached(v: View): Unit
  def onViewDetached(): Unit

  override def writeToParcel(dest: Parcel, flags: Int) = {
    dest.writeString(getClass.getName)
    dest.writeBundle(args)
  }

  override def describeContents() = 0
}

object BackStackKey {
  val CREATOR: Parcelable.Creator[BackStackKey] = new Parcelable.Creator[BackStackKey] {
    override def createFromParcel(source: Parcel): BackStackKey = {
      try{
        val className = source.readString()
        val args = source.readBundle()
        getClass.getClassLoader.loadClass(className).getConstructor(classOf[Bundle]).newInstance(args).asInstanceOf[BackStackKey]
      } catch {
        case e: Exception => null
      }
    }
    override def newArray(size: Int): Array[BackStackKey] = Array.ofDim(size)
  }
}

trait TransitionAnimation extends Parcelable {
  def inAnimation(view: View, root: View, forward: Boolean): ViewPropertyAnimator
  def outAnimation(view: View, root: View, forward: Boolean): ViewPropertyAnimator

  override def writeToParcel(dest: Parcel, flags: Int) = dest.writeString(this.getClass.getName)
  override def describeContents() = 0
}

object TransitionAnimation {
  val CREATOR: Parcelable.Creator[TransitionAnimation] = new Parcelable.Creator[TransitionAnimation] {
    override def createFromParcel(source: Parcel): TransitionAnimation = {
      try{
        getClass.getClassLoader.loadClass(source.readString()).newInstance().asInstanceOf[TransitionAnimation]
      } catch {
        case e: Exception => null
      }
    }
    override def newArray(size: Int): Array[TransitionAnimation] = Array.ofDim(size)
  }
}

class BackStackNavigator(implicit context: Context) extends Injectable {

  private val stack = mutable.Stack[(BackStackKey, TransitionAnimation)]()

  val currentState = Signal[BackStackKey]()

  private var root: Option[ViewGroup] = None
  private val inflater: LayoutInflater = LayoutInflater.from(context)

  def setup(root: ViewGroup): Unit = {
    this.root = Option(root)
    stack.clear()
  }

  def goTo(backStackKey: BackStackKey, transitionAnimation: TransitionAnimation = DefaultTransition()): Unit = {
    stack.lastOption.foreach(state => detachView(state._1, transitionAnimation, forward = true))
    stack.push((backStackKey, transitionAnimation))
    createAndAttachView(backStackKey, transitionAnimation, forward = true)
    currentState ! stack.top._1
  }

  def back(): Boolean = {
    if (stack.length > 1) {
      val (detachedState, transition) = stack.pop()
      detachView(detachedState, transition, forward = false)
      createAndAttachView(stack.top._1, transition, forward = false)
      currentState ! stack.top._1
      true
    } else {
      false
    }
  }

  def createAndAttachView(backStackKey: BackStackKey, transitionAnimation: TransitionAnimation, forward: Boolean): Unit ={
    root.foreach{ root =>
        val view = inflater.inflate(backStackKey.layoutId, root, false)
        root.addView(view)
        transitionAnimation.inAnimation(view, root, forward)
        backStackKey.onViewAttached(view)
    }
  }

  def getView(): Option[ViewGroup] ={
    root
  }

  def detachView(backStackKey: BackStackKey, transitionAnimation: TransitionAnimation, forward: Boolean): Unit ={
    root.foreach{ root =>
      backStackKey.onViewDetached()
      val removedView = Option(root.getChildAt(root.getChildCount - 1))
      removedView.foreach{ view =>
        disableView(view)
        transitionAnimation.outAnimation(view, root, forward).withEndAction(new Runnable {
          override def run() = root.removeView(view)
        })
      }
    }
  }

  def onRestore(root: ViewGroup, bundle: Bundle): Unit ={
    import scala.collection.JavaConverters._
    val keys = bundle.getParcelableArrayList[BackStackKey](KeysBundleKey).asScala
    val transitions = bundle.getParcelableArrayList[TransitionAnimation](TransitionsBundleKey).asScala
    stack.pushAll(keys.zip(transitions).reverse.filter(pair => pair._1 != null && pair._2 != null))

    this.root = Option(root)
    this.root.foreach{ root =>
      if (stack.nonEmpty) {
        val (backStackKey, _) = stack.top
        val view = inflater.inflate(backStackKey.layoutId, root, false)
        root.addView(view)
        backStackKey.onViewAttached(view)
      }
    }
  }

  def onSaveState(bundle: Bundle): Unit ={
    stack.headOption.foreach(_._1.onViewDetached())

    val lists = stackToParcelableLists
    bundle.putParcelableArrayList(KeysBundleKey, lists._1)
    bundle.putParcelableArrayList(TransitionsBundleKey, lists._2)
  }

  def stackToParcelableLists: (util.ArrayList[BackStackKey], util.ArrayList[TransitionAnimation]) = {
    import scala.collection.JavaConverters._
    val keys = stack.map(_._1).asJava
    val transitions = stack.map(_._2).asJava
    (new util.ArrayList(keys), new util.ArrayList(transitions))
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

object BackStackNavigator {
  private val KeysBundleKey = "KeysBundleKey"
  private val TransitionsBundleKey = "TransitionsBundleKey"
}

case class DefaultTransition() extends TransitionAnimation {
  def inAnimation(view: View, root: View, forward: Boolean): ViewPropertyAnimator = {
    view.setAlpha(0.0f)
    if (forward)
      view.setTranslationX(root.getWidth)
    else
      view.setTranslationX(-root.getWidth)
    view.animate().alpha(1.0f).translationX(0)
  }

  def outAnimation(view: View, root: View, forward: Boolean): ViewPropertyAnimator = {
    view.setAlpha(1.0f)
    if (forward)
      view.animate().alpha(0.0f).translationX(-root.getWidth)
    else
      view.animate().alpha(0.0f).translationX(root.getWidth)
  }
}
