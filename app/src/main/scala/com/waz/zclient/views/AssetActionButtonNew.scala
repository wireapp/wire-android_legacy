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
package com.waz.zclient.views

import java.util.Locale

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.graphics.{Canvas, ColorFilter, Paint, PixelFormat}
import android.util.AttributeSet
import com.waz.api.impl.ProgressIndicator.ProgressData
import com.waz.model.MessageData
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.messages.parts.DeliveryState._
import com.waz.zclient.messages.parts.{AssetController, DeliveryState}
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

//TODO rename when old button is deleted
class AssetActionButtonNew(context: Context, attrs: AttributeSet, style: Int) extends GlyphProgressView(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private val a: TypedArray = context.getTheme.obtainStyledAttributes(attrs, R.styleable.AssetActionButton, 0, 0)
  private val isFileType = a.getBoolean(R.styleable.AssetActionButton_isFileType, false)

  val zms = inject[Signal[ZMessaging]]
  val assetService = inject[AssetController]
  val message = Signal[MessageData]()
  val accentController = inject[AccentColorController]

  val asset = assetService.assetSignal(message)
  val deliveryState = DeliveryState(message, asset)
  private val normalButtonDrawable = getDrawable(R.drawable.selector__icon_button__background__video_message)

  private val errorButtonDrawable = getDrawable(R.drawable.selector__icon_button__background__video_message__error)
  private val onCompletedDrawable = if (isFileType) new FileDrawable(asset.map(_._1.mimeType.extension)) else normalButtonDrawable

  accentController.accentColor.map(_.getColor).on(Threading.Ui)(setProgressColor)

  deliveryState.map {
    case Complete => (if (isFileType) 0 else R.string.glyph__play, onCompletedDrawable)
    case Uploading |
         Downloading => (R.string.glyph__close, normalButtonDrawable)
    case _: Failed | Cancelled => (R.string.glyph__redo, errorButtonDrawable)
    case _ => (0, null)
  }.on(Threading.Ui) {
    case (action, drawable) =>
      setText(if (action == 0) "" else getString(action))
      setBackground(drawable)
  }

  def listenToPlayState(isPlaying: Signal[Boolean]): Unit = isPlaying.map {
    case true => R.string.glyph__pause
    case false => R.string.glyph__play
  }.map(getString).on(Threading.Ui)(setText)

  deliveryState.zip(message.map(_.assetId)).flatMap {
    case (Uploading, id) => assetService.uploadProgress(id).map(Option(_))
    case (Downloading, id) => assetService.downloadProgress(id).map(Option(_))
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

  deliveryState.map { case Complete => false; case _ => true }.on(Threading.Ui) {
    //only want to set the onClick listener if NOT completed
    case true =>
      this.onClick {
        deliveryState.currentValue.foreach {
          case UploadFailed => message.currentValue.foreach(assetService.retry)
          case Uploading => message.currentValue.foreach(assetService.cancelUpload)
          case Downloading => message.currentValue.foreach(assetService.cancelDownload)
          case _ => //
        }
      }
    case false => //do nothing, individual view parts will handle what happens when in the Completed state.
  }

}

protected class FileDrawable(ext: Signal[String])(implicit context: Context, cxt: EventContext) extends Drawable {

  ext.onChanged.on(Threading.Ui) { _ =>
    invalidateSelf()
  }

  private final val textCorrectionSpacing = getDimenPx(R.dimen.wire__padding__4)
  private final val fileGlyph = getString(R.string.glyph__file)
  private final val glyphPaint = new Paint
  private final val textPaint = new Paint

  glyphPaint.setTypeface(TypefaceUtils.getTypeface(TypefaceUtils.getGlyphsTypefaceName))
  glyphPaint.setColor(getColor(R.color.black_48))
  glyphPaint.setAntiAlias(true)
  glyphPaint.setTextAlign(Paint.Align.CENTER)
  glyphPaint.setTextSize(getDimenPx(R.dimen.content__audio_message__button__size))

  textPaint.setColor(getColor(R.color.white))
  textPaint.setAntiAlias(true)
  textPaint.setTextAlign(Paint.Align.CENTER)
  textPaint.setTextSize(getDimenPx(R.dimen.wire__text_size__tiny))

  override def draw(canvas: Canvas): Unit = {
    canvas.drawText(fileGlyph, getBounds.width / 2, getBounds.height, glyphPaint)
    ext.currentValue.foreach { ex => canvas.drawText(ex.toUpperCase(Locale.getDefault), getBounds.width / 2, getBounds.height - textCorrectionSpacing, textPaint) }
  }

  override def setColorFilter(colorFilter: ColorFilter): Unit = {
    glyphPaint.setColorFilter(colorFilter)
    textPaint.setColorFilter(colorFilter)
  }

  override def setAlpha(alpha: Int): Unit = {
    glyphPaint.setAlpha(alpha)
    textPaint.setAlpha(alpha)
  }

  override def getOpacity: Int = PixelFormat.TRANSLUCENT
}
