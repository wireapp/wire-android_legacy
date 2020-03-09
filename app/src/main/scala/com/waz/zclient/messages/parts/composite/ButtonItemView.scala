package com.waz.zclient.messages.parts.composite

import android.content.Context
import android.util.AttributeSet
import android.view.{View, ViewGroup}
import android.view.View.OnClickListener
import android.widget.{Button, LinearLayout, ProgressBar, TextView}
import com.waz.utils.events.EventStream
import com.waz.zclient.{R, ViewHelper}

class ButtonItemView(context: Context, attrs: AttributeSet, style: Int)
    extends LinearLayout(context, attrs, style)
    with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  setOrientation(LinearLayout.VERTICAL)
  inflate(R.layout.message_button)

  private lazy val buttonContainer: ViewGroup = getChildAt(0).asInstanceOf[ViewGroup]
  private lazy val button: Button = buttonContainer.getChildAt(1).asInstanceOf[Button]
  private lazy val progressBar: ProgressBar = buttonContainer.getChildAt(0).asInstanceOf[ProgressBar]
  private lazy val errorText: TextView = getChildAt(1).asInstanceOf[TextView]

  val selected = EventStream[Unit]()

  button.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      setWaiting()
      selected ! {}
    }
  })

  def setError(error: String): Unit = {
    errorText.setText(error)
    errorText.setVisibility(View.VISIBLE)
  }

  def clearError(): Unit = {
    errorText.setText(null)
    errorText.setVisibility(View.GONE)
  }

  def setConfirmed(): Unit = {
    progressBar.setVisibility(View.GONE)
    //TODO: highlight button
  }

  def setWaiting(): Unit = {
    progressBar.setVisibility(View.VISIBLE)
  }

  def setUnselected(): Unit = {
    progressBar.setVisibility(View.GONE)
    //TODO: remove highlight
  }

  def setButton(uiModel: ButtonItemViewUIModel): Unit = {
    button.setText(uiModel.title)
    button.setContentDescription(uiModel.title)
    uiModel.error.fold(clearError())(setError)
    //TODO: set button state (confirmed, waiting, cleared)
  }
}

//TODO: add state
class ButtonItemViewUIModel(val title: String, val error: Option[String])
