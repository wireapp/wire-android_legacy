package com.waz.zclient.notifications.controllers

final case class Span(style: Int, range: Int, offset: Int = 0)

object Span {
  val ForegroundColorSpanBlack = 1
  val ForegroundColorSpanGray  = 2
  val StyleSpanBold            = 3
  val StyleSpanItalic          = 4

  val HeaderRange = 1
  val BodyRange   = 2
}
