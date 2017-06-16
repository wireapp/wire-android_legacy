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
package com.waz.zclient.pages.main.profile.preferences

import android.content.Context
import android.util.AttributeSet
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageController.{ImageSource, WireImage}
import net.xpece.android.support.preference.Preference

class PicturePreference(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) extends Preference(context, attrs, defStyleAttr, defStyleRes) with PreferenceHelper {
  def this(context: Context, attrs: AttributeSet, defStyleAttr: Int) = this(context, attrs, defStyleAttr, R.style.Preference_Material)
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, R.attr.preferenceStyle)
  def this(context: Context) = this(context, null)

  val zms = inject[Signal[ZMessaging]]
  val wireImage = zms.flatMap(_.users.selfUser.flatMap(_.picture match {
    case Some(picture) => Signal[ImageSource](WireImage(picture))
    case _ => Signal.empty[ImageSource]
  }))

  private val diameter = getDimenPx(R.dimen.pref_account_icon_size)

  returning(new ImageAssetDrawable(wireImage, ImageAssetDrawable.ScaleType.CenterInside, ImageAssetDrawable.RequestBuilder.Round)) { d =>
    d.setBounds(0, 0, diameter, diameter)
    setIcon(d)
  }
}
