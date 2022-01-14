/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.common.views

import android.content.Context
import android.graphics._
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.{CenterCrop, CircleCrop}
import com.bumptech.glide.request.RequestOptions
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.Picture
import com.waz.model._
import com.waz.service.ZMessaging
import com.wire.signals.Signal
import com.waz.utils.{NameParts, returning}
import com.waz.zclient.common.views.ChatHeadView._
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.glide.transformations.{GlyphOverlayTransformation, GreyScaleTransformation, IntegrationBackgroundCrop}
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils.{getColor, getString}
import com.waz.zclient.{R, ViewHelper}
import com.waz.threading.Threading._

class ChatHeadView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends ImageView(context, attrs, defStyleAttr) with ViewHelper with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private val zms = inject[Signal[ZMessaging]]
  private val userId = Signal[Option[UserId]]()
  val attributes: Attributes = parseAttributes(attrs)

  private val options = for {
    z         <- zms
    Some(uId) <- userId
    user      <- z.usersStorage.signal(uId)
  } yield optionsForUser(user, z.teamId.exists(user.teamId.contains(_)), attributes)

  options.onUi(setInfo)

  def parseAttributes(attributeSet: AttributeSet): Attributes = {
    val a = context.getTheme.obtainStyledAttributes(attributeSet, R.styleable.ChatHeadView, 0, 0)

    val isRound = a.getBoolean(R.styleable.ChatHeadView_is_round, true)
    val showWaiting = a.getBoolean(R.styleable.ChatHeadView_show_waiting, true)
    val grayScaleOnConnected = a.getBoolean(R.styleable.ChatHeadView_gray_on_unconnected, true)
    val defaultBackground = a.getColor(R.styleable.ChatHeadView_default_background, Color.TRANSPARENT)
    val allowIcon = a.getBoolean(R.styleable.ChatHeadView_allow_icon, true)

    Attributes(isRound, showWaiting  && allowIcon, grayScaleOnConnected && allowIcon, defaultBackground)
  }

  def loadUser(userId: UserId): Unit =
    this.userId ! Some(userId)

  def setUserData(userData: UserData, belongsToSelfTeam: Boolean): Unit = {
    setInfo(optionsForUser(userData, belongsToSelfTeam, attributes))
    loadUser(userData.id)
  }

  def clearImage(): Unit = {
    WireGlide(context).clear(this)
    setImageDrawable(null)
  }

  def clearUser(): Unit = {
    clearImage()
    this.userId ! None
  }

  def setIntegration(integration: IntegrationData): Unit =
    setInfo(optionsForIntegration(integration, attributes))

  def setInfo(options: ChatHeadViewOptions): Unit =
    if (options.picture.isEmpty) {
      WireGlide(context).clear(this)
      setImageDrawable(options.placeholder)
    } else
      options.glideRequest.into(this)

  private def optionsForIntegration(integration: IntegrationData, attributes: Attributes): ChatHeadViewOptions =
    ChatHeadViewOptions(
      integration.asset.map(PictureUploaded),
      attributes.defaultBackground,
      grayScale = false,
      NameParts.parseFrom(integration.name).initials,
      cropShape = Some(CropShape.RoundRect),
      None)

  private def optionsForUser(user: UserData, teamMember: Boolean, attributes: Attributes): ChatHeadViewOptions = {
    val backgroundColor = AccentColor.apply(user.accent).color
    val greyScale = !(user.isConnected || user.isSelf || user.isWireBot || teamMember) && attributes.greyScaleOnConnected
    val initials = NameParts.parseFrom(user.name).initials
    val icon =
      if (user.isBlocked)
        Some(OverlayIcon.Blocked)
      else if (pendingConnectionStatuses.contains(user.connection) && attributes.showWaiting && !user.isWireBot)
        Some(OverlayIcon.Waiting)
      else
        None
    val shape =
      if (user.isWireBot && attributes.isRound)
        Some(CropShape.RoundRect)
      else if (attributes.isRound)
        Some(CropShape.Circle)
      else
        None

    ChatHeadViewOptions(user.picture, backgroundColor, greyScale, initials, shape, icon)
  }
}

object ChatHeadView {
  private val pendingConnectionStatuses = Set(ConnectionStatus.PendingFromUser, ConnectionStatus.PendingFromOther)

  final case class Attributes(isRound: Boolean,
                              showWaiting: Boolean,
                              greyScaleOnConnected: Boolean,
                              defaultBackground: Int)

  object OverlayIcon extends Enumeration {
    val Waiting, Blocked = Value
  }
  type OverlayIcon = OverlayIcon.Value

  object CropShape extends Enumeration {
    val RoundRect, Circle = Value
  }
  type CropShape = CropShape.Value

  final case class ChatHeadViewOptions(picture: Option[Picture],
                                       backgroundColor: Int,
                                       grayScale: Boolean,
                                       initials: String,
                                       cropShape: Option[CropShape],
                                       icon: Option[OverlayIcon])(implicit context: Context) extends DerivedLogTag {
    lazy val placeholder = ChatHeadViewPlaceholder(backgroundColor, initials, cropShape = cropShape, reversedColors = cropShape.isEmpty)

    def glideRequest(implicit context: Context): RequestBuilder[Drawable] = {
      val request = picture match {
        case Some(p) => WireGlide(context).load(p)
        case _       => WireGlide(context).load(placeholder)
      }
      val requestOptions = new RequestOptions()

      requestOptions.placeholder(placeholder)
      val transformations = Seq.newBuilder[Transformation[Bitmap]]

      if (grayScale) transformations += new GreyScaleTransformation()
      transformations += new CenterCrop()
      icon.map {
        case OverlayIcon.Waiting => R.string.glyph__clock
        case OverlayIcon.Blocked => R.string.glyph__block
      }.foreach(g => transformations += new GlyphOverlayTransformation(getString(g)))
      cropShape.foreach { cs =>
        transformations += (cs match {
          case CropShape.Circle => new CircleCrop()
          case CropShape.RoundRect => new IntegrationBackgroundCrop()
        })
      }

      val transformationsResult = transformations.result()
      if (transformationsResult.nonEmpty)
        requestOptions.transform(transformationsResult:_*)
      request.apply(requestOptions)
    }
  }

  final case class ChatHeadViewPlaceholder(color: Int,
                                           text: String,
                                           cropShape: Option[CropShape],
                                           reversedColors: Boolean = false)(implicit context: Context) extends Drawable {
    private val textPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { p =>
      p.setTextAlign(Paint.Align.CENTER)
      val tf = TypefaceUtils.getTypeface(getString(if (reversedColors) R.string.wire__typeface__medium else R.string.wire__typeface__light))
      p.setTypeface(tf)
      p.setColor(if (reversedColors) color else Color.WHITE)
    }

    private val backgroundPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) {
      _.setColor(if (reversedColors) getColor(R.color.black_16) else color)
    }

    override def draw(canvas: Canvas): Unit = {
      val radius = Math.min(getBounds.width(), getBounds.height())  / 2

      cropShape match {
        case Some(CropShape.Circle) =>
          canvas.drawCircle(getBounds.centerX(), getBounds.centerY(), radius, backgroundPaint)
        case Some(CropShape.RoundRect) =>
          canvas.drawRoundRect(new RectF(getBounds), radius * 0.4f, radius * 0.4f, backgroundPaint)
        case _ =>
          canvas.drawPaint(backgroundPaint)
      }

      textPaint.setTextSize(radius / 1.1f)
      val y = getBounds.centerY() - ((textPaint.descent + textPaint.ascent) / 2f)
      val x = getBounds.centerX()
      canvas.drawText(text, x, y, textPaint)
    }

    override def setAlpha(alpha: Int): Unit = {
      backgroundPaint.setAlpha(alpha)
      invalidateSelf()
    }

    override def setColorFilter(colorFilter: ColorFilter): Unit = {
      backgroundPaint.setColorFilter(colorFilter)
      invalidateSelf()
    }

    override def getOpacity: Int = backgroundPaint.getAlpha
  }

}
