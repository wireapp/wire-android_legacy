package com.waz.zclient.messages.parts.composite

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

//TODO: implement MessageViewPart
class CompositePartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  setOrientation(LinearLayout.VERTICAL)

  //TODO: convert MessageData to Text & Buttons

}
