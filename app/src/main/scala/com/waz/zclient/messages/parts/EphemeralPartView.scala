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
package com.waz.zclient.messages.parts

import android.content.res.ColorStateList
import android.graphics.drawable.{ColorDrawable, Drawable}
import android.view.View
import android.widget.{ImageView, TextView}
import com.waz.api.AccentColor
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.ViewHelper
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.messages.MessageViewPart
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.ui.utils.{ColorUtils, TypefaceUtils}

trait EphemeralPartView extends MessageViewPart { self: ViewHelper =>

  lazy val redactedTypeface = TypefaceUtils.getTypeface(TypefaceUtils.getRedactedTypedaceName)
  lazy val accentController = inject[AccentColorController]

  val expired = message map { m => m.isEphemeral && m.expired }

  def registerEphemeral(textView: TextView) = {
    val originalTypeface = textView.getTypeface
    val originalColor = textView.getTextColors

    val typeface = expired map { if (_) redactedTypeface else originalTypeface }
    val color = expired flatMap[Either[ColorStateList, AccentColor]] {
      case true => accentController.accentColor.map { Right(_) }
      case false => Signal const Left(originalColor)
    }

    typeface { textView.setTypeface }
    color {
      case Left(csl) => textView.setTextColor(csl)
      case Right(ac) => textView.setTextColor(ac.getColor())
    }
  }

  def ephemeralDrawable(drawable: Drawable) =
    for {
      hide <- expired
      acc <- accentController.accentColor
    } yield
      if (hide) new ColorDrawable(ColorUtils.injectAlpha(ThemeUtils.getEphemeralBackgroundAlpha(getContext), acc.getColor()))
      else drawable

  def registerEphemeral(view: View, background: Drawable): Unit =
    ephemeralDrawable(background).on(Threading.Ui) { view.setBackground }

  def registerEphemeral(imageView: ImageView, imageDrawable: Drawable): Unit =
    ephemeralDrawable(imageDrawable).on(Threading.Ui) { imageView.setImageDrawable }
}
