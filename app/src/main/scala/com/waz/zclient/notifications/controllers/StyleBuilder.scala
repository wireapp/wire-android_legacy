package com.waz.zclient.notifications.controllers

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Style
import com.waz.utils.returning
import com.waz.zclient.utils.format

final case class StyleBuilder(style: Int,
                              title: SpannableWrapper,
                              summaryText: Option[String] = None,
                              bigText: Option[SpannableWrapper] = None,
                              lines: List[SpannableWrapper] = List.empty) {
  override def toString: String =
    format(className = "StyleBuilder", oneLiner = true,
      "style"       -> Some(style),
      "title"       -> Some(title),
      "summaryText" -> summaryText,
      "bigText"     -> bigText,
      "lines"       -> (if (lines.nonEmpty) Some(lines) else None)
    )

  def build(implicit cxt: Context): Style = style match {
    case StyleBuilder.BigText =>
      returning(new NotificationCompat.BigTextStyle) { bts =>
        bts.setBigContentTitle(title.build)
        summaryText.foreach(bts.setSummaryText)
        bigText.map(_.build).foreach(bts.bigText(_))
      }
    case StyleBuilder.Inbox =>
      returning(new NotificationCompat.InboxStyle) { is =>
        is.setBigContentTitle(title.build)
        summaryText.foreach(is.setSummaryText)
        lines.map(_.build).foreach(is.addLine(_))
      }
  }
}

object StyleBuilder {
  val BigText = 1
  val Inbox   = 2
}
