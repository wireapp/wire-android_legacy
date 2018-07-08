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
package com.waz.zclient.preferences.views

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
import com.waz.zclient.utils.RichView
import com.waz.ZLog.ImplicitTag._

trait Switchable {
  val onCheckedChange: EventStream[Boolean]
  def setChecked(checked: Boolean, disableListener: Boolean = false): Unit
}

class SwitchPreference(context: Context, attrs: AttributeSet, style: Int) extends TextButton(context, attrs, style) with Switchable with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def layoutId = R.layout.preference_switch

  private val attributesArray: TypedArray =
    context.getTheme.obtainStyledAttributes(attrs, R.styleable.SwitchPreference, 0, 0)

  lazy val zms = inject[Signal[ZMessaging]]

  val prefInfo = Signal[PrefInfo]()
  override val onCheckedChange = EventStream[Boolean]()

  val switch = findById[Switch](R.id.preference_switch)

  lazy val pref = for {
    z        <- zms
    prefInfo <- prefInfo
  } yield {
    if (prefInfo.global)
      z.prefs.preference(prefInfo.key)
    else
      z.userPrefs.preference(prefInfo.key)
  }

  Option(attributesArray.getString(R.styleable.SwitchPreference_key)).foreach { key =>
    prefInfo ! PrefInfo(PrefKey[Boolean](key, customDefault = false), global = false)
  }

  pref.flatMap(_.signal).onUi(setChecked(_))

  onCheckedChange.onUi { value =>
    pref.head.map(_.update(value))(Threading.Ui)
  }

  val checkChangeListener = new OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) = {
      pref.head.map(_.update(isChecked))(Threading.Ui)
      onCheckedChange ! isChecked
    }
  }

  switch.setOnCheckedChangeListener(checkChangeListener)
  this.onClick(setChecked(!switch.isChecked))

  def setDisabled(disabled: Boolean) = {
    setEnabled(!disabled)
    switch.setEnabled(!disabled)
    title.foreach(_.setAlpha(if (disabled) 0.5f else 1f))
  }

  override def setChecked(checked: Boolean, disableListener: Boolean = false): Unit = {
    if (disableListener) switch.setOnCheckedChangeListener(null)
    switch.setChecked(checked)
    if (disableListener) switch.setOnCheckedChangeListener(checkChangeListener)
  }

  def setPreference(prefKey: PrefKey[Boolean], global: Boolean = false): Unit = {
    this.prefInfo ! PrefInfo(prefKey, global)
  }
}

case class PrefInfo(key: PrefKey[Boolean], global: Boolean)
