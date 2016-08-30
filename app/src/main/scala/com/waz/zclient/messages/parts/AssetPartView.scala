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

import android.content.Context
import android.graphics._
import android.util.AttributeSet
import android.widget.LinearLayout
import com.waz.api
import com.waz.api.AssetStatus._
import com.waz.api.Message
import com.waz.api.impl.ProgressIndicator.ProgressData
import com.waz.model.{AssetId, MessageContent, MessageData}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient._
import com.waz.zclient.messages.parts.DeliveryState._
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.{GlyphProgressView, ProgressDotsDrawable}

class AssetPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private lazy val assetActionButton: AssetActionButton = findById(R.id.action_button)

  val assets = inject[AssetController]
  val message = Signal[MessageData]()

  setBackground(new AssetBackground(message.flatMap(m => assets.deliveryState(m))))

  override val tpe: MsgPart = MsgPart.Asset

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    message ! msg
    assetActionButton.message ! msg
  }
}

class AssetController(implicit inj: Injector) extends Injectable {
  val assets = inject[Signal[ZMessaging]].map(_.assets)

  def assetSignal(id: AssetId) = assets.flatMap(_.assetSignal(id))

  def deliveryState(m: MessageData) = assetSignal(m.assetId).map(a => DeliveryState(a._2, m.state))

  def downloadProgress(id: AssetId) = assets.flatMap(_.downloadProgress(id))

  def uploadProgress(id: AssetId) = assets.flatMap(_.uploadProgress(id))
}

protected sealed trait DeliveryState

protected object DeliveryState {

  case object Complete extends DeliveryState

  case object OtherUploading extends DeliveryState

  case object Uploading extends DeliveryState

  case object Downloading extends DeliveryState

  case object Failed extends DeliveryState

  case object Unknown extends DeliveryState

  def apply(as: api.AssetStatus, ms: Message.Status): DeliveryState = (as, ms) match {
    case (UPLOAD_CANCELLED | UPLOAD_FAILED | DOWNLOAD_FAILED, _) => Failed
    case (UPLOAD_NOT_STARTED | META_DATA_SENT | PREVIEW_SENT | UPLOAD_IN_PROGRESS, mState) =>
      mState match {
        case Message.Status.FAILED => Failed
        case Message.Status.SENT => OtherUploading
        case _ => Uploading
      }
    case (DOWNLOAD_IN_PROGRESS, _) => Downloading
    case (UPLOAD_DONE | DOWNLOAD_DONE, _) => Complete
    case _ => Unknown
  }
}

protected class AssetBackground(deliveryState: Signal[DeliveryState])(implicit context: WireContext, eventContext: EventContext) extends DrawableHelper {
  private val cornerRadius = ViewUtils.toPx(context, 4).toFloat

  private val backgroundPaint = new Paint
  backgroundPaint.setColor(context.getResources.getColor(R.color.light_graphite_8))

  private val dots = new ProgressDotsDrawable
  dots.setCallback(this)

  private var showDots = false
  deliveryState.map { case OtherUploading => true; case _ => false }.onChanged.on(Threading.Ui) (showDots = _)

  override def draw(canvas: Canvas): Unit = {
    canvas.drawRoundRect(new RectF(getBounds), cornerRadius, cornerRadius, backgroundPaint)
    if (showDots) dots.draw(canvas)
  }

  override def onBoundsChange(bounds: Rect): Unit = dots.setBounds(bounds)
}

class AssetActionButton(context: Context, attrs: AttributeSet, style: Int) extends GlyphProgressView(context, attrs, style) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  val assetService = inject[AssetController]

  val message = Signal[MessageData]()
  val deliveryState = message.flatMap(m => assetService.deliveryState(m))

  def normalButtonBackground = context.getResources.getDrawable(R.drawable.selector__icon_button__background__video_message)

  def errorButtonBackground = context.getResources.getDrawable(R.drawable.selector__icon_button__background__video_message__error)

  def fileDrawable = new FileDrawable(message.map(_.assetId).flatMap(id => inject[AssetController].assetSignal(id)).map(_._1.mimeType.extension))

  //TODO playback controls for audio messages
  deliveryState.map {
    case Complete => (0, fileDrawable) //TODO handle audio and video messages here
    case Uploading |
         Downloading => (R.string.glyph__close, normalButtonBackground)
    case Failed => (R.string.glyph__redo, errorButtonBackground)
    case _ => (0, null)
  }.on(Threading.Ui) {
    case (action, drawable) =>
      setText(if (action == 0) "" else context.getString(action))
      setBackground(drawable)
  }

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
}

protected class FileDrawable(ext: Signal[String])(implicit context: Context, cxt: EventContext) extends DrawableHelper {

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
}


