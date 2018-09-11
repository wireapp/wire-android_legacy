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
package com.waz.zclient.cursor

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.text.{Editable, TextUtils, TextWatcher}
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view._
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import android.widget.{LinearLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.model._
import com.waz.service.UserSearchService
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.cursor.CursorController.{EnteredTextSource, KeyboardState}
import com.waz.zclient.messages.MessagesController
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.ui.cursor._
import com.waz.zclient.ui.text.TextTransform
import com.waz.zclient.ui.views.OnDoubleClickListener
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.views.AvailabilityView
import com.waz.zclient.{R, ViewHelper}

class CursorView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
    extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  import CursorView._
  import Threading.Implicits.Ui

  val controller       = inject[CursorController]
  val accentColor      = inject[Signal[AccentColor]]
  val layoutController = inject[IGlobalLayoutController]
  val messages         = inject[MessagesController]

  setOrientation(LinearLayout.VERTICAL)
  inflate(R.layout.cursor_view_content)

  val cursorToolbarFrame = returning(findById[CursorToolbarContainer](R.id.cal__cursor)) { f =>
    val left = getDimenPx(R.dimen.cursor_toolbar_padding_horizontal_edge)
    f.setPadding(left, 0, left, 0)
  }

  val cursorEditText   = findById[CursorEditText]       (R.id.cet__cursor)
  val mainToolbar      = findById[CursorToolbar]        (R.id.c__cursor__main)
  val secondaryToolbar = findById[CursorToolbar]        (R.id.c__cursor__secondary)
  val topBorder        = findById[View]                 (R.id.v__top_bar__cursor)
  val hintView         = findById[TextView]             (R.id.ttv__cursor_hint)
  val dividerView      = findById[View]                 (R.id.v__cursor__divider)
  val emojiButton      = findById[CursorIconButton]     (R.id.cib__emoji)
  val keyboardButton   = findById[CursorIconButton]     (R.id.cib__keyboard)
  val sendButton       = findById[CursorIconButton]     (R.id.cib__send)
  val mentionsList     = findById[RecyclerView]         (R.id.mentions_list)

  val ephemeralButton = returning(findById[EphemeralTimerButton](R.id.cib__ephemeral)) { v =>
    controller.ephemeralBtnVisible.onUi(v.setVisible)

    controller.ephemeralExp.pipeTo(v.ephemeralExpiration)
    inject[ThemeController].darkThemeSet.pipeTo(v.darkTheme)

    v.setOnClickListener(new OnDoubleClickListener() {
      override def onDoubleClick(): Unit =
        controller.toggleEphemeralMode()

      override def onSingleClick(): Unit =
        controller.keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.EPHEMERAL)
    })
  }

  val defaultHintTextColor = hintView.getTextColors.getDefaultColor

  val dividerColor = controller.isEditingMessage.map{
    case true => getColor(R.color.separator_light)
    case _    => dividerView.getBackground.asInstanceOf[ColorDrawable].getColor
  }

  val bgColor = controller.isEditingMessage map {
    case true => getStyledColor(R.attr.cursorEditBackground)
    case false => getStyledColor(R.attr.wireBackgroundColor)
  }

  val lineCount = Signal(1)
  val topBarVisible = for {
    multiline <- lineCount.map(_ > 2)
    typing <- controller.typingIndicatorVisible
    scrolledToBottom <- messages.scrolledToBottom
  } yield
    !typing && (multiline || !scrolledToBottom)

  private val cursorTextWatcher = new MentionsTextWatcher
  private val mentionCandidatesAdapter = new MentionCandidatesAdapter()
  mentionsList.setAdapter(mentionCandidatesAdapter)
  mentionsList.setLayoutManager(new LinearLayoutManager(context))
  mentionCandidatesAdapter.onUserClicked.onUi { info =>
    cursorTextWatcher.createMention(info.userId, info.name, cursorEditText.getEditableText, cursorEditText.getSelectionEnd)
  }

  private val mentionSearchResults = for {
    searchService <- inject[Signal[UserSearchService]]
    query <- cursorTextWatcher.shouldShowMentionList
    convId <- inject[ConversationController].currentConvId
    results <- searchService.searchUsersInConversation(convId, query.getOrElse(""))
  } yield results

  mentionSearchResults.map(_.map(ud => MentionCandidateInfo(ud.id, ud.getDisplayName, ud.handle.getOrElse(Handle())))).onUi {
    mentionCandidatesAdapter.setData
  }

  Signal(cursorTextWatcher.shouldShowMentionList.map(_.nonEmpty), mentionSearchResults.map(_.nonEmpty)).map{
    case (true, true) => true
    case _ => false
  }.onUi(showMentionsList)

  private def showMentionsList(visible: Boolean) = {
    mentionsList.setVisible(visible)
    topBorder.setVisible(visible)
  }

  lineCount.onUi(cursorEditText.setLines(_))

  dividerColor.onUi(dividerView.setBackgroundColor)
  bgColor.onUi(setBackgroundColor)

  emojiButton.menuItem ! Some(CursorMenuItem.Emoji)
  keyboardButton.menuItem ! Some(CursorMenuItem.Keyboard)

  controller.emojiKeyboardVisible.onUi { emojiVisible =>
    emojiButton.setVisible(!emojiVisible)
    keyboardButton.setVisible(emojiVisible)
  }

  emojiButton.onClick {
    controller.keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.EMOJIS)
  }

  keyboardButton.onClick {
    controller.notifyKeyboardVisibilityChanged(true)
  }

  val cursorHeight = getDimenPx(R.dimen.new_cursor_height)

  mainToolbar.cursorItems ! MainCursorItems
  secondaryToolbar.cursorItems ! SecondaryCursorItems

  cursorEditText.addTextChangedListener(new TextWatcher() {

    override def beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int): Unit = ()

    override def onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int): Unit = {
      val text = charSequence.toString
      controller.enteredText ! (text, EnteredTextSource.FromView)
      if (text.trim.nonEmpty) lineCount ! Math.max(cursorEditText.getLineCount, 1)
    }

    override def afterTextChanged(editable: Editable): Unit = ()
  })

  cursorEditText.addTextChangedListener(cursorTextWatcher)

  cursorEditText.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = controller.notifyKeyboardVisibilityChanged(true)
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

  controller.sendButtonEnabled.onUi { enabled =>
    cursorEditText.setImeOptions(if (enabled) EditorInfo.IME_ACTION_NONE else EditorInfo.IME_ACTION_SEND)
  }

  accentColor.map(_.color).onUi(cursorEditText.setAccentColor)

  private lazy val transformer = TextTransform.get(ContextUtils.getString(R.string.single_image_message__name__font_transform))

  (for {
    eph <- controller.isEphemeral
    av <- controller.convAvailability
    name <- controller.conv.map(_.displayName)
  } yield (eph, av, name)).onUi {
    case (true, av, _) =>
      hintView.setText(getString(R.string.cursor__ephemeral_message))
      AvailabilityView.displayLeftOfText(hintView, av, defaultHintTextColor)
    case (false, av, name) if av != Availability.None =>
      val transformedName = transformer.transform(name.split(' ')(0)).toString
      hintView.setText(getString(AvailabilityView.viewData(av).textId, transformedName))
      AvailabilityView.displayLeftOfText(hintView, av, defaultHintTextColor)
    case _ =>
      hintView.setText(getString(R.string.cursor__type_a_message))
      AvailabilityView.hideAvailabilityIcon(hintView)
  }

  (for {
    Some(MessageExpiry(_)) <- controller.ephemeralExp
    Availability.None      <- controller.convAvailability
    ac                     <- accentColor
  } yield ac.color)
    .orElse(Signal.const(defaultHintTextColor))
    .onUi(hintView.setTextColor)

  // allows the controller to "empty" the text field if necessary by resetting the signal.
  // specifying the source guards us from an infinite loop of the view and controller updating each other
  controller.enteredText {
    case (text, EnteredTextSource.FromController) if text != cursorEditText.getText.toString => cursorEditText.setText(text)
    case _ =>
  }

  (controller.isEditingMessage.zip(controller.enteredText) map {
    case (editing, (text, _)) => !editing && text.isEmpty
  }).onUi { hintView.setVisible }

  controller.convIsActive.onUi(this.setVisible)

  //topBarVisible.onUi(topBorder.setVisible)

  controller.onMessageSent.onUi(_ => setText(""))

  controller.isEditingMessage.onChanged.onUi {
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

  def insertText(text: String): Unit =
    cursorEditText.getText.insert(cursorEditText.getSelectionStart, text)

  def hasText: Boolean = !TextUtils.isEmpty(cursorEditText.getText.toString)

  def getText: String = cursorEditText.getText.toString

  def setConversation(): Unit = {
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
