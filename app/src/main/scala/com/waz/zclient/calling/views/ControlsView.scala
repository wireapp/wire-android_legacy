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
package com.waz.zclient.calling.views

import android.animation.{Animator, AnimatorListenerAdapter, AnimatorSet, ValueAnimator}
import android.content.Context
import android.graphics._
import android.support.annotation.NonNull
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.View.GONE
import android.view._
import android.widget.{FrameLayout, LinearLayout}
import com.facebook.rebound._
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.api.VideoSendState._
import com.waz.api.impl.AccentColor
import com.waz.model.AssetData
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.images.BitmapSignal
import com.waz.threading.Threading
import com.waz.ui.MemoryImageCache.BitmapRequest.Round
import com.waz.utils.NameParts
import com.waz.utils.events.{EventStream, Signal}
import com.waz.zclient.calling.controllers.{CallPermissionsController, CurrentCallController}
import com.waz.zclient.ui.animation.interpolators.penner.{Expo, Quart}
import com.waz.zclient.ui.text.TypefaceFactory
import com.waz.zclient.ui.utils.{ResourceUtils, TypefaceUtils}
import com.waz.zclient.{R, ViewHelper}

class ControlsView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {

  implicit val logTag = ZLog.logTagFor[ControlsView]

  import com.waz.zclient.utils.RichView

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  val onClickEvent = EventStream[Unit]

  lazy val controller = inject[CurrentCallController]

  LayoutInflater.from(context).inflate(R.layout.calling_controls, this, true)
  private val outgoingControls: ViewGroup = findById(R.id.ocl_outgoing_controls)
  private val incomingControls: ViewGroup = findById(R.id.icl_incoming_controls)

  outgoingControls.onClick(onClickEvent ! (()))

  controller.showOngoingControls.on(Threading.Ui) { s =>
    incomingControls.setVisible(!s)
    outgoingControls.setVisible(s)
  }
}

private[calling] class OutgoingControlsView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {

  import com.waz.zclient.utils.RichView

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  LayoutInflater.from(context).inflate(R.layout.calling__controls__ongoing, this, true)
  setOrientation(LinearLayout.HORIZONTAL)

  val leftButton:   CallControlButtonView = findById(R.id.ccbv__button_left)
  val middleButton: CallControlButtonView = findById(R.id.ccbv__button_middle)
  val rightButton:  CallControlButtonView = findById(R.id.ccbv__button_right)
  val rightSpacer:  View                  = findById(R.id.v__right_view_spacer)

  val controller = inject[CurrentCallController]

  import controller._
  import controller.glob._

  leftButtonSettings.on(Threading.Ui)(_.set(leftButton, this))
  middleButtonSettings.on(Threading.Ui)(_.set(middleButton, this))
  rightButtonSettings.on(Threading.Ui)(_.set(rightButton, this))

  rightButtonShown.on(Threading.Ui) { v =>
    rightSpacer.setVisible(v)
    rightButton.setVisible(v)
  }

  muted.on(Threading.Ui)(leftButton.setButtonPressed)

  Signal(videoCall, speakerButton, videoSendState).map {
    case (true, _, videoSendState) => videoSendState == SEND
    case (false, speakerEnabled, _) => speakerEnabled
  }.on(Threading.Ui)(rightButton.setButtonPressed)
}

private class IncomingControlsView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {

  import IncomingControlsView._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  lazy val callController = inject[CurrentCallController]
  lazy val callPermissionsController = inject[CallPermissionsController]

  private val glyphSize = getResources.getDimensionPixelSize(R.dimen.wire__icon_button__text_size)
  private val textColor = ContextCompat.getColor(getContext, R.color.text__primary_dark)
  private val lightTypeface = TypefaceFactory.getInstance.getTypeface(getResources.getString(R.string.wire__typeface__light))
  private val smallTextSize = getResources.getDimensionPixelSize(R.dimen.wire__text_size__small)

  private val leftButtonGlyphString = getResources.getString(R.string.glyph__end_call)
  private val leftButtonColor = ContextCompat.getColor(getContext, R.color.accent_red)
  private val leftButtonPressedColor = ContextCompat.getColor(getContext, R.color.draw_light_red)
  private val rightButtonColor = ContextCompat.getColor(getContext, R.color.accent_green)
  private val rightButtonPressedColor = ContextCompat.getColor(getContext, R.color.draw_light_green)

  private val array = context.getTheme.obtainStyledAttributes(attrs, R.styleable.IncomingControlsLayout, 0, 0)
  private val buttonRadius = array.getDimensionPixelSize(R.styleable.IncomingControlsLayout_buttonDiameter, 0) / 2
  private val avatarRadius = array.getDimensionPixelSize(R.styleable.IncomingControlsLayout_avatarDiameter, 0) / 2
  private val expandedButtonRadius = array.getDimensionPixelSize(R.styleable.IncomingControlsLayout_buttonExpandedDiameter, 0) / 2
  private val edgeToEdgeDistance = array.getDimensionPixelSize(R.styleable.IncomingControlsLayout_edgeToEdgeWidth, 0)
  array.recycle

  private val attractionRadius = buttonRadius * ATTRACTION_FACTOR_THRESHOLD

  private val glyphPaint = new Paint
  glyphPaint.setColor(textColor)
  glyphPaint.setTypeface(TypefaceFactory.getInstance.getTypeface(TypefaceUtils.getGlyphsTypefaceName))
  glyphPaint.setTextSize(smallTextSize)

  private val initialsPaint = new Paint
  initialsPaint.setColor(textColor)
  initialsPaint.setTypeface(lightTypeface)
  initialsPaint.setTextSize((2 * buttonRadius * ResourceUtils.getResourceFloat(getResources, R.dimen.wire__text_size__regular)).toInt)

  private val centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG)
  centerPaint.setStyle(Paint.Style.FILL)

  private val buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG)

  private val avatarPositionSpringListener = new AvatarPositionSpringListener
  private val springSystem = SpringSystem.create
  springSystem.addListener(avatarPositionSpringListener)

  private val xSpring = springSystem.createSpring.addListener(avatarPositionSpringListener)
  private val ySpring = springSystem.createSpring.addListener(avatarPositionSpringListener)

  private val sizeSpring = SpringSystem.create.createSpring.addListener(new TargetSizeSpringListener)

  private val targetIndicatorSpring = SpringSystem.create.createSpring.addListener(new TargetIndicatorSpringListener)
  targetIndicatorSpring.setSpringConfig(INDICATING)

  private var otherTargetAlpha = 1f
  private var targetScale = 1f
  private var avatarAnimationValue = 1f
  private var avatarRect = new RectF

  private val leftTarget = new PointF
  private val rightTarget = new PointF
  private val targets = Vector(leftTarget, rightTarget)

  private val current = new PointF
  private val last = new PointF

  private var droppedTarget: PointF = null
  private var scaleFactor = INIT_SCALE
  private var velocityTracker: VelocityTracker = null

  private var dragging = false
  private var inTarget = false
  private var centerPoint = new PointF

  private var textRect = new Rect

  private val allowClickOnActionButtons = callController.glob.wasUiActiveOnCallStart

  setBackgroundColor(Color.TRANSPARENT)

  val rightButtonGlyphSignal = callController.glob.videoCall map {
    case true => getResources.getString(R.string.glyph__video)
    case false => getResources.getString(R.string.glyph__call)
  }

  val bitmapSignal = callController.glob.zms.zip(callController.glob.selfUser).flatMap {
    case (zms, selfUser) =>
      selfUser.picture.fold {
        Signal.const[Option[BitmapResult]](None)
      } { assetId =>
        zms.assetsStorage.signal(assetId).flatMap {
          case data@AssetData.IsImage() => BitmapSignal(data, Round(avatarRadius * 2, 0, Color.TRANSPARENT), zms.imageLoader, zms.imageCache).map(Option(_))
          case _ => Signal.const[Option[BitmapResult]](None)
        }
      }
  }.map {
    case Some(BitmapLoaded(bitmap, etag)) if bitmap != null => Some(bitmap)
    case _ => None
  }

  val userInitials = callController.glob.selfUser.map(data => NameParts.parseFrom(data.name).initials)

  val accentColor = callController.glob.selfUser.map(data => AccentColor(data.accent).getColor())

  Signal(rightButtonGlyphSignal, userInitials, accentColor, bitmapSignal).on(Threading.Ui) { _ =>
    invalidate()
  }

  protected override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) = {
    val centerY: Float = getHeight - getPaddingBottom - Math.min(buttonRadius, avatarRadius)
    val centerX: Float = getWidth / 2f
    leftTarget.x = (getWidth / 2) - (edgeToEdgeDistance / 2) + buttonRadius
    leftTarget.y = centerY
    rightTarget.x = (getWidth / 2) + (edgeToEdgeDistance / 2) - buttonRadius
    rightTarget.y = centerY
    centerPoint.x = centerX
    centerPoint.y = centerY
    xSpring.setCurrentValue(centerPoint.x).setAtRest
    ySpring.setCurrentValue(centerPoint.y).setAtRest
    super.onLayout(changed, left, top, right, bottom)
  }

  protected override def onDraw(canvas: Canvas) = {
    super.onDraw(canvas)

    drawTarget(leftTarget, canvas, touchDownOnLeftAction, leftButtonPressedColor, leftButtonColor, leftButtonGlyphString)
    drawTarget(rightTarget, canvas, touchDownOnRightAction, rightButtonPressedColor, rightButtonColor, rightButtonGlyphSignal.currentValue.getOrElse(""))

    val avatarAlpha: Int = (255 * avatarAnimationValue).toInt
    val avatarRadius: Float = this.avatarRadius * avatarAnimationValue
    val initialsTextSize: Float = getResources.getDimensionPixelSize(R.dimen.calling__button__glyph__size) * avatarAnimationValue

    bitmapSignal.currentValue.foreach {
      _.fold {
        centerPaint.setAlpha(avatarAlpha)
        val accentColor = this.accentColor.currentValue.getOrElse(Color.TRANSPARENT)
        centerPaint.setColor(accentColor)
        canvas.drawCircle(current.x, current.y, avatarRadius, centerPaint)
        val initials = userInitials.currentValue.getOrElse("")
        initialsPaint.setTextSize(initialsTextSize)
        initialsPaint.setAlpha(avatarAlpha)
        drawTextCentered(canvas, initials, initialsPaint, current)
      } { bitmap =>
        buttonPaint.setColor(Color.WHITE)
        buttonPaint.setAlpha(avatarAlpha)
        avatarRect.left = current.x - avatarRadius
        avatarRect.top = current.y - avatarRadius
        avatarRect.right = current.x + avatarRadius
        avatarRect.bottom = current.y + avatarRadius
        canvas.drawBitmap(bitmap, null, avatarRect, buttonPaint)
      }
    }
  }

  private def drawTarget(target: PointF, canvas: Canvas, touchDownOnAction: Boolean, buttonPressedColor: Int, buttonColor: Int, glyphString: String): Unit = {
    val isActionSelected = distPoint(target, droppedTarget).~=(0f)
    val actionAlpha = (255 * (if (!isActionSelected) otherTargetAlpha else 1f)).toInt
    val actionRadius = scaleFactor * buttonRadius * (if (isActionSelected) targetScale else 1f)
    val glyphTextSize = glyphSize * (if (isActionSelected) targetScale else 1f)
    if (allowClickOnActionButtons && touchDownOnAction) buttonPaint.setColor(buttonPressedColor) else buttonPaint.setColor(buttonColor)
    buttonPaint.setStyle(Paint.Style.FILL)
    buttonPaint.setAlpha(actionAlpha)
    canvas.drawCircle(target.x, target.y, actionRadius, buttonPaint)
    glyphPaint.setAlpha(actionAlpha)
    glyphPaint.setTextSize(glyphTextSize)
    drawTextCentered(canvas, glyphString, glyphPaint, target)
  }

  private def drawTextCentered(canvas: Canvas, text: String, textPaint: Paint, centerPoint: PointF) = {
    textPaint.setTextAlign(Paint.Align.LEFT)
    textPaint.getTextBounds(text, 0, text.length, textRect)
    val x = centerPoint.x - textRect.width / 2f - textRect.left
    val y = centerPoint.y + textRect.height / 2f - textRect.bottom
    canvas.drawText(text, x, y, textPaint)
  }

  private def dropInTarget(target: PointF) = {
    val otherTargetAnimator = ValueAnimator.ofFloat(1f, 0f)
    otherTargetAnimator.setInterpolator(new Quart.EaseOut)
    otherTargetAnimator.setDuration(getResources.getInteger(R.integer.wire__animation__delay__short))
    otherTargetAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      def onAnimationUpdate(animation: ValueAnimator) = {
        otherTargetAlpha = animation.getAnimatedValue.asInstanceOf[Float]
        invalidate
      }
    })
    val targetAnimator = ValueAnimator.ofFloat(1f, 0f)
    targetAnimator.setInterpolator(new Expo.EaseOut)
    targetAnimator.setStartDelay(getResources.getInteger(R.integer.wire__animation__delay__short))
    targetAnimator.setDuration(getResources.getInteger(R.integer.wire__animation__delay__regular))
    targetAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      def onAnimationUpdate(animation: ValueAnimator) = {
        targetScale = animation.getAnimatedValue.asInstanceOf[Float]
        invalidate
      }
    })
    val avatarAnimator = ValueAnimator.ofFloat(1f, 0f)
    avatarAnimator.setInterpolator(new Expo.EaseInOut)
    avatarAnimator.setDuration(getResources.getInteger(R.integer.wire__animation__delay__regular))
    avatarAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      def onAnimationUpdate(animation: ValueAnimator) = {
        avatarAnimationValue = animation.getAnimatedValue.asInstanceOf[Float]
        invalidate
      }
    })
    val animation = new AnimatorSet
    animation.playTogether(otherTargetAnimator, targetAnimator, avatarAnimator)
    animation.addListener(new AnimatorListenerAdapter() {
      override def onAnimationStart(animation: Animator) = {
        droppedTarget = target
      }

      override def onAnimationEnd(animation: Animator) = {
        setVisibility(GONE)
      }

      override def onAnimationCancel(animation: Animator) = {
        setVisibility(GONE)
      }
    })
    animation.start
  }

  private var touchDownOnLeftAction: Boolean = false
  private var touchDownOnRightAction: Boolean = false

  override def onTouchEvent(@NonNull event: MotionEvent): Boolean = {
    val touchX = event.getX
    val touchY = event.getY
    var ret = false
    event.getAction match {
      case MotionEvent.ACTION_DOWN =>
        last.x = touchX
        last.y = touchY
        ySpring.setSpringConfig(COASTING)
        xSpring.setSpringConfig(COASTING)
        velocityTracker = VelocityTracker.obtain
        velocityTracker.addMovement(event)
        if (distPoint(current, last) <= buttonRadius) {
          dragging = true
          inTarget = false
          sizeSpring.setEndValue(1f)
          ret = true
        }
        else if (distPoint(targets.head, last) <= buttonRadius) {
          touchDownOnLeftAction = true
          invalidate
          if (!allowClickOnActionButtons) {
            targetIndicatorSpring.setEndValue(-1f)
          }
          ret = true
        }
        else if (distPoint(targets.last, last) <= buttonRadius) {
          touchDownOnRightAction = true
          invalidate
          if (!allowClickOnActionButtons) {
            targetIndicatorSpring.setEndValue(1f)
          }
          ret = true
        }
      case MotionEvent.ACTION_MOVE if dragging =>
        velocityTracker.addMovement(event)
        var touchOutsideTarget = true
        var closestTarget: PointF = null

        for (target <- targets) {
          if (dist(touchX, touchY, target.x, target.y) < attractionRadius && touchOutsideTarget) {
            touchOutsideTarget = false
            closestTarget = target
          }
        }
        if (touchOutsideTarget) {
          var jumpOutside: Boolean = false
          var isOutside: Boolean = false
          for (target <- targets) {
            if (distPoint(current, target) < 0.5f * buttonRadius * ATTRACTION_FACTOR_THRESHOLD && !jumpOutside) {
              jumpOutside = true
            }
            if (distPoint(current, target) > attractionRadius && !isOutside) {
              isOutside = true
            }
          }
          val offsetX: Float = current.x - touchX
          val offsetY: Float = current.y - touchY
          if (isOutside && !jumpOutside && !(offsetX > buttonRadius || offsetY > buttonRadius)) {
            ySpring.setSpringConfig(COASTING)
            xSpring.setSpringConfig(COASTING)
            xSpring.setCurrentValue(xSpring.getCurrentValue - offsetX)
            ySpring.setCurrentValue(ySpring.getCurrentValue - offsetY)
            inTarget = false
          }
          else {
            xSpring.setSpringConfig(CONVERGING)
            xSpring.setEndValue(touchX)
            ySpring.setSpringConfig(CONVERGING)
            ySpring.setEndValue(touchY)
          }
        }
        else {
          xSpring.setSpringConfig(CONVERGING)
          xSpring.setEndValue(closestTarget.x)
          ySpring.setSpringConfig(CONVERGING)
          ySpring.setEndValue(closestTarget.y)
          if (!inTarget) {
            callController.vibrate()
          }
          inTarget = true
        }
        ret = true
      case MotionEvent.ACTION_UP | MotionEvent.ACTION_CANCEL =>
        if (allowClickOnActionButtons) {
          if (touchDownOnLeftAction && distPoint(targets.head, last) <= buttonRadius) {
            callController.dismissCall()
          }
          else if (touchDownOnRightAction && distPoint(targets.last, last) <= buttonRadius) {
            callPermissionsController.acceptCall()
          }
        }
        touchDownOnLeftAction = false
        touchDownOnRightAction = false
        invalidate
        if (dragging) {
          velocityTracker.addMovement(event)
          velocityTracker.computeCurrentVelocity(1000)
          ySpring.setSpringConfig(CONVERGING)
          xSpring.setSpringConfig(CONVERGING)
          xSpring.setVelocity(velocityTracker.getXVelocity)
          ySpring.setVelocity(velocityTracker.getYVelocity)
          if (!inTarget) {
            sizeSpring.setEndValue(0f)
          }
          inTarget = false
          dragging = false
          checkDroppedToTarget
          ret = true
        }
      case _ => //do nothing
    }
    last.x = touchX
    last.y = touchY
    return ret
  }

  private def checkConstraints = {
    val x = current.x
    val y = current.y
    if (x + avatarRadius >= getWidth) {
      xSpring.setVelocity(-xSpring.getVelocity)
      xSpring.setCurrentValue(xSpring.getCurrentValue - (x + avatarRadius - getWidth), false)
    }
    if (x - avatarRadius <= 0) {
      xSpring.setVelocity(-xSpring.getVelocity)
      xSpring.setCurrentValue(xSpring.getCurrentValue - (x - avatarRadius), false)
    }
    if (y + avatarRadius >= getHeight) {
      ySpring.setVelocity(-ySpring.getVelocity)
      ySpring.setCurrentValue(ySpring.getCurrentValue - (y + avatarRadius - getHeight), false)
    }
    if (y - avatarRadius <= 0) {
      ySpring.setVelocity(-ySpring.getVelocity)
      ySpring.setCurrentValue(ySpring.getCurrentValue - (y - avatarRadius), false)
    }
  }

  private def checkDroppedToTarget = {
    for (target <- targets) {
      if (distPoint(current, target) < attractionRadius) {
        xSpring.setSpringConfig(CONVERGING)
        xSpring.setEndValue(target.x)
        ySpring.setSpringConfig(CONVERGING)
        ySpring.setEndValue(target.y)
        dropInTarget(target)
        if (target == targets.head) {
          callController.dismissCall()
        }
        else {
          callPermissionsController.acceptCall()
        }
      }
      else if (target == targets.last && !dragging) {
        xSpring.setSpringConfig(CONVERGING)
        xSpring.setEndValue(centerPoint.x)
        ySpring.setSpringConfig(CONVERGING)
        ySpring.setEndValue(centerPoint.y)
      }
    }
  }

  private def distPoint(x: PointF, y: PointF): Float = {
    (Option(x), Option(y)) match {
      case (Some(x), Some(y)) => dist(x.x, x.y, y.x, y.y)
      case _ => Float.NaN
    }
  }

  private def dist(posX: Double, posY: Double, pos2X: Double, pos2Y: Double): Float = {
    return Math.sqrt(Math.pow(pos2X - posX, 2) + Math.pow(pos2Y - posY, 2)).toFloat
  }

  private final class TargetIndicatorSpringListener extends SimpleSpringListener {
    override def onSpringUpdate(spring: Spring) = if (!dragging) {
      if (Math.abs(targetIndicatorSpring.getCurrentValue) > 0.95f && (targetIndicatorSpring.getEndValue.~=(1f) || targetIndicatorSpring.getEndValue.~=(-1f))) {
        targetIndicatorSpring.setVelocity(0f)
        targetIndicatorSpring.setEndValue(0f)
      }
      val center: Float = centerPoint.x
      val dist: Float = Math.abs(targets.head.x - centerPoint.x)
      current.x = SpringUtil.mapValueFromRangeToRange(targetIndicatorSpring.getCurrentValue, -1, 1, center - 0.5f * dist, center + 0.5f * dist).toFloat
      invalidate
    }
  }

  private final class AvatarPositionSpringListener extends SimpleSpringListener with SpringSystemListener {
    override def onSpringUpdate(spring: Spring) = {
      current.x = xSpring.getCurrentValue.toFloat
      current.y = ySpring.getCurrentValue.toFloat
      invalidate
    }

    def onAfterIntegrate(springSystem: BaseSpringSystem) = {
      checkConstraints
    }

    def onBeforeIntegrate(springSystem: BaseSpringSystem) = {
    }
  }

  private class TargetSizeSpringListener extends SimpleSpringListener {
    override def onSpringUpdate(spring: Spring) = {
      val maxScale = (expandedButtonRadius.toFloat) / buttonRadius.toFloat
      scaleFactor = SpringUtil.mapValueFromRangeToRange(sizeSpring.getCurrentValue, 0, 1, INIT_SCALE, maxScale).toFloat
      invalidate
    }
  }
}

private object IncomingControlsView {

  implicit val precision = Precision(0.001)

  private val ATTRACTION_FACTOR_THRESHOLD = 2.25f
  private val CONVERGING = SpringConfig.fromOrigamiTensionAndFriction(20, 6)
  private val COASTING = SpringConfig.fromOrigamiTensionAndFriction(0, 1)
  private val INDICATING = new SpringConfig(120, 13)
  private val INIT_SCALE = 1.0f

  case class Precision(val p: Double)

  implicit class DoubleWithAlmostEquals(val d: Double) extends AnyVal {
    def ~=(d2: Double)(implicit p: Precision) = (d - d2).abs < p.p
  }

  implicit class FloatWithAlmostEquals(val f: Float) extends AnyVal {
    def ~=(f2: Float)(implicit p: Precision) = (f - f2).abs < p.p
  }

}
