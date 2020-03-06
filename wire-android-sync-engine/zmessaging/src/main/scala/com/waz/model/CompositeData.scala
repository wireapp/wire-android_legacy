package com.waz.model

import com.waz.model.GenericContent.{Button, Text}

class CompositeData(items: Seq[CompositeMessageItem],
                    expectsReadConfirmation: Option[Boolean],
                    legalHoldStatus: Option[Int])

sealed class CompositeMessageItem
case class TextItem(text: Text) extends CompositeMessageItem
case class ButtonItem(button: Button) extends CompositeMessageItem
object UnknownItem extends CompositeMessageItem
