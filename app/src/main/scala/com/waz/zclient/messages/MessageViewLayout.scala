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
package com.waz.zclient.messages

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View.MeasureSpec
import android.view.ViewGroup.{LayoutParams, MarginLayoutParams}
import android.view.{Gravity, View, ViewGroup}
import android.widget.FrameLayout
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model.MessageContent
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.MessageViewLayout.PartDesc
import com.waz.zclient.messages.parts.ReplyPartView

abstract class MessageViewLayout(context: Context, attrs: AttributeSet, style: Int) extends ViewGroup(context, attrs, style) {
  protected val factory: MessageViewFactory

  protected var listParts = Seq.empty[MessageViewPart]
  protected var frameParts = Seq.empty[MessageViewPart]
  private var separatorHeight = 0

  private val defaultLayoutParams = generateDefaultLayoutParams()

  setClipChildren(false)

  protected def setParts(msg: MessageAndLikes, parts: Seq[PartDesc], opts: MsgBindOptions, adapter: MessagesListAdapter)(implicit ec: EventContext): Unit = {
    verbose(s"setParts: opts: $opts, parts: ${parts.map(_.tpe)}")

    // recycle views in reverse order, recycled views are stored in a Stack, this way we will get the same views back if parts are the same
    // XXX: once views get bigger, we may need to optimise this, we don't need to remove views that will get reused, currently this seems to be fast enough
    (0 until getChildCount).reverseIterator.map(getChildAt) foreach {
      case pv: MessageViewPart => factory.recycle(pv)
      case _ =>
    }

    val views = parts.zipWithIndex map { case (PartDesc(tpe, content), index) =>
      val view = factory.get(tpe, this)
      view.setVisibility(View.VISIBLE)
      view.set(msg, content, opts)
      view match {
        case v: ReplyPartView if msg.quote.nonEmpty =>
          v.setQuote(msg.quote.get)
          v.onClicked.onUi { _ =>
            adapter.positionForMessage(msg.quote.get).foreach { pos =>
              if (pos >= 0) adapter.scrollController.scrollToPositionRequested ! pos
            } (Threading.Ui)
          }
        case _ =>
      }
      if (view.getParent == null) addViewInLayout(view, index, Option(view.getLayoutParams).getOrElse(defaultLayoutParams))
      view
    }

    (0 until getChildCount).map(getChildAt).foreach { v =>
      if (!views.contains(v)) removeView(v)
    }

    val (fps, lps) = views.partition {
      case _: FrameLayoutPart => true
      case _ => false
    }
    frameParts = fps
    listParts = lps

    // TODO: request layout only if parts were actually changed
    requestLayout()
  }

  def removeListPart(view: MessageViewPart) = {
    listParts = listParts.filter(_ != view)
    removeView(view)
    factory.recycle(view)
  }

  override def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int): Unit = {
    val w = View.getDefaultSize(getSuggestedMinimumWidth, widthMeasureSpec)
    var h = getPaddingTop + getPaddingBottom
    separatorHeight = 0
    listParts foreach { v =>
      if (v.getVisibility != View.GONE) {
        measureChildWithMargins(v, widthMeasureSpec, 0, heightMeasureSpec, 0)
        val m = getMargin(v.getLayoutParams)
        h += v.getMeasuredHeight + m.top + m.bottom

        if (v.tpe.isInstanceOf[SeparatorPart])
          separatorHeight += v.getMeasuredHeight + m.top + m.bottom
      }
    }

    val hSpec = MeasureSpec.makeMeasureSpec(h - separatorHeight, MeasureSpec.AT_MOST)
    frameParts foreach { v =>
      if (v.getVisibility != View.GONE) {
        measureChildWithMargins(v, widthMeasureSpec, 0, hSpec, 0)
        h = math.max(h, v.getMeasuredHeight + separatorHeight + getPaddingTop + getPaddingBottom)
      }
    }

    setMeasuredDimension(w, h)
  }

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    val w = r - l
    val h = b - t
    var top = getPaddingTop
    listParts foreach { v =>
      if (v.getVisibility != View.GONE) {
        val vh = v.getMeasuredHeight
        val m = getMargin(v.getLayoutParams)

        top += m.top
        // TODO: handle RTL
        v.layout(m.left, top, m.left + v.getMeasuredWidth, top + vh)
        top += vh + m.bottom
      }
    }

    frameParts foreach { v =>
      if (v.getVisibility != View.GONE) {
        val vw = v.getMeasuredWidth
        val vh = v.getMeasuredHeight
        val gravity = v.getLayoutParams match {
          case lp: FrameLayout.LayoutParams => lp.gravity
          case _ => Gravity.TOP | Gravity.START
        }
        val m = getMargin(v.getLayoutParams)
        val absoluteGravity = Gravity.getAbsoluteGravity(gravity, getLayoutDirection)

        val left = absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK match {
          case Gravity.CENTER_HORIZONTAL =>
            (w - vw) / 2 + m.left - m.right
          case Gravity.RIGHT =>
            w - vw - m.right
          case _ =>
            m.left
        }

        val top = gravity & Gravity.VERTICAL_GRAVITY_MASK match {
          case Gravity.CENTER_VERTICAL =>
            separatorHeight / 2 + (h - vh) / 2 + m.top - m.bottom
          case Gravity.BOTTOM =>
            h - vh - m.bottom - getPaddingBottom
          case _ =>
            m.top + separatorHeight + getPaddingTop
        }
        v.layout(left, top, left + vw, top + vh)
      }
    }
  }

  private val EmptyMargin = new Rect(0, 0, 0, 0)
  private def getMargin(lp: LayoutParams) = lp match {
    case mp: MarginLayoutParams => new Rect(mp.leftMargin, mp.topMargin, mp.rightMargin, mp.bottomMargin)
    case _ => EmptyMargin
  }

  override def shouldDelayChildPressedState(): Boolean = false

  override def generateLayoutParams(attrs: AttributeSet): LayoutParams =
    new FrameLayout.LayoutParams(getContext, attrs)

  override def generateLayoutParams(p: LayoutParams): LayoutParams =
    new FrameLayout.LayoutParams(p)

  override def generateDefaultLayoutParams(): LayoutParams =
    new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
}

object MessageViewLayout {
  case class PartDesc(tpe: MsgPart, content: Option[MessageContent] = None)
}
