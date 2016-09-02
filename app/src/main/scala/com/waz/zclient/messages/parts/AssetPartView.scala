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

import android.app.DownloadManager
import android.content.res.TypedArray
import android.content.{Context, Intent}
import android.graphics._
import android.net.Uri
import android.support.v7.app.AppCompatDialog
import android.text.TextUtils
import android.text.format.Formatter
import android.util.{AttributeSet, TypedValue}
import android.view.{Gravity, View}
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget._
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api
import com.waz.api.AssetStatus._
import com.waz.api.Message
import com.waz.api.impl.ProgressIndicator.ProgressData
import com.waz.model.AssetMetaData.HasDuration
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.assets.GlobalRecordAndPlayService
import com.waz.service.assets.GlobalRecordAndPlayService.{AssetMediaKey, Content, UnauthenticatedContent}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.messages.parts.DeliveryState._
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{AssetUtils, RichSeekBar, RichView, StringUtils, ViewUtils}
import com.waz.zclient.views.ImageAssetDrawable.State.Loaded
import com.waz.zclient.views.ImageController.WireImage
import com.waz.zclient.views.{GlyphProgressView, ImageAssetDrawable, ProgressDotsDrawable}
import com.waz.zclient.{R, _}
import org.threeten.bp.Duration

import scala.util.Success

abstract class AssetPartView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  val zms = inject[Signal[ZMessaging]]
  val assets = inject[AssetController]
  val message = Signal[MessageData]()
  val asset = assets.assetSignal(message)
  val assetId = asset.map(_._1.id)
  val duration = asset.map(_._1).map {
    case AnyAssetData(_, _, _, _, _, Some(HasDuration(d)), _, _, _, _, _) => d
    case _ => Duration.ZERO
  }
  val formattedDuration = duration.map(d => StringUtils.formatTimeSeconds(d.getSeconds))

  val deliveryState = DeliveryState(message, asset)
  val actionReady = deliveryState.map { case Complete => true; case _ => false }
  val progressDots = new AssetBackground(deliveryState.map { case OtherUploading => true; case _ => false })
  setBackground(progressDots)

  def inflate(): Unit
  inflate()
  private val content: View = findById(R.id.content)

  protected val assetActionButton: AssetActionButton = findById(R.id.action_button)
  //toggle content visibility to show only progress dot background if other side is uploading asset
  deliveryState.map {
    case OtherUploading => false
    case _ => true
  }.on(Threading.Ui)(content.setVisible)

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    message ! msg
    assetActionButton.message ! msg
  }
}

class FileAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends AssetPartView(context, attrs, style) {
  self =>
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.FileAsset
  override def inflate() = inflate(R.layout.message_file_asset_content)

  private val downloadedIndicator: GlyphTextView = findById(R.id.done_indicator)
  private val fileNameView: TextView = findById(R.id.file_name)
  private val fileInfoView: TextView = findById(R.id.file_info)

  asset.map(_._1.name.getOrElse("")).on(Threading.Ui)(fileNameView.setText)
  asset.map(_._2).map(_ == DOWNLOAD_DONE).map { case true => View.VISIBLE; case false => View.GONE }.on(Threading.Ui)(downloadedIndicator.setVisibility)

  val sizeAndExt = asset.map {
    case (a, _) =>
      val size = if (a.sizeInBytes <= 0) None else Some(Formatter.formatFileSize(context, a.sizeInBytes))
      val ext = Option(a.mimeType.extension).map(_.toUpperCase(Locale.getDefault))
      (size, ext)
  }

  deliveryState.map {
    case Uploading        => (R.string.content__file__status__uploading__minimized,         R.string.content__file__status__uploading,        R.string.content__file__status__uploading__size_and_extension)
    case Downloading      => (R.string.content__file__status__downloading__minimized,       R.string.content__file__status__downloading,      R.string.content__file__status__downloading__size_and_extension)
    case Cancelled        => (R.string.content__file__status__cancelled__minimized,         R.string.content__file__status__cancelled,        R.string.content__file__status__cancelled__size_and_extension)
    case UploadFailed     => (R.string.content__file__status__upload_failed__minimized,     R.string.content__file__status__upload_failed,    R.string.content__file__status__upload_failed__size_and_extension)
    case DownloadFailed   => (R.string.content__file__status__download_failed__minimized,   R.string.content__file__status__download_failed,  R.string.content__file__status__download_failed__size_and_extension)
    case Complete         => (R.string.content__file__status__default,                      R.string.content__file__status__default,          R.string.content__file__status__default__size_and_extension)
    case _                => (0, 0, 0)
  }.zip(sizeAndExt).map {
    case ((min, dfault, full), sAndE) =>
      sAndE match {
        case (Some(size), Some(ext))  => getStringOrEmpty(full, size, ext)
        case (None, Some(ext))        => getStringOrEmpty(dfault, ext)
        case _                        => getStringOrEmpty(min)
      }
  }.on(Threading.Ui) (fileInfoView.setText)

  actionReady.on(Threading.Ui) {
    case true =>
      assetActionButton.onClick {
        for {
          id <- assetId.currentValue
          as <- zms.map(_.assets).currentValue
          a <- asset.map(_._1).currentValue
          mime <- asset.map(_._1.mimeType).currentValue
        } {
          as.getAssetUri(id).foreach {
            case Some(uri) =>
              val intent = AssetUtils.getOpenFileIntent(uri, mime.orDefault.str)
              val fileCanBeOpened = AssetUtils.fileTypeCanBeOpened(context.getPackageManager, intent)

              //TODO tidy up
              //TODO there is also a weird flash or double-dialog issue when you click outside of the dialog
              val dialog = new AppCompatDialog(context)
              a.name.foreach(dialog.setTitle)
              dialog.setContentView(R.layout.file_action_sheet_dialog)

              val title = dialog.findViewById(R.id.title).asInstanceOf[TextView]
              title.setEllipsize(TextUtils.TruncateAt.MIDDLE)
              title.setTypeface(TypefaceUtils.getTypeface(getString(R.string.wire__typeface__medium)))
              title.setTextSize(TypedValue.COMPLEX_UNIT_PX, getDimenPx(R.dimen.wire__text_size__regular))
              title.setGravity(Gravity.CENTER)

              val openButton = dialog.findViewById(R.id.ttv__file_action_dialog__open).asInstanceOf[TextView]
              val noAppFoundLabel = dialog.findViewById(R.id.ttv__file_action_dialog__open__no_app_found)
              val saveButton = dialog.findViewById(R.id.ttv__file_action_dialog__save)

              if (fileCanBeOpened) {
                noAppFoundLabel.setVisibility(View.GONE)
                openButton.setAlpha(1f)
                openButton.setOnClickListener(new View.OnClickListener() {
                  def onClick(v: View) = {
                    context.startActivity(intent)
                    dialog.dismiss()
                  }
                })
              }
              else {
                noAppFoundLabel.setVisibility(View.VISIBLE)
                val disabledAlpha = getResourceFloat(R.dimen.button__disabled_state__alpha)
                openButton.setAlpha(disabledAlpha)
              }

              saveButton.setOnClickListener(new View.OnClickListener() {
                def onClick(v: View) = {
                  dialog.dismiss()
                  as.saveAssetToDownloads(id).onComplete {
                    case Success(Some(file)) =>
                      val uri = Uri.fromFile(file)
                      val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE).asInstanceOf[DownloadManager]
                      downloadManager.addCompletedDownload(a.name.get, a.name.get, false, a.mimeType.orDefault.str, uri.getPath, a.sizeInBytes, true)
                      Toast.makeText(context, com.waz.zclient.ui.R.string.content__file__action__save_completed, Toast.LENGTH_SHORT).show()

                      val intent: Intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                      intent.setData(uri)
                      context.sendBroadcast(intent)
                    case _ =>
                  } (Threading.Ui)
                }
              })

              dialog.show()
            case _ =>
          }(Threading.Ui)
        }
      }

    case false =>
  }
}

class AudioAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends AssetPartView(context, attrs, style) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.AudioAsset
  override def inflate() = inflate(R.layout.message_audio_asset_content)

  private val durationView: TextView = findById(R.id.duration)
  private val progressBar: SeekBar = findById(R.id.progress)

  val accentColorController = inject[AccentColorController]

  accentColorController.accentColor.map(_.getColor).on(Threading.Ui)(progressBar.setColor)

  val playControls = new PlaybackControls(asset.map(_._1))

  formattedDuration.on(Threading.Ui)(durationView.setText)
  duration.map(_.toMillis.toInt).on(Threading.Ui)(progressBar.setMax)
  playControls.playHead.map(_.toMillis.toInt).on(Threading.Ui)(progressBar.setProgress)

  actionReady.on(Threading.Ui) {
    case true =>
      progressBar.setEnabled(true)
      assetActionButton.listenToPlayState(playControls.isPlaying)
      assetActionButton.onClick {
        playControls.playOrPause()
      }

      progressBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener {
        override def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean): Unit =
        if (fromUser) playControls.setPlayHead(Duration.ofMillis(progress))

        override def onStopTrackingTouch(seekBar: SeekBar): Unit = ()

        override def onStartTrackingTouch(seekBar: SeekBar): Unit = ()
      })

    case false => progressBar.setEnabled(false)
  }

}

class VideoAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends AssetPartView(context, attrs, style) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.VideoAsset
  override def inflate() = inflate(R.layout.message_video_asset_content)

  private val durationView: TextView = findById(R.id.duration)
  inflate(R.layout.message_video_asset_content)

  private val imageDrawable = new ImageAssetDrawable(assetId.map(WireImage))

  imageDrawable.state.map {
    case Loaded(_, _) => getColor(R.color.white)
    case _ => getColor(R.color.black)
  }.on(Threading.Ui)(durationView.setTextColor)

  deliveryState.on(Threading.Ui) {
    case OtherUploading => setBackground(progressDots)
    case _ =>
      setBackground(imageDrawable)
      imageDrawable.state.on(Threading.Ui) {
        case ImageAssetDrawable.State.Failed(_) => setBackground(progressDots)
        case _ =>
      }
  }

  //TODO there is more logic for what text to display, but it doesn't seem to be used - confirm
  formattedDuration.on(Threading.Ui)(durationView.setText)

  actionReady.on(Threading.Ui) {
    case true =>
      assetActionButton.onClick {
        for {
          id <- assetId.currentValue
          as <- zms.map(_.assets).currentValue
          mime <- asset.map(_._1.mimeType).currentValue
        } {
          as.getAssetUri(id).foreach {
            case Some(uri) =>
              val intent = AssetUtils.getOpenFileIntent(uri, mime.orDefault.str)
              context.startActivity(intent)
            case _ =>
          } (Threading.Ui)
        }
      }
    case _ =>
  }

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    super.set(pos, msg, part, widthHint)

    val Dim2(w, h) = msg.imageDimensions.getOrElse(Dim2(1, 1))
    val margin = if (h > w) getDimenPx(R.dimen.content__padding_left) else 0
    val displayWidth = widthHint - 2 * margin
    val height = (h * (displayWidth.toDouble / w)).toInt

    val pms = new LinearLayout.LayoutParams(displayWidth, height)
    pms.setMargins(margin, 0, margin, 0)
    setLayoutParams(pms)
  }
}

class PlaybackControls(asset: Signal[AnyAssetData])(implicit injector: Injector) extends Injectable {
  val zms = inject[Signal[ZMessaging]]
  val rAndP = zms.map(_.global.recordingAndPlayback)

  val isPlaying = rAndP.zip(asset).flatMap { case (rP, a) => rP.isPlaying(AssetMediaKey(a.id)) }
  val playHead = rAndP.zip(asset).flatMap { case (rP, a) => rP.playhead(AssetMediaKey(a.id)) }

  private def rPAction(f: (GlobalRecordAndPlayService, AssetMediaKey, Content, Boolean) => Unit): Unit = {
    for {
      as <- zms.map(_.assets).currentValue
      rP <- rAndP.currentValue
      id <- asset.map(_.id).currentValue
      isPlaying <- isPlaying.currentValue
    } {
      as.getAssetUri(id).foreach {
        case Some(uri) => f(rP, AssetMediaKey(id), UnauthenticatedContent(uri), isPlaying)
        case None =>
      }(Threading.Background)
    }
  }

  def playOrPause() = rPAction { case (rP, key, content, playing) => if (playing) rP.pause(key) else rP.play(key, content) }

  def setPlayHead(duration: Duration) = rPAction { case (rP, key, content, playing) => rP.setPlayhead(key, content, duration) }
}

class AssetController(implicit inj: Injector) extends Injectable {

  val zms = inject[Signal[ZMessaging]]
  val assets = zms.map(_.assets)
  val messages = zms.map(_.messages)

  def assetSignal(mes: Signal[MessageData]) = mes.flatMap(m => assets.flatMap(_.assetSignal(m.assetId)))

  def downloadProgress(id: AssetId) = assets.flatMap(_.downloadProgress(id))

  def uploadProgress(id: AssetId) = assets.flatMap(_.uploadProgress(id))

  def cancelUpload(m: MessageData) = assets.currentValue.foreach(_.cancelUpload(m.assetId, m.id))

  def cancelDownload(m: MessageData) = assets.currentValue.foreach(_.cancelDownload(m.assetId))

  def retry(m: MessageData) = if (m.state == Message.Status.FAILED || m.state == Message.Status.FAILED_READ) messages.currentValue.foreach(_.retryMessageSending(m.convId, m.id))
}

protected sealed trait DeliveryState

protected object DeliveryState {

  case object Complete extends DeliveryState

  case object OtherUploading extends DeliveryState

  case object Uploading extends DeliveryState

  case object Downloading extends DeliveryState

  case object Cancelled extends DeliveryState

  trait Failed extends DeliveryState

  case object UploadFailed extends Failed

  case object DownloadFailed extends Failed

  case object Unknown extends DeliveryState

  private def apply(as: api.AssetStatus, ms: Message.Status): DeliveryState = {
    val res = (as, ms) match {
      case (UPLOAD_CANCELLED, _) => Cancelled
      case (UPLOAD_FAILED, _) => UploadFailed
      case (DOWNLOAD_FAILED, _) => DownloadFailed
      case (UPLOAD_NOT_STARTED | META_DATA_SENT | PREVIEW_SENT | UPLOAD_IN_PROGRESS, mState) =>
        mState match {
          case Message.Status.FAILED => UploadFailed
          case Message.Status.SENT => OtherUploading
          case _ => Uploading
        }
      case (DOWNLOAD_IN_PROGRESS, _) => Downloading
      case (UPLOAD_DONE | DOWNLOAD_DONE, _) => Complete
      case _ => Unknown
    }
    verbose(s"Mapping Asset.Status: $as, and Message.Status $ms to DeliveryState: $res")
    res
  }

  def apply(message: Signal[MessageData], asset: Signal[(AnyAssetData, api.AssetStatus)]): Signal[DeliveryState] =
    message.zip(asset).map { case (m, (_, s)) => apply(s, m.state) }
}

protected class AssetBackground(showDots: Signal[Boolean])(implicit context: WireContext, eventContext: EventContext) extends DrawableHelper {
  private val cornerRadius = ViewUtils.toPx(context, 4).toFloat

  private val backgroundPaint = new Paint
  backgroundPaint.setColor(getColor(R.color.light_graphite_8))

  private val dots = new ProgressDotsDrawable
  dots.setCallback(this)

  showDots.onChanged.on(Threading.Ui)(_ => invalidateSelf())

  override def draw(canvas: Canvas): Unit = {
    canvas.drawRoundRect(new RectF(getBounds), cornerRadius, cornerRadius, backgroundPaint)
    if (showDots.currentValue.getOrElse(false)) dots.draw(canvas)
  }

  override def onBoundsChange(bounds: Rect): Unit = dots.setBounds(bounds)
}

class AssetActionButton(context: Context, attrs: AttributeSet, style: Int) extends GlyphProgressView(context, attrs, style) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  private val a: TypedArray = context.getTheme.obtainStyledAttributes(attrs, R.styleable.AssetActionButton, 0, 0)
  private val isFileType = a.getBoolean(R.styleable.AssetActionButton_isFileType, false)

  val zms = inject[Signal[ZMessaging]]
  val assets = zms.map(_.assets)
  val assetService = inject[AssetController]
  val message = Signal[MessageData]()
  val accentController = inject[AccentColorController]

  val asset = assetService.assetSignal(message)
  val deliveryState = DeliveryState(message, asset)
  private val normalButtonDrawable = getDrawable(R.drawable.selector__icon_button__background__video_message)

  private val errorButtonDrawable = getDrawable(R.drawable.selector__icon_button__background__video_message__error)
  private val onCompletedDrawable = if (isFileType) new FileDrawable(asset.map(_._1.mimeType.extension)) else normalButtonDrawable

  accentController.accentColor.map(_.getColor).on(Threading.Ui) (setProgressColor)

  deliveryState.map {
    case Complete => (if (isFileType) 0 else R.string.glyph__play, onCompletedDrawable)
    case Uploading |
         Downloading => (R.string.glyph__close, normalButtonDrawable)
    case _ : Failed | Cancelled => (R.string.glyph__redo, errorButtonDrawable)
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


