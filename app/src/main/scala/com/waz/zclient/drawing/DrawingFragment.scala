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
package com.waz.zclient.drawing

import java.io.File
import java.net.URI

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.Intent
import android.graphics.{Bitmap, BitmapFactory}
import android.hardware.{Sensor, SensorManager}
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import android.view._
import android.widget.{FrameLayout, TextView}
import com.waz.api.MemoryImageCache
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.assets.{AssetService, Content, UriHelper}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.utils.wrappers.{URI => URIWrapper}
import com.waz.zclient.common.controllers.ScreenController
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.controllers.drawing.IDrawingController.{DrawingDestination, DrawingMethod}
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.pages.main.conversation.AssetIntentsManager
import com.waz.zclient.ui.colorpicker.{ColorPickerLayout, EmojiBottomSheetDialog, EmojiSize}
import com.waz.zclient.ui.sketch.{DrawingCanvasCallback, DrawingCanvasView}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.{ColorUtils, MathUtils}
import com.waz.zclient.ui.views.{CursorIconButton, SketchEditText}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.utils.debug.ShakeEventListener
import com.waz.zclient.utils.extensions.ViewExtensionsKt
import com.waz.zclient.{FragmentHelper, R}

import scala.collection.immutable.ListSet
import scala.concurrent.Future

object DrawingFragment {
  val Tag: String = classOf[DrawingFragment].getName
  private val SavedInstanceBitmap     = "SAVED_INSTANCE_BITMAP"
  private val ArgDrawingDestination   = "ARGUMENT_DRAWING_DESTINATION"
  private val ArgDrawingMethod        = "ARGUMENT_DRAWING_METHOD"

  private val ContentMimeArg          = "CONTENT_MIME"
  private val BytesInputArg           = "BYTES_INPUT"
  private val UriInputArg             = "URI_INPUT"
  private val FileInputArg            = "FILE_INPUT"
  private val AssetInputArg           = "ASSET_INPUT"

  private val TextAlphaInvisible      = 0F
  private val TextAlphaMove           = 0.2F
  private val TextAlphaVisible        = 1F
  private val SendButtonDisabledAlpha = 102

  def newInstance(sketch: Sketch): DrawingFragment =
    returning(new DrawingFragment) { f =>
      f.setArguments(returning(new Bundle) { b =>
        putInputToBundle(b, sketch.input)
        b.putString(ArgDrawingDestination, sketch.dest.toString)
        b.putString(ArgDrawingMethod, sketch.method.toString)
      })
    }

  case class Sketch(input: Option[Either[Content, AssetId]], dest: DrawingDestination, method: DrawingMethod)

  object Sketch {
    import DrawingDestination._
    import DrawingMethod._

    val BlankSketch = Sketch(None, SKETCH_BUTTON, DRAW)

    def cameraPreview(input: Content, method: DrawingMethod = DRAW) = Sketch(Some(Left(input)), CAMERA_PREVIEW_VIEW, method)
    def asset(assetId: AssetId, method: DrawingMethod = DRAW)       = Sketch(Some(Right(assetId)), SINGLE_IMAGE_VIEW, method)
  }

  def putInputToBundle(b: Bundle, input: Option[Either[Content, AssetId]]): Unit = input match {
    case Some(Left(Content.Bytes(mime, bytes))) =>
      b.putByteArray(BytesInputArg, bytes)
      b.putString(ContentMimeArg, mime.str)
    case Some(Left(Content.Uri(uri))) =>
      b.putString(UriInputArg, uri.toString)
    case Some(Left(Content.File(mime, file))) =>
      b.putString(FileInputArg, file.getAbsolutePath)
      b.putString(ContentMimeArg, mime.str)
    case Some(Right(assetId)) =>
      b.putString(AssetInputArg, assetId.str)
    case _ =>
  }

  def getInputFromBundle(b: Bundle): Option[Either[Content, AssetId]] =
    if (b.containsKey(UriInputArg)) Some(Left(Content.Uri(URI.create(b.getString(UriInputArg)))))
    else if (b.containsKey(AssetInputArg)) Some(Right(AssetId(b.getString(AssetInputArg))))
    else if (b.containsKey(ContentMimeArg)) {
      val mime = if (b.containsKey(ContentMimeArg)) Mime.Image.supported.find { _.str == b.getString(ContentMimeArg) } else None
      mime.flatMap { m =>
        if (b.containsKey(BytesInputArg)) Some(Left(Content.Bytes(m, b.getByteArray(BytesInputArg))))
        else if (b.containsKey(FileInputArg)) Some(Left(Content.File(m, new File(b.getString(FileInputArg)))))
        else None
      }
    } else None
}

class DrawingFragment extends FragmentHelper
  with ColorPickerLayout.OnColorSelectedListener
  with ColorPickerLayout.OnWidthChangedListener
  with DrawingCanvasCallback
  with ViewTreeObserver.OnScrollChangedListener
  with AssetIntentsManager.Callback {

  import DrawingFragment._
  import com.waz.threading.Threading.Implicits.Ui

  private lazy val screenController   = inject[ScreenController]
  private lazy val keyboardController = inject[KeyboardController]
  private lazy val userPrefController = inject[IUserPreferencesController] //TODO replace with SE prefs
  private lazy val sensorManager      = inject[SensorManager]
  private lazy val uriHelper          = inject[UriHelper]
  private lazy val accentColor        = inject[Signal[AccentColor]].map(_.color)
  private lazy val drawingDestination = getStringArg(ArgDrawingDestination).map(DrawingDestination.valueOf)

  private var imageInput             = Option.empty[Either[Content, AssetId]]
  private var drawingMethod          = Option.empty[DrawingMethod]
  private var currentEmojiSize       = EmojiSize.SMALL
  private var includeBackgroundImage = false
  private var shouldOpenEditText     = false

  private lazy val shakeEventListener = returning(new ShakeEventListener()) {
    _.setOnShakeListener(new ShakeEventListener.OnShakeListener() {
      override def onShake(): Unit = {
        drawingCanvasView.foreach(v => if (includeBackgroundImage) v.removeBackgroundBitmap() else v.drawBackgroundBitmap())
        includeBackgroundImage = !includeBackgroundImage
      }
    })
  }

  private var assetIntentsManager: AssetIntentsManager = _
  private lazy val defaultTextColor = getColor(R.color.text__primary_light)

  private lazy val toolbar              = view[Toolbar](R.id.t_drawing_toolbar)
  private lazy val drawingViewTip       = view[TypefaceTextView](R.id.ttv__drawing__view__tip)
  private lazy val drawingTipBackground = view[View](R.id.v__tip_background)
  private lazy val sketchEditTextView   = view[SketchEditText](R.id.et__sketch_text)

  private lazy val galleryButton = returning(view[View](R.id.gtv__drawing__gallery_button)) { vh =>
    vh.onClick { _ =>
      inject[PermissionsService].requestAllPermissions(ListSet(READ_EXTERNAL_STORAGE)).foreach {
        case true =>
          assetIntentsManager.openGalleryForSketch()
        case _ =>
      }
    }
  }

  private lazy val actionButtonEmoji = returning(view[TextView](R.id.gtv__drawing_button__emoji)) { vh =>
    vh.onClick(_ => onEmojiClick())
  }

  private lazy val actionButtonText = returning(view[TextView](R.id.gtv__drawing_button__text)) { vh =>
    vh.onClick(_ => onTextClick())
  }

  private lazy val actionButtonSketch = returning(view[TextView](R.id.gtv__drawing_button__sketch)) { vh =>
    vh.onClick(_ => onSketchClick())
    accentColor.onUi(c => vh.foreach(_.setTextColor(c)))
  }

  private lazy val conversationTitle = returning(view[TypefaceTextView](R.id.tv__drawing_toolbar__title)) { vh =>
    inject[ConversationController].currentConvName.map(_.toUpperCase()).onUi(t => vh.foreach(_.setText(t)))
  }

  private lazy val drawingCanvasView = returning(view[DrawingCanvasView](R.id.dcv__canvas)) { vh =>
    accentColor.onUi(c => vh.foreach(_.setDrawingColor(c)))
  }

  private lazy val colorLayout = returning(view[ColorPickerLayout](R.id.cpdl__color_layout)) { vh =>
    accentColor.onUi(c => vh.foreach(_.setAccentColors(getIntArray(R.array.draw_color), c)))
  }

  private lazy val sendDrawingButton  = returning(view[CursorIconButton](R.id.tv__send_button)) { vh =>
    vh.onClick { _ =>
      getFinalSketchBitmap.map(inject[ConversationController].sendAssetMessage(_, s"sketch_${AESKey().str}"))
      drawingDestination.foreach(screenController.hideSketch ! _)
    }

    accentColor.onUi { c =>
      for {
        canv <- drawingCanvasView
        send <- vh
      } send.setSolidBackgroundColor(if (canv.isEmpty) ColorUtils.injectAlpha(SendButtonDisabledAlpha, c) else c)
    }
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    imageInput          = getInputFromBundle(getArguments)
    drawingMethod       = getStringArg(ArgDrawingMethod).map(DrawingMethod.valueOf)
    assetIntentsManager = new AssetIntentsManager(getActivity, this)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_drawing, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    drawingCanvasView.foreach { v =>
      v.setDrawingCanvasCallback(this)
      v.setOnTouchListener(new View.OnTouchListener() {
        override def onTouch(v: View, event: MotionEvent): Boolean = {
          event.getAction match {
            case MotionEvent.ACTION_DOWN =>
              hideTip()
            case _ =>
          }
          false
        }
      })
    }

    colorLayout.foreach { v =>
      v.setOnColorSelectedListener(this)
      v.getViewTreeObserver.addOnScrollChangedListener(this)
    }

    toolbar.foreach { v =>
      v.inflateMenu(R.menu.toolbar_sketch)
      v.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
        override def onMenuItemClick(item: MenuItem): Boolean =
          item.getItemId match {
            case R.id.close => onClose()
            case _ => false
          }
      })
      v.setNavigationOnClickListener(new View.OnClickListener() {
        override def onClick(v: View): Unit =
          drawingCanvasView.foreach { v =>
            keyboardController.hideKeyboardIfVisible()
            v.undo()
          }
      })
      v.setNavigationIcon(R.drawable.toolbar_action_undo_disabled)
    }

    sketchEditTextView.foreach { v =>
      v.setAlpha(TextAlphaInvisible)
      v.setVisibility(View.INVISIBLE)
      v.setCustomHint(getString(R.string.drawing__text_hint))
      v.setBackground(ColorUtils.getTransparentDrawable)
      v.setHintFontId(R.string.wire__typeface__medium)
      v.setTextFontId(R.string.wire__typeface__regular)
      v.setSketchScale(1.0f)
      v.setOnTouchListener(new View.OnTouchListener() {
        private var initialX = .0
        private var initialY = .0
        private var params = null.asInstanceOf[FrameLayout.LayoutParams]

        override def onTouch(v: View, event: MotionEvent): Boolean = {

          if (drawingCanvasView.exists(_.getCurrentMode == DrawingCanvasView.Mode.TEXT))
            event.getAction match {
              case MotionEvent.ACTION_DOWN =>
                initialX = event.getX
                initialY = event.getY
                params = v.getLayoutParams.asInstanceOf[FrameLayout.LayoutParams]
                v.setAlpha(TextAlphaMove)
              case MotionEvent.ACTION_MOVE =>
                params.leftMargin += (event.getX - initialX).toInt
                params.topMargin += (event.getY - initialY).toInt
                clampSketchEditBoxPosition(params)
                v.setLayoutParams(params)
              case MotionEvent.ACTION_UP =>
                closeKeyboard()
              case _ =>
            }
          drawingCanvasView.exists(_.onTouchEvent(event))
        }
      })
    }

    keyboardController.keyboardHeight.onUi { height =>
      if (height <= 0) closeKeyboard()
      changeEditTextVisibility(height)
    }

    // Use saved background image if exists
    Option(savedInstanceState).flatMap(b => Option(b.getParcelable(SavedInstanceBitmap))) match {
      case Some(bm) => drawingCanvasView.foreach(_.setBackgroundBitmap(bm))
      case _ => setBackgroundBitmap(true)
    }

    //Instantiate lazy views
    drawingViewTip
    drawingTipBackground
    actionButtonEmoji
    actionButtonText
    actionButtonSketch
    conversationTitle
    sendDrawingButton
    galleryButton
  }

  override def onStart(): Unit = {
    super.onStart()
    sensorManager.registerListener(shakeEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
  }

  override def onStop(): Unit = {
    sensorManager.unregisterListener(shakeEventListener)
    super.onStop()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit =
    assetIntentsManager.onActivityResult(requestCode, resultCode, data)

  override def onDataReceived(tpe: AssetIntentsManager.IntentType, uri: URIWrapper): Unit = {
    import AssetIntentsManager.IntentType._
    tpe match {
      case SKETCH_FROM_GALLERY =>
        drawingMethod = Some(DrawingMethod.DRAW)
        sketchEditTextView.foreach { v =>
          v.setText("")
          v.setVisibility(View.GONE)
        }

        drawingCanvasView.foreach { v =>
          v.reset()
          v.removeBackgroundBitmap()
        }

        imageInput = Some(Left(Content.Uri(URI.create(uri.toString))))
        setBackgroundBitmap(false)
        onSketchClick()
      case _ =>
    }
  }

  override def onCanceled(tpe: AssetIntentsManager.IntentType): Unit = {}

  override def onFailed(tpe: AssetIntentsManager.IntentType): Unit = {}

  override def openIntent(intent: Intent, intentType: AssetIntentsManager.IntentType): Unit = {
    startActivityForResult(intent, intentType.requestCode)
    getActivity.overridePendingTransition(R.anim.camera_in, R.anim.camera_out)
  }

  override def onColorSelected(color: Int, strokeSize: Int): Unit = {
    onSketchClick() //when user selects color, they expect to be able to sketch
    drawingCanvasView.foreach { v =>
      v.setDrawingColor(color)
      v.setStrokeSize(strokeSize)
    }
    sketchEditTextView.foreach { v =>
      v.setBackground(ColorUtils.getRoundedTextBoxBackground(getContext, color, v.getHeight))
      if (MathUtils.floatEqual(v.getAlpha, TextAlphaInvisible)) drawSketchEditText()
    }
  }

  override def onBackPressed(): Boolean = onClose()

  private def onClose(): Boolean = {
    drawingDestination.foreach(inject[ScreenController].hideSketch ! _)
    keyboardController.hideKeyboardIfVisible()
    true
  }

  private def onEmojiClick(): Unit =
    new EmojiBottomSheetDialog(getContext, currentEmojiSize, new EmojiBottomSheetDialog.EmojiDialogListener() {
      override def onEmojiSelected(emoji: String, emojiSize: EmojiSize): Unit = {
        setDrawingMode(DrawingCanvasView.Mode.EMOJI)
        drawingCanvasView.foreach { v =>
          v.setEmoji(emoji, emojiSize.getEmojiSize(getContext))
          currentEmojiSize = emojiSize
          userPrefController.addRecentEmoji(emoji)
        }
      }
    }, userPrefController.getRecentEmojis, userPrefController.getUnsupportedEmojis).show()

  private def onSketchClick(): Unit =
    setDrawingMode(DrawingCanvasView.Mode.SKETCH)

  private def onTextClick(): Unit = {
    setDrawingMode(DrawingCanvasView.Mode.TEXT)
    for {
      d  <- drawingCanvasView
      sk <- sketchEditTextView
    } {
      if (isShowingKeyboard) closeKeyboard()
      else {
        if (sk.getVisibility != View.VISIBLE) {
          shouldOpenEditText = true
          sk.setAlpha(TextAlphaInvisible)
          sk.setBackground(ColorUtils.getTransparentDrawable)
        }
        sk.setVisibility(View.VISIBLE)
        d.hideText()
        sk.setCursorVisible(true)
        sk.requestFocus
        keyboardController.keyboardHeight.currentValue.foreach(changeEditTextVisibility)
        keyboardController.showKeyboardIfHidden()
        hideTip()
      }
    }
  }

  private def setDrawingMode(mode: DrawingCanvasView.Mode): Unit = {
    for {
      text   <- actionButtonText
      sketch <- actionButtonSketch
      emoji  <- actionButtonEmoji
      canvas <- drawingCanvasView
      accent <- accentColor.currentValue
    } {
      import DrawingCanvasView.Mode._
      val (toSetColor, toUnsetColor) = mode match {
        case TEXT   => (text,   Seq(sketch, emoji))
        case SKETCH => (sketch, Seq(text, emoji))
        case EMOJI  => (emoji,  Seq(text, sketch))
      }

      toSetColor.setTextColor(accent)
      toUnsetColor.foreach(_.setTextColor(defaultTextColor))
      canvas.setCurrentMode(mode)
    }
  }

  override def drawingAdded(): Unit =
    for {
      tb <- toolbar
      sd <- sendDrawingButton
      ac <- accentColor.currentValue
    } {
      if (isShowingKeyboard) closeKeyboard()
      tb.setNavigationIcon(R.drawable.toolbar_action_undo)
      sd.setSolidBackgroundColor(ac)
      sd.setClickable(true)
    }

  override def drawingCleared(): Unit =
    for {
      tb <- toolbar
      sd <- sendDrawingButton
      ac <- accentColor.currentValue
    } {
      tb.setNavigationIcon(R.drawable.toolbar_action_undo_disabled)
      sd.setSolidBackgroundColor(ColorUtils.injectAlpha(SendButtonDisabledAlpha, ac))
      sd.setClickable(false)
    }

  override def reserveBitmapMemory(width: Int, height: Int): Unit =
    MemoryImageCache.reserveImageMemory(width, height)

  override def onScaleChanged(scale: Float): Unit =
    sketchEditTextView.foreach { v =>
      v.setSketchScale(scale)
      val params = v.getLayoutParams.asInstanceOf[FrameLayout.LayoutParams]
      clampSketchEditBoxPosition(params)
      v.setLayoutParams(params)
    }

  override def onScaleStart(): Unit =
    for {
      cv <- drawingCanvasView
      sk <- sketchEditTextView
    } {
      cv.hideText()
      sk.setAlpha(TextAlphaVisible)
    }

  override def onScaleEnd(): Unit =
    closeKeyboard()

  override def onTextChanged(text: String, x: Int, y: Int, scale: Float): Unit =
    sketchEditTextView.foreach { v =>
      v.setMarginLeft(x)
      v.setMarginTop(y)
      v.setSketchScale(scale)
      v.setText(text)
      v.setSelection(text.length)
    }

  private def hideTip(): Unit =
    for {
      vt <- drawingViewTip
      bg <- drawingTipBackground
      cv <- drawingCanvasView
    } if (vt.getVisibility != View.GONE) {
      vt.setVisibility(View.GONE)
      bg.setVisibility(View.GONE)
      cv.setOnTouchListener(null)
    }

  private def changeEditTextVisibility(keyboardHeight: Int): Unit =
    if (shouldOpenEditText) {
      shouldOpenEditText = false

      //Posted so that the user doesn't see the previous location
      Threading.Ui {
        sketchEditTextView.foreach { v =>
          v.setLayoutParams(returning(v.getLayoutParams.asInstanceOf[FrameLayout.LayoutParams]) { params =>
            params.leftMargin = (v.getParent.asInstanceOf[ViewGroup].getMeasuredWidth - v.getMeasuredWidth) / 2
            params.topMargin = v.getParent.asInstanceOf[ViewGroup].getMeasuredHeight - keyboardHeight - v.getMeasuredHeight
          })
          v.setBackground(ColorUtils.getRoundedTextBoxBackground(getContext, accentColor.currentValue.getOrElse(getColor(R.color.black)), v.getMeasuredHeight))
          v.setAlpha(TextAlphaVisible)
        }
      }
    }
    else sketchEditTextView.foreach(_.setAlpha(TextAlphaVisible))

  private def isShowingKeyboard =
    sketchEditTextView.exists { v =>
      v.isVisible &&
        java.lang.Float.compare(v.getAlpha, TextAlphaVisible) == 0 &&
        keyboardController.isVisible
    }

  private def closeKeyboard(): Unit = {
    drawSketchEditText()
    keyboardController.hideKeyboardIfVisible()
  }

  private def drawSketchEditText(): Unit =
    for {
      sk   <- sketchEditTextView
      canv <- drawingCanvasView
    } if (sk.isVisible) {
      sk.setAlpha(TextAlphaVisible)
      sk.setCursorVisible(false)
      //This has to be on a post otherwise the setAlpha and setCursor won't be noticeable in the drawing cache
      getView.post(new Runnable() {
        override def run(): Unit = {
          val viewBitmap = ViewExtensionsKt.getViewBitmap(sk)
          val params = sk.getLayoutParams.asInstanceOf[FrameLayout.LayoutParams]
          canv.showText()
          canv.drawTextBitmap(
            viewBitmap,
            params.leftMargin,
            params.topMargin,
            sk.getText.toString,
            sk.getSketchScale
          )
          sk.setAlpha(TextAlphaInvisible)
        }
      })
    }

  private def getFinalSketchBitmap: Option[Bitmap] =
    drawingCanvasView.map { v =>
      try {
        val bitmapTrim = v.getImageTrimValues
        MemoryImageCache.reserveImageMemory(bitmapTrim.width, bitmapTrim.height)
        Bitmap.createBitmap(v.getBitmap, bitmapTrim.left, bitmapTrim.top, bitmapTrim.width, bitmapTrim.height)
      } catch {
        case _: Throwable => //TODO do we want to handle fatal too?
          v.getBitmap
      }
    }

  private def clampSketchEditBoxPosition(params: FrameLayout.LayoutParams): Unit = {
    sketchEditTextView.foreach { v =>
      val sketchWidth  = v.getParent.asInstanceOf[ViewGroup].getMeasuredWidth
      val sketchHeight = v.getParent.asInstanceOf[ViewGroup].getMeasuredHeight
      val textWidth    = v.getMeasuredWidth
      val textHeight   = v.getMeasuredHeight
      params.leftMargin = Math.min(params.leftMargin, sketchWidth - textWidth / 2)
      params.topMargin  = Math.min(params.topMargin, sketchHeight - textHeight / 2)
      params.leftMargin = Math.max(params.leftMargin, -textWidth / 2)
      params.topMargin  = Math.max(params.topMargin, -textHeight / 2)
    }
  }

  private def setBackgroundBitmap(showHint: Boolean): Unit = imageInput.foreach { input =>
    for {
      is     <- input match {
                  case Left(content)  =>
                    Future.fromTry(content.openInputStream(uriHelper))
                  case Right(assetId) =>
                    inject[Signal[AssetService]].head.flatMap(_.loadContentById(assetId).future)
                }
      bitmap =  BitmapFactory.decodeStream(is)
      cv     <- drawingCanvasView
      tip    <- drawingViewTip
      bg     <- drawingTipBackground
    } {
      includeBackgroundImage = true
      bg.setVisibility(if (showHint) View.VISIBLE else View.INVISIBLE)
      if (showHint) {
        tip.setText(getString(R.string.drawing__tip__picture__message))
        tip.setTextColor(getColorWithTheme(R.color.drawing__tip__image))
      } else hideTip()
      cv.setBackgroundBitmap(bitmap)
      drawingMethod match {
        case Some(DrawingMethod.EMOJI) => onEmojiClick()
        case Some(DrawingMethod.TEXT)  => onTextClick()
        case _ =>
      }
    }
  }

  override def onScrollWidthChanged(width: Int): Unit = {}

  override def onTextRemoved(): Unit =
    sketchEditTextView.foreach { v =>
      v.setText("")
      v.setVisibility(View.INVISIBLE)
    }

  override def onScrollChanged(): Unit = {}
}

