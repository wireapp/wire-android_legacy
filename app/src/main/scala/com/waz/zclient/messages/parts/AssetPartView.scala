/**
  * Wire
  * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.messages.parts

import java.util.Locale

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.{Canvas, ColorFilter, Paint, PixelFormat}
import android.util.AttributeSet
import android.widget.{ImageView, LinearLayout}
import com.waz.api
import com.waz.api.AssetStatus._
import com.waz.api.Message
import com.waz.api.impl.ProgressIndicator.ProgressData
import com.waz.model.{AssetId, MessageContent, MessageData}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.views.GlyphProgressView
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}

class AssetPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  private lazy val assetActionButton: AssetActionButton = findById(R.id.action_button)

  override val tpe: MsgPart = MsgPart.Asset

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    assetActionButton.msgData ! msg
  }
}

class AssetController(implicit inj: Injector) extends Injectable {
  val assets = inject[Signal[ZMessaging]].map(_.assets)

  def assetSignal(id: AssetId) = assets.flatMap(_.assetSignal(id))

  def downloadProgress(id: AssetId) = assets.flatMap(_.downloadProgress(id))

  def uploadProgress(id: AssetId) = assets.flatMap(_.uploadProgress(id))
}

class ProgressDotsView(context: Context, attrs: AttributeSet, style: Int) extends ImageView(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  private val ANIMATION_DURATION = 350 * 3
  private val lightPaint = new Paint
  private val darkPaint = new Paint
  private val dotSpacing = context.getResources.getDimensionPixelSize(R.dimen.progress_dot_spacing_and_width)
  private val dotRadius = dotSpacing / 2
  private val animator = ValueAnimator.ofInt(0, 1, 2, 3).setDuration(ANIMATION_DURATION)
  private var darkDotIndex = 0

  lightPaint.setColor(context.getResources.getColor(R.color.graphite_16))
  darkPaint.setColor(context.getResources.getColor(R.color.graphite_40))
  animator.setRepeatCount(ValueAnimator.INFINITE)
  animator.setInterpolator(null)
  animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
    def onAnimationUpdate(animation: ValueAnimator): Unit = {
      darkDotIndex = animation.getAnimatedValue.asInstanceOf[Int]
      invalidate()
    }
  })

  override def onAttachedToWindow(): Unit = {
    super.onAttachedToWindow
    animator.start()
  }

  override def onDetachedFromWindow(): Unit = {
    super.onDetachedFromWindow
    animator.cancel()
  }

  protected override def onDraw(canvas: Canvas): Unit = {
    val centerX: Int = getWidth / 2
    val centerY: Int = getHeight / 2
    val dotLeftCenterX: Int = centerX - dotSpacing - dotRadius
    val dotRightCenterX: Int = centerX + dotSpacing + dotRadius
    canvas.drawCircle(dotLeftCenterX, centerY, dotRadius, if (darkDotIndex == 0) darkPaint else lightPaint)
    canvas.drawCircle(centerX, centerY, dotRadius, if (darkDotIndex == 1) darkPaint else lightPaint)
    canvas.drawCircle(dotRightCenterX, centerY, dotRadius, if (darkDotIndex == 2) darkPaint else lightPaint)
  }
}

class AssetActionButton(context: Context, attrs: AttributeSet, style: Int) extends GlyphProgressView(context, attrs, style) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  val assetService = inject[AssetController]

  val msgData = Signal[MessageData]()
  val msgState = msgData.map(_.state)
  val assetStatus = msgData.flatMap(m => assetService.assetSignal(m.assetId)).map(_._2)

  private var overrideGlyph: String = null

  Signal(assetStatus, msgState).map { vals =>
    import api.AssetStatus._

    def normalButtonBackground = context.getResources.getDrawable(R.drawable.selector__icon_button__background__video_message)
    def errorButtonBackground = context.getResources.getDrawable(R.drawable.selector__icon_button__background__video_message__error)
    def fileDrawable = new FileDrawable(context, msgData.map(_.assetId))

    vals match {
      case (UPLOAD_CANCELLED, _) => (R.string.glyph__close, normalButtonBackground)

      case (UPLOAD_FAILED, Message.Status.SENT) => (R.string.glyph__play, errorButtonBackground)
      case (UPLOAD_FAILED, _) => (R.string.glyph__redo, errorButtonBackground)

      case (UPLOAD_NOT_STARTED | META_DATA_SENT | PREVIEW_SENT | UPLOAD_IN_PROGRESS, mState) =>
        mState match {
          case Message.Status.FAILED => (R.string.glyph__redo, errorButtonBackground)
          case Message.Status.SENT => (R.string.glyph__play, normalButtonBackground) //upload progress!
          case _ => (R.string.glyph__close, normalButtonBackground) //upload progress
        }

      case (UPLOAD_DONE | DOWNLOAD_DONE, mState) => (0, fileDrawable)

      case (DOWNLOAD_FAILED, _) => (R.string.glyph__redo, errorButtonBackground)
      case (DOWNLOAD_IN_PROGRESS, _) => (R.string.glyph__close, normalButtonBackground) // download
      case _ => (0, null)
    }
  }.map {
    case (0, d) => ("", d)
    case (id, d) => (context.getString(id), d)
  }.on(Threading.Ui) {
    case (a, d) =>
      setText(a)
      setBackground(d)
  }

  Signal(assetStatus, msgData).flatMap {
    case (UPLOAD_NOT_STARTED | META_DATA_SENT | PREVIEW_SENT | UPLOAD_IN_PROGRESS, mData) =>
      mData.state match {
        case Message.Status.FAILED => Signal.const[Option[ProgressData]](None)
        case Message.Status.SENT => assetService.uploadProgress(mData.assetId).map(Option(_))
        case _ => assetService.uploadProgress(mData.assetId).map(Option(_))
      }
    case (DOWNLOAD_IN_PROGRESS, mData) => assetService.downloadProgress(mData.assetId).map(Option(_))
    case _ => Signal.const[Option[ProgressData]](None)
  }.on(Threading.Ui) {
    case Some(p) =>
      import com.waz.api.ProgressIndicator.State._
      p.state match {
        case CANCELLED | FAILED | COMPLETED => clearProgress()
        case RUNNING if p.total == -1 => startEndlessProgress()
        case RUNNING => setProgress(if (p.total > 0) p.current.toFloat / p.total.toFloat else 0)
        case _ => clearProgress()
      }
    case _ => clearProgress()
  }

  //TODO playback controls for audio message types
}

protected class FileDrawable(context: Context, assetId: Signal[AssetId])(implicit injector: Injector, cxt: EventContext) extends Drawable with Injectable {

  private val ext = assetId.flatMap(id => inject[AssetController].assetSignal(id)).map(_._1.mimeType.extension)

  ext.onChanged.on(Threading.Ui) { _ =>
    invalidateSelf()
  }

  private final val textCorrectionSpacing = context.getResources.getDimensionPixelSize(R.dimen.wire__padding__4)
  private final val fileGlyph = context.getResources.getString(R.string.glyph__file)
  private final val glyphPaint = new Paint
  private final val textPaint = new Paint

  glyphPaint.setTypeface(TypefaceUtils.getTypeface(TypefaceUtils.getGlyphsTypefaceName))
  glyphPaint.setColor(context.getResources.getColor(R.color.black_48))
  glyphPaint.setAntiAlias(true)
  glyphPaint.setTextAlign(Paint.Align.CENTER)
  glyphPaint.setTextSize(context.getResources.getDimensionPixelSize(R.dimen.content__audio_message__button__size))

  textPaint.setColor(context.getResources.getColor(R.color.white))
  textPaint.setAntiAlias(true)
  textPaint.setTextAlign(Paint.Align.CENTER)
  textPaint.setTextSize(context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__tiny))

  override def draw(canvas: Canvas): Unit = {
    canvas.drawText(fileGlyph, getBounds.width / 2, getBounds.height, glyphPaint)
    ext.currentValue.foreach { ex => canvas.drawText(ex.toUpperCase(Locale.getDefault), getBounds.width / 2, getBounds.height - textCorrectionSpacing, textPaint) }
  }

  override def setAlpha(alpha: Int): Unit = ()

  override def setColorFilter(colorFilter: ColorFilter): Unit = ()

  override def getOpacity: Int = PixelFormat.TRANSLUCENT
}


