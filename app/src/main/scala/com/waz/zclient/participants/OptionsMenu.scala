/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.participants

import android.content.Context
import android.graphics.{Canvas, Rect, RectF}
import android.os.Bundle
import android.view.{View, ViewGroup}
import android.widget.{LinearLayout, TextView}
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.paintcode.WireStyleKit.ResizingBehavior
import com.waz.zclient.paintcode.{WireDrawable, WireStyleKit}
import com.waz.zclient.ui.animation.interpolators.penner.{Expo, Quart}
import com.waz.zclient.utils.ViewUtils.getView
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{DialogHelper, R}
import com.waz.zclient.utils.ContextUtils._

case class OptionsMenu(context: Context, controller: OptionsMenuController) extends BottomSheetDialog(context, R.style.message__bottom_sheet__base) with DialogHelper {
  private implicit val ctx: Context = context

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val view = getLayoutInflater.inflate(R.layout.message__bottom__menu, null).asInstanceOf[LinearLayout]
    setContentView(view)

    val container = view.findViewById[LinearLayout](R.id.container)
    val title = view.findViewById[TextView](R.id.title)

    def params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.toPx(getContext, 48))

    controller.title.onUi {
      case Some(text) =>
        title.setVisible(true)
        title.setText(text)
      case _ =>
        title.setVisible(false)
    }

    Signal(controller.optionItems, controller.selectedItems).onUi { case (items, selected) =>

      container.removeAllViews()

      items.foreach { item =>
        container.addView(returning(getLayoutInflater.inflate(R.layout.message__bottom__menu__row, container, false)) { itemView =>

          returning(getView[View](itemView, R.id.icon)) { v =>
            item.iconId.fold(v.setVisibility(View.GONE)) { g =>
              v.setVisibility(View.VISIBLE)
              val drawable = new WireDrawable {
                override def draw(canvas: Canvas): Unit ={
                  OptionsMenu.drawForId(g)(canvas, getDrawingRect, ResizingBehavior.AspectFit, this.paint.getColor)
                }
              }
              drawable.setPadding(new Rect(v.getPaddingLeft, v.getPaddingTop, v.getPaddingRight, v.getPaddingBottom))
              val color = item.colorId.map(getColor).getOrElse(getColor(R.color.graphite))
              drawable.setColor(color)
              v.setBackground(drawable)
            }
          }
          returning(getView[TextView](itemView, R.id.text)) { v =>
            v.setText(item.title)
            item.colorId.map(getColor).foreach(v.setTextColor(_))
          }
          itemView.onClick {
            controller.onMenuItemClicked ! item
            dismiss()
          }
          item.iconId.foreach(itemView.setId)

          returning(getView[View](itemView, R.id.tick)) { v =>
            v.setVisible(selected.contains(item))
          }
        }, params)
      }
    }
  }
}

object OptionsMenu {

  lazy val quartOut = new Quart.EaseOut
  lazy val expoOut  = new Expo.EaseOut
  lazy val expoIn   = new Expo.EaseIn

  trait AnimState
  case object Open    extends AnimState
  case object Opening extends AnimState
  case object Closing extends AnimState
  case object Closed  extends AnimState

  def drawForId(id: Int): (Canvas, RectF, ResizingBehavior, Int) => Unit = id match {
    case R.id.message_bottom_menu_item_forward => WireStyleKit.drawShare
    case R.id.message_bottom_menu_item_copy => WireStyleKit.drawCopied
    case R.id.message_bottom_menu_item_delete => WireStyleKit.drawDelete
    case R.id.message_bottom_menu_item_delete_local => WireStyleKit.drawDeleteforme
    case R.id.message_bottom_menu_item_delete_global => WireStyleKit.drawDeleteforeveryone
    case R.id.message_bottom_menu_item_edit => WireStyleKit.drawEdit
    case R.id.message_bottom_menu_item_like => WireStyleKit.drawLike
    case R.id.message_bottom_menu_item_unlike => WireStyleKit.drawLiked
    case R.id.message_bottom_menu_item_save => WireStyleKit.drawSave
    case R.id.message_bottom_menu_item_open_file => WireStyleKit.drawFile
    case R.id.message_bottom_menu_item_reveal => WireStyleKit.drawReveal
    case R.id.message_bottom_menu_item_reply => WireStyleKit.drawReply
    case R.id.message_bottom_menu_item_details => WireStyleKit.drawView

    case R.string.glyph__silence => WireStyleKit.drawMuteAlerts
    case R.string.glyph__notify => WireStyleKit.drawAlerts
    case R.string.glyph__camera => WireStyleKit.drawCamera
    case R.string.glyph__call => WireStyleKit.drawAcceptCall
    case R.string.glyph__archive => WireStyleKit.drawArchive
    case R.string.glyph__delete_me => WireStyleKit.drawDeleteforme
    case R.string.glyph__leave => WireStyleKit.drawLeave
    case R.string.glyph__block => WireStyleKit.drawBlock
    case R.string.glyph__minus => WireStyleKit.drawRemove
    case R.string.glyph__add_to_favorites => WireStyleKit.drawFavoritesOff
    case R.string.glyph__remove_from_favorites => WireStyleKit.drawFavoritesOn
    case R.string.glyph__move_to_folder => WireStyleKit.drawMoveTo
    case R.string.glyph__remove_from_folder => WireStyleKit.drawRemoveFrom
    case R.string.glyph__clear => WireStyleKit.drawEraser

    case _ => (_, _, _, _) => ()
  }
}
