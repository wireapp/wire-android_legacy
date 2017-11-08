/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.cursor

import android.content.Context
import android.content.res.ColorStateList
import android.graphics._
import android.graphics.drawable.ColorDrawable
import android.support.v4.content.res.ResourcesCompat
import android.text.{Editable, TextUtils, TextWatcher}
import android.util.AttributeSet
import android.view._
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import android.widget.{LinearLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.api._
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.ViewHelper
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController
import com.waz.zclient.cursor.CursorController.KeyboardState
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.R
import com.waz.zclient.messages.MessagesController
import com.waz.zclient.ui.cursor._
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.ui.utils.CursorUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._

class CursorView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
    extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  import CursorView._
  import Threading.Implicits.Ui

  val controller = inject[CursorController]
  val accentColor = inject[Signal[AccentColor]]
  val layoutController = inject[IGlobalLayoutController]
  val messages = inject[MessagesController]

  setOrientation(LinearLayout.VERTICAL)
  inflate(R.layout.cursor_view_content)

  val cursorToolbarFrame: CursorToolbarContainer = findById(R.id.cal__cursor)
  val cursorEditText: CursorEditText             = findById(R.id.cet__cursor)
  val mainToolbar: CursorToolbar                 = findById(R.id.c__cursor__main)
  val secondaryToolbar: CursorToolbar            = findById(R.id.c__cursor__secondary)
  val topBorder: View                            = findById(R.id.v__top_bar__cursor)
  val hintView: TextView                         = findById(R.id.ttv__cursor_hint)
  val dividerView: View                          = findById(R.id.v__cursor__divider)
  val emojiButton: CursorIconButton              = findById(R.id.cib__emoji)
  val keyboardButton: CursorIconButton           = findById(R.id.cib__keyboard)
  val sendButton: CursorIconButton               = findById(R.id.cib__send)
  val ephemeralButton: CursorIconButton          = findById(R.id.cib__ephemeral)

  lazy val editBackgroundColor = getStyledColor(R.attr.cursorEditBackground)
  val defaultTextColor = cursorEditText.getCurrentTextColor
  val defaultDividerColor =  dividerView.getBackground.asInstanceOf[ColorDrawable].getColor
  val defaultHintTextColor = hintView.getTextColors.getDefaultColor

  val dividerColor = Signal(controller.isEditingMessage, controller.convIsEphemeral, accentColor) map {
    case (true, _, _)      => getColor(R.color.separator_light)
    case (_, true, accent) => accent.getColor
    case _                 => defaultDividerColor
  }

  val bgColor = controller.isEditingMessage map {
    case true => editBackgroundColor
    case false => Color.TRANSPARENT
  }

  val cursorBtnColor = controller.convIsEphemeral.zip(controller.isEditingMessage) flatMap {
    case (true, false) =>
      accentColor.map(_.getColor).map(ColorStateList.valueOf)
    case _ if ThemeUtils.isDarkTheme(getContext) =>
      Signal.const(ResourcesCompat.getColorStateList(getResources, R.color.wire__text_color_primary_dark_selector, null))
    case _ =>
      Signal.const(ResourcesCompat.getColorStateList(getResources, R.color.wire__text_color_primary_light_selector, null))
  }

  val lineCount = Signal(0)
  val topBarVisible = for {
    multiline <- lineCount.map(_ > 2)
    typing <- controller.typingIndicatorVisible
    scrolledToBottom <- messages.scrolledToBottom
  } yield
    !typing && (multiline || !scrolledToBottom)

  dividerColor.on(Threading.Ui) { dividerView.setBackgroundColor }
  bgColor.on(Threading.Ui) { setBackgroundColor }
  cursorBtnColor.on(Threading.Ui) { c =>
    emojiButton.setTextColor(c)
    keyboardButton.setTextColor(c)
    mainToolbar.setButtonsColor(c)
    secondaryToolbar.setButtonsColor(c)
  }

  emojiButton.menuItem ! Some(CursorMenuItem.Emoji)
  keyboardButton.menuItem ! Some(CursorMenuItem.Keyboard)

  controller.emojiKeyboardVisible.on(Threading.Ui) { emojiVisible =>
    emojiButton.setVisible(!emojiVisible)
    keyboardButton.setVisible(emojiVisible)
  }

  emojiButton.onClick {
    controller.keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.EMOJIS)
  }
  keyboardButton.onClick {
    controller.keyboard ! KeyboardState.Shown
  }

  val cursorHeight = getDimenPx(R.dimen.new_cursor_height)

  mainToolbar.cursorItems ! MainCursorItems
  secondaryToolbar.cursorItems ! SecondaryCursorItems

  cursorEditText.addTextChangedListener(new TextWatcher() {
    private var text = ""

    override def onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int): Unit = {
      text = charSequence.toString
    }

    override def afterTextChanged(editable: Editable): Unit = {
      controller.enteredText ! text
      if (text.trim.nonEmpty) lineCount ! cursorEditText.getLineCount
      text = ""
    }

    override def beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int): Unit = ()
  })

  cursorEditText.setOnEditorActionListener(new OnEditorActionListener {
    override def onEditorAction(textView: TextView, actionId: Int, event: KeyEvent): Boolean = {
      if (actionId == EditorInfo.IME_ACTION_SEND ||
        (cursorEditText.getImeOptions == EditorInfo.IME_ACTION_SEND &&
          event != null &&
          event.getKeyCode == KeyEvent.KEYCODE_ENTER &&
          event.getAction == KeyEvent.ACTION_DOWN)) {
        controller.submit(textView.getText.toString)
      } else
        false
    }
  })
  cursorEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
    override def onFocusChange(view: View, hasFocus: Boolean): Unit = controller.editHasFocus ! hasFocus
  })
  cursorEditText.setFocusableInTouchMode(true)

  controller.sendButtonEnabled.on(Threading.Ui) { enabled =>
    cursorEditText.setImeOptions(if (enabled) EditorInfo.IME_ACTION_NONE else EditorInfo.IME_ACTION_SEND)
  }
  accentColor.on(Threading.Ui) { accent =>
    cursorEditText.setAccentColor(accent.getColor)
  }

  val menuLeftMargin = controller.cursorWidth map { w =>
    if ((LayoutSpec.get(getContext) eq LayoutSpec.LAYOUT_PHONE) || (LayoutSpec.get(getContext) eq LayoutSpec.LAYOUT_KINDLE)) {
      getDimenPx(R.dimen.cursor_toolbar_padding_horizontal_edge)
    } else {
      CursorUtils.getCursorMenuLeftMargin(getContext, w)
    }
  }
  menuLeftMargin { left =>
    cursorToolbarFrame.setPadding(left, 0, left, 0)
  }

  val anchorPositionPx2 = controller.cursorWidth map { w =>
    if ((LayoutSpec.get(getContext) eq LayoutSpec.LAYOUT_PHONE) || (LayoutSpec.get(getContext) eq LayoutSpec.LAYOUT_KINDLE)) {
      0
    } else {
      CursorUtils.getCursorEditTextAnchorPosition(getContext, w)
    }
  }
  anchorPositionPx2 { pos =>
    hintView.setTranslationX(pos)
  }


  val hintText = controller.convIsEphemeral map {
    case true => R.string.cursor__ephemeral_message
    case false => R.string.cursor__type_a_message
  }

  val hintColor = controller.convIsEphemeral.zip(accentColor) map {
    case (true, accent) => accent.getColor
    case (false, _) => defaultHintTextColor
  }

  val hintVisible = controller.isEditingMessage.zip(controller.enteredText) map {
    case (editing, text) => !editing && text.isEmpty
  }

  controller.convIsActive.on(Threading.Ui) { this.setVisible }

  hintText.on(Threading.Ui) { hintView.setText }
  hintColor.on(Threading.Ui) { hintView.setTextColor }
  hintVisible.on(Threading.Ui) { hintView.setVisible }

  topBarVisible.on(Threading.Ui) { topBorder.setVisible }

  controller.onMessageSent.on(Threading.Ui) { _ => setText("") }

  controller.isEditingMessage.onChanged.on(Threading.Ui) {
    case false => setText("")
    case true =>
      controller.editingMsg.head foreach {
        case Some(msg) => setText(msg.contentString)
        case _ => // ignore
      }
  }

  controller.onEditMessageReset.onUi { _ =>
    controller.editingMsg.head.map {
      case Some(msg) =>  setText(msg.contentString)
      case _ =>
    } (Threading.Ui)
  }

  def enableMessageWriting(): Unit = cursorEditText.requestFocus

  def setCallback(callback: CursorCallback) = controller.cursorCallback = Option(callback)

  def setText(text: String): Unit = {
    cursorEditText.setText(text)
    cursorEditText.setSelection(text.length)
  }

  def insertText(text: String): Unit = {
    cursorEditText.getText.insert(cursorEditText.getSelectionStart, text)
  }

  def notifyKeyboardVisibilityChanged(keyboardIsVisible: Boolean, currentFocus: View): Unit = {
    controller.keyboard.mutate {
      case KeyboardState.Shown if !keyboardIsVisible => KeyboardState.Hidden
      case _ if keyboardIsVisible => KeyboardState.Shown
      case state => state
    }

    if (keyboardIsVisible && cursorEditText.hasFocus)
      controller.cursorCallback.foreach(_.onCursorClicked())
  }

  def hasText: Boolean = !TextUtils.isEmpty(cursorEditText.getText.toString)

  def getText: String = cursorEditText.getText.toString

  def setConversation(conversation: IConversation): Unit = {
    enableMessageWriting()
    controller.editingMsg ! None
    controller.secondaryToolbarVisible ! false
  }

  def isEditingMessage: Boolean = controller.isEditingMessage.currentValue.contains(true)

  def closeEditMessage(animated: Boolean): Unit = controller.editingMsg ! None

  def onExtendedCursorClosed(): Unit =
    controller.keyboard.mutate {
      case KeyboardState.ExtendedCursor(_) => KeyboardState.Hidden
      case state => state
    }

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    super.onLayout(changed, l, t, r, b)

    controller.cursorWidth ! (r - l)
  }
}

object CursorView {
  import CursorMenuItem._

  private val MainCursorItems = Seq(Camera, VideoMessage, Sketch, Gif, AudioMessage, More)
  private val SecondaryCursorItems = Seq(Ping, File, Location, Dummy, Dummy, Less)
}
