package com.waz.zclient.messages.parts.composite

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.waz.zclient.messages.MessagesController
import com.waz.zclient.{Injectable, ViewHelper}

//TODO: implement MessageViewPart
class CompositePartView(context: Context, attrs: AttributeSet, style: Int)
    extends LinearLayout(context, attrs, style)
    with Injectable
    with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private lazy val messagesService = inject[MessagesController]

  setOrientation(LinearLayout.VERTICAL)

  //TODO: convert MessageData to Text & get buttons from messagesService

}
