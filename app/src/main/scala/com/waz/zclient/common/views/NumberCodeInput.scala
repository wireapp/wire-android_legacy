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
package com.waz.zclient.common.views

import android.app.Activity
import android.content.{ClipData, ClipboardManager, Context}
import android.content.res.ColorStateList
import android.os.Build
import android.text.{Editable, TextWatcher}
import android.util.AttributeSet
import android.view.View.OnTouchListener
import android.view._
import android.widget._
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.ui.cursor.CursorEditText
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.{ContextUtils, _}
import com.waz.zclient.{R, ViewHelper}

import scala.concurrent.Future

class NumberCodeInput(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper { self =>
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.code_input)

  val inputCount = 6
  val errorText = findById[TypefaceTextView](R.id.empty_search_message)
  val progressBar = findById[ProgressBar](R.id.progress_bar)
  val editText = findById[CursorEditText](R.id.code_edit_text)
  val texts = Seq(R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5, R.id.t6).map(findById[TypefaceTextView])
  val container = findById[LinearLayout](R.id.container)
  val codeText = Signal[(String, Boolean)](("", false))
  private var onCodeSet = (_: (String, Boolean)) => Future.successful(Option.empty[String])

  progressBar.setIndeterminate(true)
  progressBar.setVisible(false)
  errorText.setVisible(false)
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
    progressBar.setIndeterminateTintList(ColorStateList.valueOf(ContextUtils.getColor(R.color.teams_inactive_button)))
  setupInputs()
  codeText.onUi {
    case (code, copyPaste) =>
      errorText.setVisible(false)
      if (code.length >= inputCount)
        setCode(code, copyPaste)
  }

  def inputCode(code: String): Unit = {
    editText.getText.replace(0, editText.length(), code)
  }

  def requestInputFocus(): Unit = editText.requestFocus()

  private def setupInputs(): Unit = {
    editText.addTextChangedListener(new TextWatcher {
      override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit = {
        val content = s.toString.toCharArray.map(_.toString)
        texts.zipWithIndex.foreach {
          case (textView, i) =>
            textView.setText(content.applyOrElse[Int, String](i, _ => ""))
        }
        codeText ! (s.toString, count == inputCount)
      }
      override def afterTextChanged(s: Editable): Unit = {}
      override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {}
    })
    container.setOnTouchListener(new OnTouchListener {
      override def onTouch(v: View, event: MotionEvent): Boolean = {
        event.getAction match {
          case MotionEvent.ACTION_UP =>
            editText.requestFocus()
            KeyboardUtils.showKeyboard(context.asInstanceOf[Activity])
            getClipboardCode.foreach { text => editText.getText.replace(0, editText.length(), text) }
          case _ =>
        }
        false
      }
    })

  }

  def getCode: String = texts.map(_.getText.toString).mkString

  def setOnCodeSet(f: ((String, Boolean)) => Future[Option[String]]): Unit = onCodeSet = f

  private def setCode(code: String, copyPaste: Boolean): Unit = {
    progressBar.setVisible(true)
    errorText.setVisible(false)
    onCodeSet(code, copyPaste).map {
      case Some(error) =>
        errorText.setVisible(true)
        errorText.setText(error.toUpperCase)
        progressBar.setVisible(false)
        editText.setFocusable(true)
        editText.requestFocus()
      case _ =>
    } (Threading.Ui)
  }

  private def getClipboardCode: Option[String] = {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
    val clipOpt = if (clipboardManager.hasPrimaryClip) Option(clipboardManager.getPrimaryClip) else None
    val clipItem = clipOpt.flatMap(clip => if (clip.getItemCount > 0) Some(clip.getItemAt(0)) else Option.empty[ClipData.Item])
    clipItem.map(_.coerceToText(context).toString).filter(_.matches("^[0-9]{6}$"))
  }
}
