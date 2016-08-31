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
import android.content.res.TypedArray
import android.graphics._
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api
import com.waz.api.AssetStatus._
import com.waz.api.Message
import com.waz.api.impl.ProgressIndicator.ProgressData
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient._
import com.waz.zclient.messages.parts.DeliveryState._
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.{GlyphProgressView, ProgressDotsDrawable}

class FileAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val assets = inject[AssetController]
  val message = Signal[MessageData]()
  val asset = assets.assetSignal(message)
  val deliveryState = DeliveryState(message, asset)
  setBackground(new AssetBackground(deliveryState))

  private lazy val assetActionButton: AssetActionButton = findById(R.id.action_button)
  private lazy val downloadedIndicator: GlyphTextView = findById(R.id.done_indicator)
  private lazy val fileNameView: TextView = findById(R.id.file_name)
  private lazy val fileInfoView: TextView = findById(R.id.file_info)


  asset.map(_._1.name.getOrElse("")).on(Threading.Ui)(fileNameView.setText)
  asset.map(_._2).map(_ == DOWNLOAD_DONE).map { case true => View.VISIBLE; case false => View.GONE }.on(Threading.Ui)(downloadedIndicator.setVisibility)

  //TODO fileInfo strings - pretty big mess...


  override val tpe: MsgPart = MsgPart.Asset

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    message ! msg
    assetActionButton.message ! msg
  }
}

class AssetController(implicit inj: Injector) extends Injectable {
  val assets = inject[Signal[ZMessaging]].map(_.assets)

  def assetSignal(mes: Signal[MessageData]) = mes.flatMap(m => assets.flatMap(_.assetSignal(m.assetId)))

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

  private def apply(as: api.AssetStatus, ms: Message.Status): DeliveryState = (as, ms) match {
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

  def apply(message: Signal[MessageData], asset: Signal[(AnyAssetData, api.AssetStatus)]): Signal[DeliveryState] =
    message.zip(asset).map { case (m, (_, s)) => apply(s, m.state) }
}

protected class AssetBackground(deliveryState: Signal[DeliveryState])(implicit context: WireContext, eventContext: EventContext) extends DrawableHelper {
  private val cornerRadius = ViewUtils.toPx(context, 4).toFloat

  private val backgroundPaint = new Paint
  backgroundPaint.setColor(getColor(R.color.light_graphite_8))

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

class AssetActionButton(context: Context, attrs: AttributeSet, style: Int) extends GlyphProgressView(context, attrs, style) with ViewHelper with View.OnClickListener {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  private val a: TypedArray = context.getTheme.obtainStyledAttributes(attrs, R.styleable.AssetActionButton, 0, 0)
  private val isFileType = a.getBoolean(R.styleable.AssetActionButton_isFileType, false)

  val assetService = inject[AssetController]
  val message = Signal[MessageData]()

  val asset = assetService.assetSignal(message)
  val deliveryState = DeliveryState(message, asset)

  private val normalButtonDrawable = getDrawable(R.drawable.selector__icon_button__background__video_message)
  private val errorButtonDrawable = getDrawable(R.drawable.selector__icon_button__background__video_message__error)
  private val onCompletedDrawable = if (isFileType) new FileDrawable(asset.map(_._1.mimeType.extension)) else normalButtonDrawable

  //TODO playback controls for audio messages
  deliveryState.map {
    case Complete => (if (isFileType) 0 else R.string.glyph__play, onCompletedDrawable)
    case Uploading |
         Downloading => (R.string.glyph__close, normalButtonDrawable)
    case Failed => (R.string.glyph__redo, errorButtonDrawable)
    case _ => (0, null)
  }.on(Threading.Ui) {
    case (action, drawable) =>
      setText(if (action == 0) "" else getString(action))
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

  //TODO handle click events
  override def onClick(v: View): Unit = {
    verbose("AssetButton clicked")
  }
}

protected class FileDrawable(ext: Signal[String])(implicit context: Context, cxt: EventContext) extends DrawableHelper {

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
}


