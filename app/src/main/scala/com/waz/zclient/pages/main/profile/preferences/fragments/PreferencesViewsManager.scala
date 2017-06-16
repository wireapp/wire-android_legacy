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
package com.waz.zclient.pages.main.profile.preferences.fragments

import java.io.InvalidClassException

import android.content.Context
import android.support.v4.app.Fragment
import android.view.{View, ViewGroup}
import android.widget.{AdapterViewAnimator, BaseAdapter}
import com.waz.zclient.{BaseActivity, R}

import scala.collection.mutable

case class PreferencesViewsManager(activity: BaseActivity) {

  def popView(): Unit ={
    activity.getSupportFragmentManager.popBackStack()
  }

  def openView(tag: String): Unit = {
    val fragment: Fragment = tag match {
      case _ => throw new InvalidClassException(tag)
    }
    activity.getSupportFragmentManager
      .beginTransaction()
      .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
      .replace(R.id.content, fragment, tag)
      .addToBackStack(tag)
      .commit()
  }

}

object PreferencesViewsManager {
}

class PreferencesViewSwitcher(context: Context) extends AdapterViewAnimator(context) {

  override def onAnimationEnd() = {
    super.onAnimationEnd()
    if (getDisplayedChild < getAdapter.getCount - 1) {
      Option(getAdapter.asInstanceOf[StackAdapter]).foreach(_.popStack())
    }
  }
}

trait StackElement{
  def id: Long
  def newInstance(convertView: View, parent: ViewGroup): View
}

class StackAdapter extends BaseAdapter {

  private val stack = mutable.Stack[StackElement]()

  def addToStack(value: StackElement) = {
    stack.push(value)
    notifyDataSetChanged()
  }

  def popStack() = {
    stack.pop()
    notifyDataSetChanged()
  }

  override def getItemId(position: Int) = stack(position).id

  override def getCount = stack.size

  override def getView(position: Int, convertView: View, parent: ViewGroup) = {
    getItem(position).newInstance(convertView, parent)
  }

  override def getItem(position: Int) = stack(position)
}
