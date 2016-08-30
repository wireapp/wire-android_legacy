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
package com.waz.zclient.common.views

import android.content.Context
import android.content.res.TypedArray
import android.graphics._
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec
import android.view.View.MeasureSpec.{EXACTLY, makeMeasureSpec}
import com.waz.ZLog
import com.waz.api.User.ConnectionStatus
import com.waz.api.User.ConnectionStatus._
import com.waz.api.impl.AccentColor
import com.waz.api.{ContactDetails, User}
import com.waz.model.{ImageAssetData, UserData, UserId}
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapRequest.Round
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.images.BitmapSignal
import com.waz.threading.Threading
import com.waz.utils.NameParts
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}
import com.waz.zclient.utils.ContextUtils._

class ChatheadView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends View(context, attrs, defStyleAttr) with ViewHelper {

  import ChatheadView._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  private val initialsTypeface = TypefaceUtils.getTypeface(getString(R.string.chathead__user_initials__font))
  private val initialsFontColor = getColor(R.color.chathead__user_initials__font_color)
  private val iconOverlayColor = getColor(R.color.chathead__glyph__overlay_color)
  private val grayScaleColor = getColor(R.color.chathead__non_connected__color)
  private val overlayColor = getColor(R.color.text__secondary_light)

  private val a: TypedArray = context.getTheme.obtainStyledAttributes(attrs, R.styleable.ChatheadView, 0, 0)

  private val ctrl = new ChatheadController(
    a.getBoolean(R.styleable.ChatheadView_isSelectable, false),
    a.getBoolean(R.styleable.ChatheadView_show_border, true),
    Some(new Border(
      getDimen(R.dimen.chathead__min_size_large_border).toInt,
      getDimen(R.dimen.chathead__border_width).toInt,
      getDimen(R.dimen.chathead__large_border_width).toInt)),
    ColorVal(overlayColor)
  )
  private val allowIcon = a.getBoolean(R.styleable.ChatheadView_allow_icon, true)
  private val swapBackgroundAndInitialsColors = a.getBoolean(R.styleable.ChatheadView_swap_background_and_initial_colors, false)
  private val iconFontSize = a.getDimensionPixelSize(R.styleable.ChatheadView_glyph_size, getResources.getDimensionPixelSize(R.dimen.chathead__picker__glyph__font_size))
  private val initialsFontSize = a.getDimensionPixelSize(R.styleable.ChatheadView_initials_font_size, defaultInitialFontSize)
  a.recycle()

  private val initialsTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG)
  initialsTextPaint.setTextAlign(Paint.Align.CENTER)
  initialsTextPaint.setTypeface(initialsTypeface)

  private val backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG)
  backgroundPaint.setColor(Color.TRANSPARENT)

  private val iconTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG)
  iconTextPaint.setTextAlign(Paint.Align.CENTER)
  iconTextPaint.setColor(initialsFontColor)
  iconTextPaint.setTypeface(TypefaceUtils.getTypeface(TypefaceUtils.getGlyphsTypefaceName))
  iconTextPaint.setTextSize(iconFontSize)

  private val glyphOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG)
  glyphOverlayPaint.setColor(iconOverlayColor)

  private val grayScaleColorMatrix = new ColorMatrix()

  ctrl.invalidate.on(Threading.Ui)(_ => invalidate())

  ctrl.drawColors.on(Threading.Ui) { case (grayScale, accentColor) =>
    if (grayScale) {
      grayScaleColorMatrix.setSaturation(0)
      initialsTextPaint.setColor(grayScaleColor)
      backgroundPaint.setColor(grayScaleColor)
    } else {
      grayScaleColorMatrix.setSaturation(1)
      if (swapBackgroundAndInitialsColors) {
        initialsTextPaint.setColor(accentColor.value)
        backgroundPaint.setColor(initialsFontColor)
      } else {
        backgroundPaint.setColor(accentColor.value)
        initialsTextPaint.setColor(initialsFontColor)
      }
    }

    val colorMatrix = new ColorMatrixColorFilter(grayScaleColorMatrix)
    backgroundPaint.setColorFilter(colorMatrix)
    invalidate()
  }

  def setUser(user: User) = Option(user).fold(throw new IllegalArgumentException("User should not be null"))(u => setUserId(UserId(u.getId)))

  def setUserId(userId: UserId) = Option(userId).fold(throw new IllegalArgumentException("UserId should not be null"))(ctrl.assignInfo ! Left(_))

  def setContactDetails(contactDetails: ContactDetails) = Option(contactDetails).fold(throw new IllegalArgumentException("ContactDetails should not be null"))(ctrl.assignInfo ! Right(_))

  override def isSelected = {
    ctrl.selected.currentValue.getOrElse(false)
  }

  override def setSelected(selected: Boolean) = {
    ctrl.requestSelected ! selected
  }

  override def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int): Unit = {
    var width: Int = MeasureSpec.getSize(widthMeasureSpec)
    var height: Int = MeasureSpec.getSize(heightMeasureSpec)
    if (ctrl.setSelectable || allowIcon) {
      height = ((width / chatheadBottomMarginRatio) + width).toInt
    }
    else {
      val size: Int = Math.min(width, height)
      width = size
      height = size
    }

    setMeasuredDimension(width, height)
    super.onMeasure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY))
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) = {
    super.onLayout(changed, left, top, right, bottom)
    ctrl.viewWidth ! Math.min(right - left, bottom - top)
  }

  override def onDraw(canvas: Canvas): Unit = {
    val size: Float = Math.min(getWidth, getHeight)
    // This is just to prevent a really small image. Instead we want to draw just nothing
    if (size <= 1) {
      return
    }

    val borderWidth = ctrl.borderWidth.currentValue.getOrElse(0)
    val selected = ctrl.selected.currentValue.getOrElse(false)
    val connectionStatus = ctrl.connectionStatus.currentValue.getOrElse(UNCONNECTED)
    val hasBeenInvited = ctrl.hasBeenInvited.currentValue.getOrElse(false)
    val glyph = getGlyphText(selected, hasBeenInvited, connectionStatus)
    val bitmap = ctrl.bitmap.currentValue

    val radius: Float = size / 2f

    bitmap.fold {
      if (backgroundPaint.getColor != Color.TRANSPARENT) {
        drawBackgroundAndBorder(canvas, radius, borderWidth)
      }
      ctrl.initials.currentValue.foreach { initials =>
        var fontSize: Float = initialsFontSize
        if (initialsFontSize == defaultInitialFontSize) {
          fontSize = 3f * radius / 4f
        }
        initialsTextPaint.setTextSize(fontSize)
        canvas.drawText(initials, radius, getVerticalTextCenter(initialsTextPaint, radius), initialsTextPaint)
      }
    } { bitmap =>
      canvas.drawBitmap(bitmap, (size - bitmap.getWidth) / 2, 0, backgroundPaint)
    }

    // Cut out
    if (selected || !TextUtils.isEmpty(glyph)) {
      canvas.drawCircle(radius, radius, radius - borderWidth, glyphOverlayPaint)
      canvas.drawText(glyph, radius, (radius + iconTextPaint.getTextSize / 2), iconTextPaint)
    }
  }

  private def drawBackgroundAndBorder(canvas: Canvas, radius: Float, borderWidthPx: Int) = {
    if (swapBackgroundAndInitialsColors) {
      canvas.drawCircle(radius, radius, radius, initialsTextPaint)
      canvas.drawCircle(radius, radius, radius - borderWidthPx, backgroundPaint)
    }
    else {
      canvas.drawCircle(radius, radius, radius, backgroundPaint)
    }
  }

  private def getVerticalTextCenter(textPaint: Paint, cy: Float): Float = {
    return cy - ((textPaint.descent + textPaint.ascent) / 2f)
  }

  private def getGlyphText(selected: Boolean, contactHasBeenInvited: Boolean, connectionStatus: ConnectionStatus): String = {
    if (selected) {
      getResources.getString(selectedUserGlyphId)
    } else if (contactHasBeenInvited) {
      getResources.getString(pendingAddressBookContactGlyphId)
    } else {
      connectionStatus match {
        case PENDING_FROM_OTHER | PENDING_FROM_USER | IGNORED => getResources.getString(pendingUserGlyphId)
        case BLOCKED => getResources.getString(blockedUserGlyphId)
        case _ => ""
      }
    }
  }
}

object ChatheadView {

  private val selectedUserGlyphId: Int = R.string.glyph__check
  private val pendingUserGlyphId: Int = R.string.glyph__clock
  private val pendingAddressBookContactGlyphId: Int = R.string.glyph__redo
  private val blockedUserGlyphId: Int = R.string.glyph__block
  private val chatheadBottomMarginRatio: Float = 12.75f
  private val defaultInitialFontSize = -1;
}

protected class ChatheadController(val setSelectable: Boolean = false,
                                             val showBorder: Boolean = true,
                                             val border: Option[Border] = None,
                                             val contactBackgroundColor: ColorVal = ColorVal(Color.GRAY))
                                            (implicit inj: Injector, eventContext: EventContext) extends Injectable {

  private implicit val logtag = ZLog.logTagFor[ChatheadController]

  val zMessaging = inject[Signal[ZMessaging]]

  val assignInfo = Signal[Either[UserId, ContactDetails]]

  val chatheadInfo: Signal[Either[UserData, ContactDetails]] = zMessaging.zip(assignInfo).flatMap {
    case (zms, Left(userId)) => zms.usersStorage.signal(userId).map(Left(_))
    case (_, Right(contactDetails)) => Signal.const(Right(contactDetails))
  }

  val accentColor = chatheadInfo.map {
    case Left(user) => ColorVal(AccentColor(user.accent).getColor())
    case Right(contactDetails) => contactBackgroundColor
  }

  val connectionStatus = chatheadInfo.map {
    case Left(user) => user.connection
    case Right(contactDetails) => UNCONNECTED
  }

  val hasBeenInvited = chatheadInfo.map {
    case Left(user) => false
    case Right(contactDetails) => contactDetails.hasBeenInvited
  }

  val initials = chatheadInfo.map {
    case Left(user) => NameParts.parseFrom(user.name).initials
    case Right(contactDetails) => contactDetails.getInitials
  }

  val knownUser = chatheadInfo.map {
    case Left(user) => user.isConnected || user.isSelf
    case Right(contactDetails) => false
  }

  val grayScale = chatheadInfo.map {
    case Left(user) => !(user.isConnected || user.isSelf)
    case Right(contactDetails) => false
  }

  val assetId = chatheadInfo.map {
    case Left(user) => user.picture
    case Right(contactDetails) => None
  }

  val selectable = knownUser.map { knownUser =>
    knownUser && setSelectable
  }

  val requestSelected = Signal(false)

  val selected = selectable.zip(requestSelected).map {
    case (selectable, requestSelected) => selectable && requestSelected
  }

  val viewWidth = Signal(0)

  val borderWidth = viewWidth.zip(knownUser).map {
    case (viewWidth, isKnownUser) => if (showBorder && isKnownUser) border.fold(0)(_.getWidth(viewWidth)) else 0
  }

  val bitmapResult = Signal(zMessaging, assetId, viewWidth, borderWidth, accentColor).flatMap[BitmapResult] {
    case (zms, Some(id), width, bWidth, bColor) if width > 0 => zms.assetsStorage.signal(id).flatMap {
      case data: ImageAssetData => BitmapSignal(data, Round(width, bWidth, bColor.value), zms.imageLoader, zms.imageCache)
      case _ => Signal.empty[BitmapResult]
    }
    case _ => Signal.empty[BitmapResult]
  }

  val bitmap = bitmapResult.flatMap {
    case BitmapLoaded(bitmap, preview, etag) if !preview && bitmap != null => Signal(bitmap)
    case _ => Signal.empty[Bitmap]
  }

  val drawColors = grayScale.zip(accentColor)

  //Everything else that requires a redraw
  val invalidate = Signal(bitmap, selected, borderWidth).zip(Signal(initials, hasBeenInvited, connectionStatus)).onChanged
}

case class Border(val minSizeForLargeBorderWidth: Int, val smallBorderWidth: Int, val largeBorderWidth: Int) {
  def getWidth(viewWidth: Int) = {
    if (viewWidth < minSizeForLargeBorderWidth) smallBorderWidth else largeBorderWidth
  }
}

case class ColorVal(value: Int)
