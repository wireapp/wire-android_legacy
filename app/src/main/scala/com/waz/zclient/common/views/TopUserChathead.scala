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
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.{Gravity, View}
import android.widget.{ImageView, LinearLayout}
import com.waz.ZLog
import com.waz.model.{Availability, UserData}
import com.waz.zclient.ui.text.{TextTransform, TypefaceTextView}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{ContextUtils, RichView}
import com.waz.zclient.views.AvailabilityView
import com.waz.zclient.{R, ViewHelper}

class TopUserChathead(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  implicit val logTag = ZLog.logTagFor[TopUserChathead]

  inflate(R.layout.top_user_chathead, this)

  private val chathead = findById[ChatheadView](R.id.cv__chathead)
  private val footer = findById[TypefaceTextView](R.id.ttv__text_view)
  private val icon = findById[ImageView](R.id.iv__availability_icon)

  private lazy val transformer = TextTransform.get(ContextUtils.getString(R.string.participants__chathead__name_label__text_transform))

  var a = Option.empty[TypedArray]
  try {
    a = Option(getContext.obtainStyledAttributes(attrs, R.styleable.TopUserChathead))
    val chatheadSize = a.fold(0)(_.getDimensionPixelSize(R.styleable.TopUserChathead_chathead_size, 0))

    if (chatheadSize > 0) {
      chathead.setWidth(chatheadSize)
      chathead.setHeight(chatheadSize)
    }
  } finally {
    a.foreach(_.recycle())
  }

  setOrientation(LinearLayout.VERTICAL)
  setGravity(Gravity.CENTER)
  footer.setTextColor(getColor(R.color.text__primary_dark))

  def setUser(user: UserData): Unit = {
    chathead.setUserId(user.id)
    footer.setText(transformer.transform(user.getDisplayName))
    AvailabilityView.drawable(user.availability, footer.getCurrentTextColor).foreach(icon.setImageDrawable)
    icon.setVisibility(if (user.availability != Availability.None) View.VISIBLE else View.GONE)
  }
}
