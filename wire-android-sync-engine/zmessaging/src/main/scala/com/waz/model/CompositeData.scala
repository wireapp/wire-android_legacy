package com.waz.model

import com.waz.model.GenericContent.{Button, Text}

case class CompositeData(items:                   Seq[CompositeMessageItem],
                         expectsReadConfirmation: Option[Boolean],
                         legalHoldStatus:         Option[Int])

sealed trait CompositeMessageItem
case class TextItem(text: Text)       extends CompositeMessageItem
case class ButtonItem(button: Button) extends CompositeMessageItem
case object UnknownItem               extends CompositeMessageItem
