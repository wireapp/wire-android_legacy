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
package com.waz.zclient.conversation

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.Toolbar
import android.view.View.{OnClickListener, OnLayoutChangeListener}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, ImageView}
import com.waz.ZLog.ImplicitTag._
import com.waz.api.ImageAsset
import com.waz.model.{Liking, MessageId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.conversation.toolbar._
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.ui.animation.interpolators.penner.{Expo, Quart}
import com.waz.zclient.ui.cursor.CursorMenuItem
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{Offset, ViewUtils}
import com.waz.zclient.{FragmentHelper, R}
import org.threeten.bp.{LocalDateTime, ZoneId}

object ImageFragment {
  val Tag = ImageFragment.getClass.getSimpleName
  val ArgMessageId = "MESSAGE_ID_ARG"

  def newInstance(messageId: String): Fragment =
    returning(new ImageFragment)(_.setArguments(returning(new Bundle())(_.putString(ArgMessageId, messageId))))
}

class ImageFragment extends FragmentHelper {
  import ImageFragment._
  import Threading.Implicits.Ui

  implicit def context: Context = getContext

  lazy val zms                      = inject[Signal[ZMessaging]]
  lazy val collectionController     = inject[CollectionController]
  lazy val convController           = inject[ConversationController]
  lazy val messageActionsController = inject[MessageActionsController]
  lazy val drawingController        = inject[IDrawingController]
  lazy val singleImageController    = inject[ISingleImageController]

  lazy val likedBySelf = collectionController.focusedItem flatMap {
    case Some(m) => zms.flatMap { z =>
      z.reactionsStorage.signal((m.id, z.selfUserId)).map(_.action == Liking.like).orElse(Signal const false)
    }
    case None => Signal.const(false)
  }

  lazy val message = collectionController.focusedItem.collect { case Some(msg) => msg }.disableAutowiring()

  lazy val topCursorItems: Signal[Seq[ToolbarItem]] = {
    message.flatMap { m =>
      if (m.isEphemeral) zms.map(_.selfUserId == m.userId).map { fromSelf =>
        (if (fromSelf)
            Seq(MessageActionToolbarItem(MessageAction.Save))
        else
            Seq.empty) :+ MessageActionToolbarItem(MessageAction.Delete)

      } else likedBySelf.map { isLiked =>
        Seq(
          MessageActionToolbarItem(if (isLiked) MessageAction.Unlike else MessageAction.Like),
          MessageActionToolbarItem(MessageAction.Forward),
          CursorActionToolbarItem(CursorMenuItem.SKETCH),
          CursorActionToolbarItem(CursorMenuItem.EMOJI),
          CursorActionToolbarItem(CursorMenuItem.KEYBOARD),
          MoreToolbarItem
        )
      }
    }
  }

  lazy val bottomCursorItems: Signal[Seq[ToolbarItem]] = {
    message.map(_.isEphemeral).map {
      case true => Seq.empty
      case _    => Seq(
        MessageActionToolbarItem(MessageAction.Save),
        MessageActionToolbarItem(MessageAction.Reveal),
        MessageActionToolbarItem(MessageAction.Delete),
        DummyToolbarItem,
        DummyToolbarItem,
        MoreToolbarItem)
    }
  }

  lazy val imageAsset = collectionController.focusedItem.flatMap {
    case Some(messageData) => Signal[ImageAsset](ZMessaging.currentUi.images.getImageAsset(messageData.assetId))
    case _ => Signal.empty[ImageAsset]
  } disableAutowiring()

  var animationStarted = false

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = inflater.inflate(R.layout.fragment_image, container, false)
    val bottomToolbar   = findById[CustomToolbarFrame](view, R.id.bottom_toolbar)
    val headerTitle     = findById[TypefaceTextView](view, R.id.header_toolbar__title)
    val headerTimestamp = findById[TypefaceTextView](view, R.id.header_toolbar__timestamp)
    val headerToolbar   = findById[Toolbar](view, R.id.header_toolbar)

    headerToolbar.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = getFragmentManager.popBackStack()
    })

    topCursorItems.onUi(bottomToolbar.topToolbar.cursorItems ! _)
    bottomCursorItems.onUi(bottomToolbar.bottomToolbar.cursorItems ! _)

    imageAsset

    EventStream.union(bottomToolbar.topToolbar.onCursorButtonClicked, bottomToolbar.bottomToolbar.onCursorButtonClicked) {
      case item: CursorActionToolbarItem =>
        import IDrawingController.DrawingDestination._
        import IDrawingController.DrawingMethod._

        val method = item.cursorItem match {
          case CursorMenuItem.SKETCH   => Some(DRAW)
          case CursorMenuItem.EMOJI    => Some(EMOJI)
          case CursorMenuItem.KEYBOARD => Some(TEXT)
          case _ => None
        }

        method.foreach { m =>
          getFragmentManager.popBackStack()
          imageAsset.head.foreach { asset =>
            drawingController.showDrawing(asset, SINGLE_IMAGE_VIEW, m)
          }
        }
      case item: MessageActionToolbarItem =>
        if (item.action == MessageAction.Reveal) getFragmentManager.popBackStack()
        message.head foreach { msg => messageActionsController.onMessageAction ! (item.action, msg) }
      case _ =>
    }

    messageActionsController.onDeleteConfirmed.onUi { case (msg, _) =>
      if (collectionController.focusedItem.currentValue.flatten.contains(msg)) {
        getFragmentManager.popBackStack()
        singleImageController.hideSingleImage()
      }
    }

    convController.currentConv.map(_.displayName).onUi(headerTitle.setText)

    collectionController.focusedItem
      .collect { case Some(msg) => LocalDateTime.ofInstant(msg.time, ZoneId.systemDefault()).toLocalDate.toString }
      .onUi(headerTimestamp.setText)

    val layoutChangeListener = new OnLayoutChangeListener {
      override def onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) = {
        if(v.getWidth > 0 && !animationStarted){
          animationStarted = true
          Option(getArguments.getString(ArgMessageId)).foreach { messageId =>
            zms.head.flatMap(_.messagesStorage.get(MessageId(messageId))).map { _.foreach(msg => collectionController.focusedItem ! Some(msg)) }
            val imageSignal: Signal[ImageSource] = zms.flatMap(_.messagesStorage.signal(MessageId(messageId))).map(msg => WireImage(msg.assetId))
            animateOpeningTransition(new ImageAssetDrawable(imageSignal))
          }
        }
      }
    }

    view.addOnLayoutChangeListener(layoutChangeListener)
    view
  }

  override def onDestroyView() = {
    collectionController.focusedItem ! None
    super.onDestroyView()
  }

  override def onBackPressed() = false

  override def onDetach() = {
    singleImageController.hideSingleImage()
    super.onDetach()
  }

  private def animateOpeningTransition(drawable: ImageAssetDrawable): Unit =  {
    val img = singleImageController.getImageContainer

    val imageViewPager = findById[ImageViewPager](R.id.image_view_pager)
    val background     = findById[View]          (R.id.background)

    if (img == null || img.getBackground == null || !img.getBackground.isInstanceOf[ImageAssetDrawable]) {
      imageViewPager.setVisibility(View.VISIBLE)
      background.setAlpha(1f)
    } else {
      val imagePadding       = img.getBackground.asInstanceOf[ImageAssetDrawable].padding.currentValue.getOrElse(Offset.Empty)
      val clickedImageHeight = img.getHeight - imagePadding.t - imagePadding.b
      val clickedImageWidth  = img.getWidth - imagePadding.l - imagePadding.r

      if (clickedImageHeight == 0 || clickedImageWidth == 0) {
        imageViewPager.setVisibility(View.VISIBLE)
        background.setAlpha(1f)
      } else {
        val clickedImageLocation = ViewUtils.getLocationOnScreen(img)
        clickedImageLocation.offset(imagePadding.l, imagePadding.t - findById[View](R.id.header_toolbar).getHeight - getStatusBarHeight(getActivity))

        val fullContainerWidth: Int = background.getWidth
        val fullContainerHeight: Int = background.getHeight
        val scale: Float = Math.min(fullContainerWidth / clickedImageWidth.toFloat, fullContainerHeight / clickedImageHeight.toFloat)
        val fullImageWidth = clickedImageWidth.toFloat * scale
        val fullImageHeight = clickedImageHeight.toFloat * scale

        val targetX = ((fullContainerWidth - fullImageWidth) / 2).toInt + (fullImageWidth - clickedImageWidth) / 2
        val targetY = ((fullContainerHeight - fullImageHeight) / 2).toInt + (fullImageHeight - clickedImageHeight) / 2

        returning(findById[ImageView](R.id.animating_image)) { animView =>
          animView.setImageDrawable(drawable)
          val parent = animView.getParent.asInstanceOf[ViewGroup]
          parent.removeView(animView)
          animView.setLayoutParams(new FrameLayout.LayoutParams(clickedImageWidth, clickedImageHeight))
          animView.setX(clickedImageLocation.x)
          animView.setY(clickedImageLocation.y)
          animView.setScaleX(1f)
          animView.setScaleY(1f)
          parent.addView(animView)

          animView.animate
            .y(targetY)
            .x(targetX)
            .scaleX(scale)
            .scaleY(scale)
            .setInterpolator(new Expo.EaseOut)
            .setDuration(getInt(R.integer.single_image_message__open_animation__duration))
            .withStartAction(new Runnable() {
              def run() = singleImageController.getImageContainer.setVisibility(View.INVISIBLE)
            })
            .withEndAction(new Runnable() {
              def run() = {
                val messageView = singleImageController.getImageContainer
                Option(messageView).foreach(_.setVisibility(View.VISIBLE))
                animView.setVisibility(View.GONE)
                imageViewPager.setVisibility(View.VISIBLE)
              }
            })
            .start()
        }

        background.animate
          .alpha(1f)
          .setDuration(getInt(R.integer.framework_animation_duration_short))
          .setInterpolator(new Quart.EaseOut)
          .start()
      }
    }
  }
}
