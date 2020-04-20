package com.waz.zclient.messages.parts.composite

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.waz.model.MessageContent
import com.waz.service.messages.MessageAndLikes
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.parts.TextPartView
import com.waz.zclient.messages.ClickableViewPart
import com.waz.zclient.messages.{MessagesController, MsgPart}
import com.waz.zclient.{Injectable, R, ViewHelper}

class CompositePartView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with Injectable
    with ViewHelper
    with ClickableViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  override val tpe: MsgPart = MsgPart.Composite

  setOrientation(LinearLayout.VERTICAL)
  inflate(R.layout.layout_composite_message)

  private lazy val textPartView = findById[TextPartView](R.id.composite_message_textpartview)
  private lazy val buttonContainerView = findById[ButtonContainerView](R.id.composite_message_button_container)

  private lazy val controller = inject[MessagesController]

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions] = None): Unit = {
    textPartView.set(msg, part, opts)
    buttonContainerView.bindMessage(msg.message.id)

    buttonContainerView.selectedButtonId.onUi {
      case (messageId, buttonId) => controller.clickButton(messageId, buttonId)
    }
  }
}
