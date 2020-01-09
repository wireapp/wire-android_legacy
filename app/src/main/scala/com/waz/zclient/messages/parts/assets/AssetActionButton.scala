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
package com.waz.zclient.messages.parts.assets

import java.util.Locale

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.{Canvas, ColorFilter, Paint, PixelFormat}
import android.util.AttributeSet
import com.waz.service.assets.{AssetStatus, DownloadAssetStatus, UploadAssetStatus}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.views.GlyphProgressView
import com.waz.zclient.{R, ViewHelper}

class AssetActionButton(context: Context, attrs: AttributeSet, style: Int) extends GlyphProgressView(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private val isFileType = withStyledAttributes(attrs, R.styleable.AssetActionButton) { _.getBoolean(R.styleable.AssetActionButton_isFileType, false) }
  private val accentController = inject[AccentColorController]

  val onClicked = EventStream[DeliveryState]

  private val normalButtonDrawable = getDrawable(R.drawable.selector__icon_button__background__video_message)
  private val errorButtonDrawable = getDrawable(R.drawable.selector__icon_button__background__video_message__error)
  private def onCompletedDrawable(ext: String) = if (isFileType) new FileDrawable(Signal.const(ext)) else normalButtonDrawable

  def setStatus(status: AssetStatus, extension: String, playing: Boolean): Unit = {
    val (icon, drawable, stateText) = status match {
      case DownloadAssetStatus.InProgress | UploadAssetStatus.InProgress => (R.string.glyph__close, normalButtonDrawable, R.string.action_button_state_close)
      case DownloadAssetStatus.Failed | DownloadAssetStatus.Cancelled =>    (R.string.glyph__redo, errorButtonDrawable, R.string.action_button_state_redo)
      case UploadAssetStatus.Failed | UploadAssetStatus.Cancelled =>        (R.string.glyph__redo, errorButtonDrawable, R.string.action_button_state_redo)
      case AssetStatus.Done if playing =>                                   (R.string.glyph__pause, onCompletedDrawable(extension), R.string.action_button_state_pause)
      case AssetStatus.Done if !playing =>                                  (R.string.glyph__play, onCompletedDrawable(extension), R.string.action_button_state_play)
      case _ =>                                                             (0, null, R.string.action_button_state_none)
    }

    setContentDescription(getString(stateText)) //Set content description for tests
    setBackground(drawable)
    setText(icon match {
      case 0 => ""
      case r => getString(r)
    })
  }

  accentController.accentColor.map(_.color).on(Threading.Ui)(setProgressColor)
}

protected class FileDrawable(ext: Signal[String])(implicit context: Context, cxt: EventContext) extends Drawable {

  private final val textCorrectionSpacing = getDimenPx(R.dimen.wire__padding__4)
  private final val fileGlyph = getString(R.string.glyph__file)
  private final val glyphPaint = new Paint
  private final val textPaint = new Paint

  private var extension = ""

  glyphPaint.setTypeface(TypefaceUtils.getTypeface(TypefaceUtils.getGlyphsTypefaceName))
  glyphPaint.setColor(getColor(R.color.black_48))
  glyphPaint.setAntiAlias(true)
  glyphPaint.setTextAlign(Paint.Align.CENTER)
  glyphPaint.setTextSize(getDimenPx(R.dimen.content__audio_message__button__size))

  textPaint.setColor(getColor(R.color.white))
  textPaint.setAntiAlias(true)
  textPaint.setTextAlign(Paint.Align.CENTER)
  textPaint.setTextSize(getDimenPx(R.dimen.wire__text_size__tiny))

  ext.on(Threading.Ui) { ex =>
    extension = ex.toUpperCase(Locale.getDefault)
    invalidateSelf()
  }

  override def draw(canvas: Canvas): Unit = {
    canvas.drawText(fileGlyph, getBounds.width / 2, getBounds.height, glyphPaint)
    canvas.drawText(extension, getBounds.width / 2, getBounds.height - textCorrectionSpacing, textPaint)
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
