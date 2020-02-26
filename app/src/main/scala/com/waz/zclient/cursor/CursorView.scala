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
import android.graphics.{Color, Rect}
import android.text.InputType._
import android.text.method.TransformationMethod
import android.text._
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view._
import android.view.inputmethod.EditorInfo._
import android.widget.TextView.OnEditorActionListener
import android.widget.{EditText, LinearLayout, TextView}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.UserSearchService
import com.waz.threading.Threading
import com.waz.utils.events.{Signal, SourceSignal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.conversation.{ConversationController, ReplyController}
import com.waz.zclient.cursor.CursorController.{EnteredTextSource, KeyboardState}
import com.waz.zclient.cursor.MentionUtils.{Replacement, getMention}
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.ui.cursor.CursorEditText.OnBackspaceListener
import com.waz.zclient.ui.cursor._
import com.waz.zclient.ui.text.TypefaceEditText.OnSelectionChangedListener
import com.waz.zclient.ui.views.OnDoubleClickListener
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.views.AvailabilityView
import com.waz.zclient.{R, ViewHelper}

class CursorView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends LinearLayout(context, attrs, defStyleAttr)
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  import CursorView._
  import Threading.Implicits.Ui

  private lazy val accentColor     = inject[Signal[AccentColor]]
  private lazy val controller      = inject[CursorController]
  private lazy val replyController = inject[ReplyController]

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

  val lineCount = Signal(getInt(R.integer.cursor_starting_lines))

  Signal(controller.typingIndicatorVisible, replyController.currentReplyContent)
    .map { case (typing, currentReply) => !typing && currentReply.isEmpty  }
    .onUi(topBorder.setVisible)

  private val cursorSpanWatcher = new MentionSpanWatcher
  private val cursorText: SourceSignal[String] = Signal(cursorEditText.getEditableText.toString)
  private val cursorSelection: SourceSignal[(Int, Int)] = Signal((cursorEditText.getSelectionStart, cursorEditText.getSelectionEnd))

  val mentionQuery = Signal(cursorText, cursorSelection).collect {
    case (text, (_, sEnd)) if sEnd <= text.length => MentionUtils.mentionQuery(text, sEnd)
  }
  val selectionHasMention = Signal(cursorText, cursorSelection).collect {
    case (text, (_, sEnd)) if sEnd <= text.length =>
      MentionUtils.mentionMatch(text, sEnd).exists { m =>
        CursorMentionSpan.hasMentionSpan(cursorEditText.getEditableText, m.start, sEnd)
      }
  }
  val cursorSingleSelection = cursorSelection.map(s => s._1 == s._2)
  val mentionSearchResults = for {
    searchService <- inject[Signal[UserSearchService]]
    convId <- inject[ConversationController].currentConvId
    query <- mentionQuery
    selectionHasMention <- selectionHasMention
    selectionSingle <- cursorSingleSelection
    results <- if (selectionHasMention || !selectionSingle)
      Signal.const(IndexedSeq.empty[UserData])
    else
      searchService.mentionsSearchUsersInConversation(convId, query.getOrElse(""), includeSelf = false)
  } yield results.reverse

  def createMention(userId: UserId, name: String, editText: EditText, selectionIndex: Int, accentColor: Int): Unit = {
    val editable = editText.getEditableText
    getMention(editable.toString, selectionIndex, userId, name).foreach {
      case (mention, Replacement(rStart, rEnd, rText)) =>
        editable.replace(rStart, rEnd, CursorMentionSpan.PlaceholderChar + " ")
        editable.setSpan(
          CursorMentionSpan(userId, rText, accentColor),
          mention.start,
          mention.start + 1,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        editText.setSelection(mention.start + 2)
        controller.enteredText ! (getText, EnteredTextSource.FromView)
    }
  }

  lineCount.onUi { lineCount =>
    if (lineCount.<(getInt(R.integer.cursor__max_lines))) {
      cursorEditText.setLines(lineCount)
    }
  }

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

  cursorEditText.setOnSelectionChangedListener(new OnSelectionChangedListener {
    override def onSelectionChanged(selStart: Int, selEnd: Int): Unit =
      cursorSelection ! (selStart, selEnd)
  })

  cursorEditText.addTextChangedListener(new TextWatcher() {

    override def beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int): Unit = {
      val editable = cursorEditText.getEditableText
      editable.removeSpan(cursorSpanWatcher)
      editable.setSpan(cursorSpanWatcher, 0, editable.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }

    override def onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int): Unit = {
      val text = charSequence.toString
      controller.enteredText ! (getText, EnteredTextSource.FromView)
      if (text.trim.nonEmpty && cursorEditText.getLineCount >= getInt(R.integer.cursor_starting_lines)) lineCount ! cursorEditText.getLineCount else {
        lineCount ! getInt(R.integer.cursor_starting_lines)
      }
      cursorText ! text
    }

    override def afterTextChanged(editable: Editable): Unit = {}
  })

  cursorEditText.setBackspaceListener(new OnBackspaceListener {

    //XXX: This is a bit ugly...
    var hasSelected = false
    override def onBackspace(): Boolean = {
      val sStart = cursorEditText.getSelectionStart
      val sEnd = cursorEditText.getSelectionEnd
      val mentionAtSelection = CursorMentionSpan.getMentionSpans(cursorEditText.getEditableText).find(_._3 == sEnd)
      mentionAtSelection match {
        case Some((_, s, e)) if hasSelected =>
          cursorEditText.getEditableText.replace(s, e, "")
          hasSelected = false
          true
        case Some(_) if sStart == sEnd && !hasSelected =>
          cursorEditText.post(new Runnable {
            override def run(): Unit = {
              mentionAtSelection.foreach(m => cursorEditText.setSelection(m._2, m._3))
              hasSelected = true
            }
          })
          true
        case None =>
          hasSelected = false
          false
      }
    }
  })

  cursorEditText.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = controller.notifyKeyboardVisibilityChanged(true)
  })

  cursorEditText.setOnEditorActionListener(new OnEditorActionListener {
    override def onEditorAction(textView: TextView, actionId: Int, event: KeyEvent): Boolean = {
      if (actionId == IME_ACTION_SEND ||
        (cursorEditText.getImeOptions == IME_ACTION_SEND &&
          event != null &&
          event.getKeyCode == KeyEvent.KEYCODE_ENTER &&
          event.getAction == KeyEvent.ACTION_DOWN)) {
        val cursorText = getText
        controller.submit(cursorText.text, cursorText.mentions)
      } else
        false
    }
  })

  controller.editHasFocus ! cursorEditText.hasFocus
  cursorEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
    override def onFocusChange(view: View, hasFocus: Boolean): Unit = controller.editHasFocus ! hasFocus
  })

  cursorEditText.setTransformationMethod(new TransformationMethod() {
    override def getTransformation(source: CharSequence, view: View): CharSequence = {
      source
    }

    override def onFocusChanged(view: View, sourceText: CharSequence, focused: Boolean, direction: Int, previouslyFocusedRect: Rect): Unit = ()
  })

  cursorEditText.setFocusableInTouchMode(true)

  cursorEditText.addInputType(TYPE_TEXT_FLAG_MULTI_LINE | TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_NORMAL)

  controller.keyboardPrivateMode.onUi(cursorEditText.setPrivateMode)

  controller.sendButtonEnabled.onUi {
    case true =>
      cursorEditText.addImeOption(IME_ACTION_NONE)
      cursorEditText.removeImeOption(IME_ACTION_SEND)
    case false =>
      cursorEditText.removeImeOption(IME_ACTION_NONE)
      cursorEditText.addImeOption(IME_ACTION_SEND)
  }

  accentColor.map(_.color).onUi(cursorEditText.setAccentColor)

  (for {
    eph  <- controller.isEphemeral
    av   <- controller.convAvailability
    name <- controller.conv.map(_.displayName)
  } yield (eph, av, name)).onUi {
    case (true, av, _) =>
      hintView.setText(getString(R.string.cursor__ephemeral_message))
      AvailabilityView.displayStartOfText(hintView, av, defaultHintTextColor)
    case (false, av, name) if av != Availability.None =>
      hintView.setText(getString(AvailabilityView.viewData(av).textId))
      AvailabilityView.displayStartOfText(hintView, av, defaultHintTextColor)
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
    case (CursorText(text, mentions), EnteredTextSource.FromController) if text != cursorEditText.getText.toString => setText(text, mentions)
    case _ =>
  }

  (controller.isEditingMessage.zip(controller.enteredText) map {
    case (editing, (text, _)) => !editing && text.isEmpty
  }).onUi { hintView.setVisible }

  controller.convIsActive.onUi(this.setVisible)

  controller.onMessageSent.onUi(_ => setText(CursorText.Empty))

  controller.isEditingMessage.onChanged.onUi {
    case false => setText(CursorText.Empty)
    case true =>
      controller.editingMsg.head foreach {
        case Some(msg) => setText(msg.contentString, msg.mentions)
        case _ => // ignore
      }
  }

  controller.onEditMessageReset.onUi { _ =>
    controller.editingMsg.head.map {
      case Some(msg) =>  setText(msg.contentString, msg.mentions)
      case _ =>
    } (Threading.Ui)
  }

  controller.onCursorItemClick {
    case CursorMenuItem.Mention =>
      mentionQuery.head.map {
        case None =>
          val sel = cursorEditText.getSelectionStart
          val mentionSymbol = if (cursorEditText.getText.length() == 0 || cursorEditText.getText.charAt(Math.max(sel - 1, 0)).isWhitespace) "@" else " @"

          cursorEditText.getEditableText.insert(sel, mentionSymbol)
        case _ =>
      } (Threading.Ui)
    case _ =>
  }

  def enableMessageWriting(): Unit = cursorEditText.requestFocus

  def setCallback(callback: CursorCallback) = controller.cursorCallback = Option(callback)

  def setText(cursorText: CursorText): Unit = {
    val color = accentColor.map(_.color).currentValue.getOrElse(Color.BLUE)
    var offset = 0
    var text = cursorText.text
    var mentionSpans = Seq.empty[(CursorMentionSpan, Int, Int)]
    cursorText.mentions.sortBy(_.start).foreach { case Mention(uid, mStart, mLength) =>
      val tStart = mStart + offset
      val tEnd = mStart + mLength + offset
      val mentionText = text.substring(tStart, tEnd)

      text = text.substring(0, tStart) + CursorMentionSpan.PlaceholderChar + text.substring(tEnd)
      mentionSpans = mentionSpans :+ (CursorMentionSpan(uid.get, mentionText, color), tStart, tStart + CursorMentionSpan.PlaceholderChar.length)

      offset = offset + CursorMentionSpan.PlaceholderChar.length - mLength
    }

    cursorEditText.setText(text)
    mentionSpans.foreach { case (span, start, end) =>
      cursorEditText.getEditableText.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    controller.enteredText ! (getText, EnteredTextSource.FromView)
    cursorEditText.setSelection(cursorEditText.getEditableText.length())
  }

  def setText(text: String, mentions: Seq[Mention]): Unit = setText(CursorText(text, mentions))

  def insertText(text: String): Unit =
    cursorEditText.getEditableText.insert(cursorEditText.getSelectionStart, text)

  def hasText: Boolean = !TextUtils.isEmpty(cursorEditText.getText.toString)

  def getText: CursorText = {
    var offset = 0
    var cursorText = cursorEditText.getEditableText.toString
    var mentions = Seq.empty[Mention]
    CursorMentionSpan.getMentionSpans(cursorEditText.getEditableText).sortBy(_._2).foreach {
      case (span, s, e) =>
        val spanLength = e - s
        val mentionLength = span.text.length

        cursorText = cursorText.substring(0, s + offset) + span.text + cursorText.substring(e + offset)
        mentions = mentions :+ Mention(Some(span.userId), s + offset, mentionLength)

        offset = offset + mentionLength - spanLength
    }
    CursorText(cursorText, mentions)
  }

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

  private val MainCursorItems = Seq(Camera, Mention, Sketch, Gif, AudioMessage, More)
  private val SecondaryCursorItems = Seq(VideoMessage, Ping, File, Location, Dummy, Less)
}
