package com.waz.zclient.messages.parts.composite

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import com.waz.api.Message
import com.waz.model.MessageContent
import com.waz.service.messages.MessageAndLikes
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.parts.TextPartView
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.{Injectable, R, ViewHelper}

class CompositePartView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with Injectable
    with ViewHelper
    with MessageViewPart {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  setOrientation(LinearLayout.VERTICAL)
  inflate(R.layout.layout_composite_message)

  private lazy val textContainer = findById[ViewGroup](R.id.composite_message_text_container)
  private lazy val buttonContainerView = findById[ButtonContainerView](R.id.composite_message_button_container)

  override def tpe = MsgPart.Composite

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions] = None): Unit = {
    val messageData = msg.message

    textContainer.removeAllViews()
    messageData.content.foreach { msgContent =>
      val textPartView = inflate(R.layout.message_text, textContainer, false).asInstanceOf[TextPartView]
      textContainer.addView(textPartView)

      val textMessageData = messageData.copy(
        msgType = Message.Type.TEXT,
        content = Seq(msgContent),
        protos = Seq.empty,
        firstMessage = false,
        assetId = None,
        forceReadReceipts = None
      )
      val textMessageAndLikes = msg.copy(message = textMessageData)

      textPartView.set(textMessageAndLikes, None, None)
    }

    buttonContainerView.bindMessage(messageData.id)
  }
}
