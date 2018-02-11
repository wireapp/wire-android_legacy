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
package com.waz.zclient.usersearch.views

import android.content.Context
import android.graphics.drawable.ShapeDrawable
import android.os.Build
import android.support.annotation.NonNull
import android.text._
import android.text.style.{AbsoluteSizeSpan, ReplacementSpan}
import android.util.AttributeSet
import android.view.ActionMode.Callback
import android.view._
import android.view.inputmethod.{EditorInfo, InputConnection, InputConnectionWrapper}
import com.waz.zclient.R
import com.waz.zclient.common.views.PickableElement
import com.waz.zclient.pages.main.pickuser.UserTokenSpan
import com.waz.zclient.ui.text.SpannableEditText
import com.waz.zclient.utils.ContextUtils._

class PickerSpannableEditText(val context: Context, val attrs: AttributeSet, val defStyle: Int) extends SpannableEditText(context, attrs, defStyle) with TextWatcher {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  implicit val cxt = context

  private var flagNotifyAfterTextChanged = true
  private var hintTextSmallScreen = ""
  private var hintTextSize = 0
  private var lightTheme = false
  private var elements = Set.empty[PickableElement]
  private var hasText = false
  private var callback: PickerSpannableEditText.Callback = _

  Option(attrs).foreach { attrs =>
    val a = getContext.obtainStyledAttributes(attrs, R.styleable.PickUserEditText)
    hintTextSmallScreen = a.getString(R.styleable.PickUserEditText_hintSmallScreen)
    hintTextSize = a.getDimensionPixelSize(R.styleable.PickUserEditText_hintTextSize, 0)
    a.recycle()
  }

  setCustomSelectionActionModeCallback(new Callback { //TODO set null?
    override def onDestroyActionMode(mode: ActionMode) = {}
    override def onCreateActionMode(mode: ActionMode, menu: Menu) = false
    override def onActionItemClicked(mode: ActionMode, item: MenuItem) = false
    override def onPrepareActionMode(mode: ActionMode, menu: Menu) = false
  })

  setLongClickable(false)
  setTextIsSelectable(false)
  addTextChangedListener(this)
  setHintText(getHint)

  def applyLightTheme(lightTheme: Boolean): Unit =
    this.lightTheme = lightTheme

  override protected def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int): Unit = {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    if (!TextUtils.isEmpty(getHint) && !TextUtils.isEmpty(hintTextSmallScreen)) {
      val paint = getPaint
      val hintWidth: Float = paint.measureText(getHint, 0, getHint.length)
      val availableTextSpace: Float = getMeasuredWidth - getPaddingLeft - getPaddingRight
      if (hintWidth > availableTextSpace) {
        setHint(hintTextSmallScreen)
      }
    }
  }

  def setHintText(newHint: CharSequence): Unit = {
    val span: SpannableString = new SpannableString(newHint)
    span.setSpan(new AbsoluteSizeSpan(hintTextSize), 0, newHint.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    setHint(span)
  }

  def setCallback(callback: PickerSpannableEditText.Callback): Unit = {
    this.callback = callback
    super.setCallback(new SpannableEditText.Callback() {
      def onRemovedTokenSpan(id: String): Unit =
        elements.find(_.id == id).foreach(callback.onRemovedTokenSpan)

      def onClick(v: View): Unit = {}
    })
  }

  override protected def setHintCursorSize(cursorDrawable: ShapeDrawable): Unit =
    if (!(hasText || Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) && !(getHint.length() == 0)) {
      val padding = toPx(PickerSpannableEditText.EXTRA_PADDING_DP)
      val textSizeDifferencePx = getTextSize.toInt - hintTextSize
      val bottomPadding = textSizeDifferencePx + padding
      cursorDrawable.setPadding(0, padding, 0, bottomPadding)
    }

  def addElement(element: PickableElement): Unit =
    if (!hasToken(element)) {
      elements += element
      flagNotifyAfterTextChanged = false
      addElementToken(element.id, element.name)
      flagNotifyAfterTextChanged = true
      clearNonSpannableText()
      resetDeleteModeForSpans()
    }

  private def hasToken(element: PickableElement): Boolean =
    Option(element) match {
      case None => false
      case Some(_) =>
        val buffer = getText
        val spans = buffer.getSpans(0, buffer.length, classOf[SpannableEditText.TokenSpan])
        spans.find(_.getId == element.id) match {
          case Some(_) => true
          case _ => false
        }
    }

  def removeElement(element: PickableElement): Unit =
    if (hasToken(element) && removeSpan(element.id)) {
      elements -= element
      resetDeleteModeForSpans()
    }

  def getElements: Set[PickableElement] =
    elements

  def reset(): Unit = {
    clearNonSpannableText()
    setText("")
  }

  def getSearchFilter: String =
    getNonSpannableText

  override def onCreateInputConnection(@NonNull outAttrs: EditorInfo): InputConnection = {
    val conn: InputConnection = super.onCreateInputConnection(outAttrs)
    outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION
    new CustomInputConnection(conn, true)
  }

  private def addElementToken(userId: String, userName: String): Unit = {
    val context: Context = getContext
    val lineWidth: Int = getMeasuredWidth - getPaddingLeft - getPaddingRight
    val userTokenSpan: UserTokenSpan = new UserTokenSpan(userId, userName, context, false, lineWidth)
    userTokenSpan.setDeleteModeTextColor(getAccentColor)
    if (lightTheme) {
      userTokenSpan.setTextColor(getColor(R.color.text__primary_light)(context))
    }
    appendSpan(userTokenSpan)
    setSelection(getText.length)
  }

  private def moveTypedTextToEnd(start: Int, before: Int, count: Int): Unit = {
    val buffer = getText
    val allSpans = buffer.getSpans(0, buffer.length, classOf[ReplacementSpan])

    allSpans.headOption match {
      case Some(span) =>
        if (start < buffer.getSpanStart(span) && before == 0) {
          buffer.delete(start, start + count)
          append(buffer.toString.substring(start, start + count))
          setSelection(getText.length)
        }
      case _ => //
    }
  }

  private def removeSelectedElementToken(): Boolean = {
    val buffer = getText
    val spans = buffer.getSpans(0, buffer.length, classOf[UserTokenSpan])
    spans.find(_.getDeleteMode) match {
      case Some(span) =>
        super.removeSpan(span)
        elements = elements.filterNot(_.id == span.getId)
        true
      case _ => false
    }
  }

  private def deleteSpanBeforeSelection(): Boolean = {
    val buffer = getText
    val selectionEnd: Int = getSelectionEnd
    val spans = buffer.getSpans(getSelectionStart, selectionEnd, classOf[SpannableEditText.TokenSpan])

    spans.reverse.find { span =>
      val end = buffer.getSpanEnd(span)
      val atLineBreak = getLayout.getLineForOffset(end) != getLayout.getLineForOffset(selectionEnd)
      end <= selectionEnd || (end <= (selectionEnd + 1) && atLineBreak)
    } match {
      case Some(span) =>
        super.removeSpan(span)
        elements = elements.filterNot(_.id == span.getId)
        setSelection(getText.length)
        true
      case _ => false
    }
  }


  def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {}

  override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit =
    moveTypedTextToEnd(start, before, count)

  def afterTextChanged(s: Editable): Unit =
    if (notifyTextWatcher) {
      if (flagNotifyAfterTextChanged) callback.afterTextChanged(getSearchFilter)
      val hadText = hasText
      hasText = s.length > 0
      if (hadText && !hasText || !hadText && hasText) updateCursor()
    }

  private class CustomInputConnection (val target: InputConnection, val mutable: Boolean) extends InputConnectionWrapper(target, mutable) {
    override def deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean = {
      sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)) && sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }

    override def sendKeyEvent(event: KeyEvent): Boolean =
      if (event.getAction == KeyEvent.ACTION_DOWN && event.getKeyCode == KeyEvent.KEYCODE_DEL &&
        (removeSelectedElementToken() || deleteSpanBeforeSelection())) true
      else super.sendKeyEvent(event)
  }

}

object PickerSpannableEditText {
  val EXTRA_PADDING_DP: Int = 2

  trait Callback {
    def onRemovedTokenSpan(element: PickableElement): Unit

    def afterTextChanged(s: String): Unit
  }

}
