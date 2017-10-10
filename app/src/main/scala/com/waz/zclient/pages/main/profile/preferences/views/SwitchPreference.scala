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
package com.waz.zclient.pages.main.profile.preferences.views

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.{CompoundButton, Switch}
import com.waz.content.Preferences.PrefKey
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal}
import com.waz.zclient.{R, ViewHelper}

trait Switchable {
  val onCheckedChange: EventStream[Boolean]
  def setChecked(checked: Boolean): Unit
}

class SwitchPreference(context: Context, attrs: AttributeSet, style: Int) extends TextButton(context, attrs, style) with Switchable with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def layoutId = R.layout.preference_switch

  private val attributesArray: TypedArray =
    context.getTheme.obtainStyledAttributes(attrs, R.styleable.SwitchPreference, 0, 0)

  val keyAttr = Option(attributesArray.getString(R.styleable.SwitchPreference_key))
  val switch = findById[Switch](R.id.preference_switch)
  val prefInfo = Signal[PrefInfo]()
  lazy val zms = inject[Signal[ZMessaging]]
  lazy val pref = for {
    z <- zms
    prefInfo <- prefInfo
  } yield {
    if (prefInfo.global)
      z.prefs.preference(prefInfo.key)
    else
      z.userPrefs.preference(prefInfo.key)
  }

  override val onCheckedChange = EventStream[Boolean]()

  pref.flatMap(_.signal).on(Threading.Ui) { setChecked }
  onCheckedChange.on(Threading.Ui) { value =>
    pref.head.map(_.update(value))(Threading.Ui)
  }
  keyAttr.foreach{ key =>
    prefInfo ! PrefInfo(PrefKey[Boolean](key, customDefault = false), global = false)
  }

  switch.setOnCheckedChangeListener(new OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) = {
      pref.head.map(_.update(isChecked))(Threading.Ui)
      onCheckedChange ! isChecked
    }
  })

  def setDisabled(disabled: Boolean) = {
    setEnabled(!disabled)
    switch.setEnabled(!disabled)
    title.foreach(_.setAlpha(if (disabled) 0.5f else 1f))
  }

  override def setChecked(checked: Boolean): Unit = {
    switch.setChecked(checked)
  }

  def setPreference(prefKey: PrefKey[Boolean], global: Boolean = false): Unit = {
    this.prefInfo ! PrefInfo(prefKey, global)
  }
}

case class PrefInfo(key: PrefKey[Boolean], global: Boolean)
