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
package com.waz.zclient.calling

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.ImageView
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.events._
import com.waz.zclient.ViewHelper.inflate
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.controllers.CallController.CallParticipantInfo
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.views.SingleUserRowView
import com.waz.zclient.paintcode.ForwardNavigationIcon
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.{getColor, getDrawable, getString}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{Injectable, Injector, R}

class CallParticipantsAdapter(implicit context: Context, eventContext: EventContext, inj: Injector)
  extends RecyclerView.Adapter[ViewHolder]
    with Injectable
    with DerivedLogTag {

  import CallParticipantsAdapter._

  private var items = Seq.empty[CallParticipantInfo]
  private var numOfParticipants = 0
  private var maxRows = Option.empty[Int]
  private var theme: Theme = Theme.Dark

  val onShowAllClicked = EventStream[Unit]()

  val callController  = inject[CallController]
  val themeController = inject[ThemeController]

  def setMaxRows(maxRows: Int): Unit = {
    this.maxRows =
      if (maxRows > 0) Some(maxRows)
      else if (maxRows == 0) Some(1)  // we try to show the "Show all" button anyway
      else None
    notifyDataSetChanged()
  }

  callController.participantInfos().onUi { v =>
    numOfParticipants = v.size
    items = maxRows.filter(_ < numOfParticipants).fold(v)(m => v.take(m - 1))
    notifyDataSetChanged()
  }

  callController.theme.onUi { theme =>
    this.theme = theme
    notifyDataSetChanged()
  }

  override def getItemViewType(position: Int): Int =
    if (maxRows.contains(position + 1) && maxRows.exists(_ < numOfParticipants)) ShowAll
    else UserRow

  override def getItemCount: Int = maxRows match {
    case Some(mr) if mr < numOfParticipants => mr
    case _                                  => items.size
  }

  override def getItemId(position: Int): Long =
    if (maxRows.contains(position) && maxRows.exists(_ < numOfParticipants)) 0
    else items.lift(position).map(_.userId.hashCode().toLong).getOrElse(0)

  setHasStableIds(true)

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = holder match {
    case h: CallParticipantViewHolder => h.bind(items(position), theme)
    case h: ShowAllButtonViewHolder   => h.bind(numOfParticipants, theme)
    case _ =>
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = viewType match {
    case UserRow =>
      CallParticipantViewHolder(inflate[SingleUserRowView](R.layout.single_user_row, parent, addToParent = false))
    case ShowAll =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.list_options_button, parent, false)
      view.onClick(onShowAllClicked ! {})
      ShowAllButtonViewHolder(view)
  }
}

object CallParticipantsAdapter {
  val UserRow = 0
  val ShowAll = 1
}

case class CallParticipantViewHolder(view: SingleUserRowView) extends ViewHolder(view) {
  def bind(callParticipantInfo: CallParticipantInfo, theme: Theme): Unit = {
    view.setCallParticipantInfo(callParticipantInfo)
    view.setTheme(theme, background = false)
    view.setSeparatorVisible(false)
  }
}

case class ShowAllButtonViewHolder(view: View) extends ViewHolder(view) {
  private implicit val ctx: Context = view.getContext
  view.findViewById[ImageView](R.id.next_indicator).setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
  view.setMarginTop(0)
  private lazy val nameView = view.findViewById[TypefaceTextView](R.id.name_text)

  def bind(numOfParticipants: Int, theme: Theme): Unit = {
    nameView.setText(getString(R.string.show_all_participants, numOfParticipants.toString))
    setTheme(theme)
  }

  private def setTheme(theme: Theme): Unit = {
    val color = if (theme == Theme.Light) R.color.wire__text_color_primary_light_selector else R.color.wire__text_color_primary_dark_selector
    nameView.setTextColor(getColor(color))
    view.setBackground(getDrawable(R.drawable.selector__transparent_button))
  }
}
