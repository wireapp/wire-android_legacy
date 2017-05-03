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
package com.waz.zclient.fragments

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
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.conversation.CollectionController
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.conversation.views.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.ui.animation.interpolators.penner.{Expo, Quart}
import com.waz.zclient.ui.cursor.CursorMenuItem
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.{Offset, ViewUtils}
import com.waz.zclient.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.views.toolbar._
import com.waz.zclient.views.{ImageAssetDrawable, ImageViewPager}
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R}
import org.threeten.bp.{LocalDateTime, ZoneId}

object ImageFragment {
  val TAG = ImageFragment.getClass.getSimpleName

  val MESSAGE_ID_ARG = "MESSAGE_ID_ARG"

  def newInstance(messageId: String): Fragment = {
    val fragment = new ImageFragment
    val bundle = new Bundle()
    bundle.putString(MESSAGE_ID_ARG, messageId)
    fragment.setArguments(bundle)
    fragment
  }

  trait ImageContainer extends View
  trait Container
}

class ImageFragment extends BaseFragment[ImageFragment.Container] with FragmentHelper with OnBackPressedListener {
  import ImageFragment._
  import Threading.Implicits.Ui

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val collectionController = inject[CollectionController]
  lazy val messageActionsController = inject[MessageActionsController]
  lazy val likedBySelf = collectionController.focusedItem flatMap {
    case Some(m) => zms.flatMap { z =>
      z.reactionsStorage.signal((m.id, z.selfUserId)).map(_.action == Liking.like).orElse(Signal const false)
    }
    case None => Signal.const(false)
  }
  lazy val message = collectionController.focusedItem.map(_.map(_.id)) collect {
    case Some(id) => ZMessaging.currentUi.messages.cachedOrNew(id)
  } disableAutowiring()

  lazy val imageAsset = collectionController.focusedItem.flatMap {
    case Some(messageData) => Signal[ImageAsset](ZMessaging.currentUi.images.getImageAsset(messageData.assetId))
    case _ => Signal.empty[ImageAsset]
  } disableAutowiring()

  lazy val currentConversation = for {
    z <- zms
    Some(convId) <- z.convsStats.selectedConversationId
    conv <- z.convsStorage.signal(convId)
  } yield conv

  var animationStarted = false

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = inflater.inflate(R.layout.fragment_image, container, false)
    val bottomToolbar = ViewUtils.getView[CustomToolbarFrame](view, R.id.bottom_toolbar)
    val headerTitle = ViewUtils.getView[TypefaceTextView](view, R.id.header_toolbar__title)
    val headerTimestamp = ViewUtils.getView[TypefaceTextView](view, R.id.header_toolbar__timestamp)
    val headerToolbar = ViewUtils.getView[Toolbar](view, R.id.header_toolbar)

    headerToolbar.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        getFragmentManager.popBackStack()
      }
    })

    likedBySelf.on(Threading.Ui) {
      case true =>
        bottomToolbar.topToolbar.cursorItems ! Seq(
          MessageActionToolbarItem(MessageAction.UNLIKE),
          MessageActionToolbarItem(MessageAction.FORWARD),
          CursorActionToolbarItem(CursorMenuItem.SKETCH),
          CursorActionToolbarItem(CursorMenuItem.EMOJI),
          CursorActionToolbarItem(CursorMenuItem.KEYBOARD),
          MoreToolbarItem)
      case false =>
        bottomToolbar.topToolbar.cursorItems ! Seq(
          MessageActionToolbarItem(MessageAction.LIKE),
          MessageActionToolbarItem(MessageAction.FORWARD),
          CursorActionToolbarItem(CursorMenuItem.SKETCH),
          CursorActionToolbarItem(CursorMenuItem.EMOJI),
          CursorActionToolbarItem(CursorMenuItem.KEYBOARD),
          MoreToolbarItem)
    }

    imageAsset

    EventStream.union(bottomToolbar.topToolbar.onCursorButtonClicked, bottomToolbar.bottomToolbar.onCursorButtonClicked) {
      case item: CursorActionToolbarItem =>
        item.cursorItem match {
          case CursorMenuItem.SKETCH =>
            getFragmentManager.popBackStack()
            imageAsset.head.foreach { asset =>
              getControllerFactory.getDrawingController.showDrawing(asset, IDrawingController.DrawingDestination.SINGLE_IMAGE_VIEW, IDrawingController.DrawingMethod.DRAW)
            }
          case CursorMenuItem.EMOJI =>
            getFragmentManager.popBackStack()
            imageAsset.head.foreach { asset =>
              getControllerFactory.getDrawingController.showDrawing(asset, IDrawingController.DrawingDestination.SINGLE_IMAGE_VIEW, IDrawingController.DrawingMethod.EMOJI)
            }
          case CursorMenuItem.KEYBOARD =>
            getFragmentManager.popBackStack()
            imageAsset.head.foreach { asset =>
              getControllerFactory.getDrawingController.showDrawing(asset, IDrawingController.DrawingDestination.SINGLE_IMAGE_VIEW, IDrawingController.DrawingMethod.TEXT)
            }
          case _ =>
        }
      case item: MessageActionToolbarItem =>
        if (item.action == MessageAction.REVEAL) {
          getFragmentManager.popBackStack()
        }
        message.currentValue.foreach( msg => messageActionsController.onMessageAction ! (item.action, msg))
      case _ =>
    }

    currentConversation.on(Threading.Ui) { conv =>
      headerTitle.setText(conv.displayName)
    }

    collectionController.focusedItem.on(Threading.Ui) {
      case Some(messageData) =>
        headerTimestamp.setText(LocalDateTime.ofInstant(messageData.time, ZoneId.systemDefault()).toLocalDate.toString)
      case _ =>
    }

    bottomToolbar.bottomToolbar.cursorItems ! Seq(
      MessageActionToolbarItem(MessageAction.SAVE),
      MessageActionToolbarItem(MessageAction.REVEAL),
      MessageActionToolbarItem(MessageAction.DELETE),
      DummyToolbarItem,
      DummyToolbarItem,
      MoreToolbarItem)

    val layoutChangeListener: OnLayoutChangeListener = new OnLayoutChangeListener {
      override def onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) = {
        if(v.getWidth > 0 && !animationStarted){
          animationStarted = true
          Option(getArguments.getString(MESSAGE_ID_ARG)).foreach { messageId =>
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

  override def onBackPressed() = {
    false
  }

  private def animateOpeningTransition(drawable: ImageAssetDrawable): Unit =  {
    val animatingImageView = ViewUtils.getView(getView, R.id.animating_image).asInstanceOf[ImageView]
    val imageViewPager = ViewUtils.getView[ImageViewPager](getView, R.id.image_view_pager)
    val clickedImage = getControllerFactory.getSingleImageController.getImageContainer
    val background = ViewUtils.getView[View](getView, R.id.background)
    val topToolbar = ViewUtils.getView[View](getView, R.id.header_toolbar)
    val openAnimationDuration = getResources.getInteger(R.integer.single_image_message__open_animation__duration)
    val openAnimationBackgroundDuration = getResources.getInteger(R.integer.framework_animation_duration_short)

    if (!clickedImage.getBackground.isInstanceOf[ImageAssetDrawable]) {
      imageViewPager.setVisibility(View.VISIBLE)
      background.setAlpha(1f)
      return
    }
    val imagePadding = clickedImage.getBackground.asInstanceOf[ImageAssetDrawable].padding.currentValue.getOrElse(Offset.Empty)
    val clickedImageHeight = clickedImage.getHeight - imagePadding.t - imagePadding.b
    val clickedImageWidth = clickedImage.getWidth - imagePadding.l - imagePadding.r

    if (clickedImageHeight == 0 || clickedImageWidth == 0) {
      imageViewPager.setVisibility(View.VISIBLE)
      background.setAlpha(1f)
      return
    }

    val clickedImageLocation = ViewUtils.getLocationOnScreen(clickedImage)
    clickedImageLocation.offset(imagePadding.l, imagePadding.t - topToolbar.getHeight - ViewUtils.getStatusBarHeight(getActivity))

    val fullContainerWidth: Int = background.getWidth
    val fullContainerHeight: Int = background.getHeight
    val scale: Float = Math.min(fullContainerWidth / clickedImageWidth.toFloat, fullContainerHeight / clickedImageHeight.toFloat)
    val fullImageWidth = clickedImageWidth.toFloat * scale
    val fullImageHeight = clickedImageHeight.toFloat * scale

    val targetX = ((fullContainerWidth - fullImageWidth) / 2).toInt + (fullImageWidth - clickedImageWidth) / 2
    val targetY = ((fullContainerHeight - fullImageHeight) / 2).toInt + (fullImageHeight - clickedImageHeight) / 2

    animatingImageView.setImageDrawable(drawable)

    val parent: ViewGroup = animatingImageView.getParent.asInstanceOf[ViewGroup]
    parent.removeView(animatingImageView)
    val layoutParams: ViewGroup.LayoutParams = new FrameLayout.LayoutParams(clickedImageWidth, clickedImageHeight)
    animatingImageView.setLayoutParams(layoutParams)
    animatingImageView.setX(clickedImageLocation.x)
    animatingImageView.setY(clickedImageLocation.y)
    animatingImageView.setScaleX(1f)
    animatingImageView.setScaleY(1f)
    parent.addView(animatingImageView)

    animatingImageView.animate.y(targetY).x(targetX).scaleX(scale).scaleY(scale).setInterpolator(new Expo.EaseOut).setDuration(openAnimationDuration).withStartAction(new Runnable() {
      def run(): Unit =  {
        getControllerFactory.getSingleImageController.getImageContainer.setVisibility(View.INVISIBLE)
      }
    }).withEndAction(new Runnable() {
      def run(): Unit =  {
        val messageView = getControllerFactory.getSingleImageController.getImageContainer
        Option(messageView).foreach(_.setVisibility(View.VISIBLE))
        animatingImageView.setVisibility(View.GONE)
        imageViewPager.setVisibility(View.VISIBLE)
      }
    }).start()
    background.animate.alpha(1f).setDuration(openAnimationBackgroundDuration).setInterpolator(new Quart.EaseOut).start()
  }

  override protected def onPreDetach() = {
    getControllerFactory.getSingleImageController.hideSingleImage()
  }
}
