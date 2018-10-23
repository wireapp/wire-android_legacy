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
package com.waz.zclient.messages.parts.quotes

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.waz.service.tracking.TrackingService
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.messages.parts.QuoteView
import com.waz.zclient.ui.text.LinkTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

class TextQuoteView (context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper with QuoteView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val collectionController = inject[CollectionController]
  val accentColorController = inject[AccentColorController]
  lazy val trackingService = inject[TrackingService]

  val textSizeRegular = getDimenPx(R.dimen.wire__text_size__regular)
  val textSizeEmoji   = getDimenPx(R.dimen.wire__text_size__emoji)

//  inflate(R.layout.message_text_quote)

  private val textView = findById[LinkTextView](R.id.text)

  message
    .map(_.contentString)
    .onUi(textView.setText)

}
