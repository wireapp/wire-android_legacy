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
import android.widget.{LinearLayout, NumberPicker}
import androidx.appcompat.view.ContextThemeWrapper
import com.waz.utils.events.{EventStream, Subscription}
import com.waz.zclient.conversation.ConversationController._
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

import scala.concurrent.duration.FiniteDuration

class EphemeralLayout(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  import EphemeralLayout._

  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  lazy val numberPicker = new NumberPicker(new ContextThemeWrapper(getContext, R.style.NumberPickerText))

  val expirationSelected = EventStream[(Option[FiniteDuration], Boolean)]()

  def setSelectedExpiration(expiration: Option[FiniteDuration]): Unit =
    numberPicker.setValue(PredefinedExpirations.indexWhere(_ == expiration))

  override protected def onFinishInflate(): Unit = {
    super.onFinishInflate()
    numberPicker.setMinValue(0)
    numberPicker.setMaxValue(PredefinedExpirations.size - 1)
    numberPicker.setDisplayedValues(PredefinedExpirations.map(getEphemeralDisplayString).toArray)
    numberPicker.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit =
        expirationSelected ! (PredefinedExpirations(numberPicker.getValue), true)
    })
    numberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
      override def onValueChange(picker: NumberPicker, oldVal: Int, newVal: Int): Unit = {
        expirationSelected ! (PredefinedExpirations(numberPicker.getValue), false)
      }
    })

    try {
      val f = numberPicker.getClass.getDeclaredField("mSelectionDivider") //NoSuchFieldException
      f.setAccessible(true)
      f.set(numberPicker, getDrawable(R.drawable.number_picker_divider))
    } catch {
      case t: Throwable =>
        error(l"Something went wrong", t)
    }
    addView(numberPicker)
  }

  private var subscription = Option.empty[Subscription]
  def setCallback(cb: Callback): Unit = {
    subscription.foreach(_.destroy())
    subscription = Some(expirationSelected.onUi {
      case (exp, close) => cb.onEphemeralExpirationSelected(exp, close)
    })
  }
}

object EphemeralLayout {

  trait Callback {
    def onEphemeralExpirationSelected(expiration: Option[FiniteDuration], close: Boolean): Unit
  }
}
