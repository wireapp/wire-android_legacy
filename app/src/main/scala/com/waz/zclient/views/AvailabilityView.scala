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
package com.waz.zclient.views

import android.content.Context
import android.graphics.drawable.{BitmapDrawable, Drawable}
import android.os.Bundle
import android.support.design.widget.BottomSheetDialog
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.{ImageView, LinearLayout, TextView}
import com.waz.model.Availability
import com.waz.zclient.messages.UsersController
import com.waz.zclient.ui.text.{GlyphTextView, TextTransform, TypefaceTextView}
import com.waz.zclient.{DialogHelper, R, ViewHelper}
import com.waz.ZLog.ImplicitTag._
import android.graphics.{Bitmap, Canvas, Color}
import com.waz.zclient.paintcode.WireStyleKit
import com.waz.zclient.tracking.AvailabilityChanged
import com.waz.zclient.utils.ContextUtils

abstract class AvailabilityView(context: Context, attrs: AttributeSet, style: Int, allowUpdate: Boolean) extends LinearLayout(context, attrs, style) with ViewHelper {
  import com.waz.zclient.views.AvailabilityView._

  inflate(R.layout.availability_view)

  setOrientation(LinearLayout.HORIZONTAL)
  private val padding = ContextUtils.getDimenPx(R.dimen.wire__padding__8)
  setPadding(padding, padding, padding, padding)

  private val textView = findById[TypefaceTextView](R.id.ttv__availability__text)
  private val setStatusView = findById[GlyphTextView](R.id.gtv__setstatus__icon)

  if (allowUpdate) {
    this.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = showAvailabilityMenu(AvailabilityChanged.Settings)
    })
    setStatusView.setVisibility(View.VISIBLE)
  } else {
    this.setOnClickListener(null)
    setStatusView.setVisibility(View.GONE)
  }

  private lazy val transformer = TextTransform.get(context.getResources.getString(R.string.availability_view__font_transform))

  def set(availability: Availability): Unit = {
    AvailabilityView.displayLeftOfText(textView, availability, textView.getCurrentTextColor)

    availability match {
      case Availability.None if !allowUpdate =>
        textView.setText("")
      case Availability.None =>
        textView.setText(getResources.getString(R.string.availability_setstatus))
      case _ =>
        val name = getResources.getString(viewData(availability).nameId)
        val transformedName = transformer.transform(name).toString
        textView.setText(transformedName)
    }
  }
}

class UpdateAvailabilityView(context: Context, attrs: AttributeSet, style: Int) extends AvailabilityView(context, attrs, style, true) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)
}

class ShowAvailabilityView(context: Context, attrs: AttributeSet, style: Int) extends AvailabilityView(context, attrs, style, false) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)
}

class AvailabilityMenu(override val context: Context, method: AvailabilityChanged.Method) extends BottomSheetDialog(context, R.style.message__bottom_sheet__base) with DialogHelper {
  implicit val ctx: Context = context
  import com.waz.zclient.views.AvailabilityView._

  private lazy val usersController = inject[UsersController]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    val view = getLayoutInflater.inflate(R.layout.availability_menu, null).asInstanceOf[LinearLayout]
    setContentView(view)

    val noneButton = findViewById[View](R.id.availability__choose_none)
    val availableImage = findViewById[ImageView](R.id.iv__image_available)
    val availableButton = findViewById[View](R.id.availability__choose_available)
    val busyImage = findViewById[ImageView](R.id.iv__image_busy)
    val busyButton = findViewById[View](R.id.availability__choose_busy)
    val awayImage = findViewById[ImageView](R.id.iv__image_away)
    val awayButton = findViewById[View](R.id.availability__choose_away)

    def changeAvailabilityAndDismiss(newAvailability: Availability) = {
      usersController.updateAvailability(newAvailability)
      usersController.trackAvailability(newAvailability, method)
      dismiss()
    }

    noneButton.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = changeAvailabilityAndDismiss(Availability.None)
    })

    drawable(Availability.Available, black, 16).foreach(availableImage.setImageDrawable)
    availableButton.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = changeAvailabilityAndDismiss(Availability.Available)
    })

    drawable(Availability.Busy, black, 16).foreach(busyImage.setImageDrawable)
    busyButton.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = changeAvailabilityAndDismiss(Availability.Busy)
    })

    drawable(Availability.Away, black, 16).foreach(awayImage.setImageDrawable)
    awayButton.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = changeAvailabilityAndDismiss(Availability.Away)
    })
  }
}


object AvailabilityView {
  case class ViewData(nameId: Int, textId: Int, drawIcon: (Canvas, Int) => Unit)

  val viewData: Map[Availability, ViewData] = Map(
    Availability.None      -> ViewData(R.string.availability_none,      0,                                    (_, _) => ()),
    Availability.Available -> ViewData(R.string.availability_available, R.string.availability_text_available, WireStyleKit.drawAvailableIcon),
    Availability.Busy      -> ViewData(R.string.availability_busy,      R.string.availability_text_busy,      WireStyleKit.drawBusyIcon),
    Availability.Away      -> ViewData(R.string.availability_away,      R.string.availability_text_away,      WireStyleKit.drawAwayIcon)
  )

  private val PUSH_DOWN_PX = 5

  def displayLeftOfText(view: TextView, av: Availability, color: Int, pushDown: Boolean = false)(implicit ctx: Context): Unit = {
    val name = ContextUtils.getString(AvailabilityView.viewData(av).nameId)
    val pd = if (pushDown) PUSH_DOWN_PX else 0
    val drawable = AvailabilityView.drawable(av, color)
    drawable.foreach(d => d.setBounds(0, pd, d.getIntrinsicWidth, d.getIntrinsicHeight + pd))
    view.setContentDescription(name)
    val oldDrawables = view.getCompoundDrawables
    view.setCompoundDrawablesRelative(drawable.orNull, oldDrawables(1), oldDrawables(2), oldDrawables(3))
  }

  def hideAvailabilityIcon(view: TextView): Unit = {
    val oldDrawables = view.getCompoundDrawables
    view.setCompoundDrawablesRelative(null, oldDrawables(1), oldDrawables(2), oldDrawables(3))
  }

  def showAvailabilityMenu(method: AvailabilityChanged.Method)(implicit ctx: Context): Unit = new AvailabilityMenu(ctx, method).show()

  val white = Color.argb(255, 255, 255, 255)
  val black = Color.argb(255, 0, 0, 0)

  def drawable(availability: Availability, fillColor: Int = white, dpSize: Int = 10)(implicit ctx: Context): Option[Drawable] = availability match {
    case Availability.None => None
    case _ =>
      val drawable = iconCache.getOrElseUpdate(s"${availability.id}.$dpSize.$fillColor", {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        viewData(availability).drawIcon(new Canvas(bmp), fillColor)
        val px = ContextUtils.toPx(dpSize)
        new BitmapDrawable(ctx.getResources, Bitmap.createScaledBitmap(bmp, px, px, true))
      })
      Option(drawable)
  }

  private val iconCache = scala.collection.mutable.Map[String, BitmapDrawable]()
}
