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
package com.waz.zclient.usersearch.views

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import com.waz.api.ContentSearchQuery
import com.waz.content.MessageIndexStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.collection.controllers.{CollectionController, CollectionUtils}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views.ChatHeadView
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.MsgPart.Text
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.messages.{MessageViewPart, MsgPart, UsersController}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.{ColorUtils, TextViewUtils}
import com.waz.zclient.utils.Time.TimeStamp
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.utils._

trait SearchResultRowView extends MessageViewPart with ViewHelper {
  val searchedQuery = Signal[ContentSearchQuery]()
}

class TextSearchResultRowView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with SearchResultRowView
    with DerivedLogTag {

  import TextSearchResultRowView._
  import Threading.Implicits.Ui

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = Text

  inflate(R.layout.search_text_result_row)
  setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getResources.getDimensionPixelSize(R.dimen.search__result__height)))
  setOrientation(LinearLayout.HORIZONTAL)

  private lazy val messagesIndexStorage     = inject[Signal[MessageIndexStorage]]
  private lazy val accentColorController    = inject[AccentColorController]
  private lazy val usersController          = inject[UsersController]

  private lazy val contentTextView = ViewUtils.getView(this, R.id.message_content).asInstanceOf[TypefaceTextView]
  private lazy val infoTextView    = ViewUtils.getView(this, R.id.message_info).asInstanceOf[TypefaceTextView]
  private lazy val chatheadView    = ViewUtils.getView(this, R.id.chathead).asInstanceOf[ChatHeadView]
  private lazy val resultsCount    = ViewUtils.getView(this, R.id.search_result_count).asInstanceOf[TypefaceTextView]

  (for {
    mis      <- messagesIndexStorage
    color    <- accentColorController.accentColor
    m        <- message
    q        <- searchedQuery if q.toString().nonEmpty
    nContent <- Signal.future(mis.getNormalizedContentForMessage(m.id))
  } yield (m, q, color, nContent)).onUi {
    case (msg, query, color, Some(normalizedContent)) =>
      val spannableString = CollectionUtils.getHighlightedSpannableString(msg.contentString, normalizedContent, query.elements, ColorUtils.injectAlpha(0.5f, color.color), StartEllipsisThreshold)
      contentTextView.setText(spannableString._1)
      resultsCount.setText(spannableString._2.toString)
      resultsCount.setVisible(spannableString._2 > 1)
    case (msg, _, _, None) =>
      contentTextView.setText(msg.contentString)
      resultsCount.setVisible(false)
    case _ =>
  }

  (for {
    m <- message
    u <- usersController.user(m.userId)
  } yield (m, u)).onUi {
    case (msg, user) =>
      infoTextView.setText(TextViewUtils.getBoldText(getContext, s"[[${user.name}]] ${TimeStamp(msg.time.instant, showWeekday = false).string}"))
      chatheadView.loadUser(msg.userId)
  }

  this.onClick {
    message.head.foreach { m =>
      inject[CollectionController].focusedItem ! Some(m)
      inject[MessageActionsController].onMessageAction ! (MessageAction.Reveal, m)
    }
  }
}

object TextSearchResultRowView {
  val StartEllipsisThreshold = 15
}
