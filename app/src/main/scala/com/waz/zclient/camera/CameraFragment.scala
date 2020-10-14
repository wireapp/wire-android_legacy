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
package com.waz.zclient.camera

import java.util

import android.animation.{Animator, AnimatorListenerAdapter, ObjectAnimator}
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, TextView}
import com.waz.service.assets.Content
import com.waz.utils.returning
import com.waz.utils.wrappers.URI
import com.waz.zclient.camera.views.CameraPreviewTextureView
import com.waz.zclient.common.controllers.ScreenController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.drawing.DrawingFragment.Sketch
import com.waz.zclient.pages.main.conversation.AssetIntentsManager
import com.waz.zclient.pages.main.profile.camera.controls.{CameraBottomControl, CameraTopControl}
import com.waz.zclient.pages.main.profile.camera.{CameraContext, CameraFocusView, ProfileToCameraAnimation}
import com.waz.zclient.pages.main.{ImagePreviewCallback, ImagePreviewLayout}
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, SquareOrientation}
import com.waz.zclient.views.ProgressView
import com.waz.zclient.{FragmentHelper, R}
import com.waz.threading.Threading._

import scala.concurrent.duration._

class CameraFragment extends FragmentHelper
  with CameraPreviewObserver
  with ImagePreviewCallback
  with CameraTopControl.CameraTopControlCallback
  with CameraBottomControl.CameraBottomControlCallback {

  private lazy val cameraContext = CameraContext.getFromOrdinal(getArguments.getInt(CameraFragment.CAMERA_CONTEXT))

  private lazy val accentColorController = inject[AccentColorController]
  private lazy val cameraController      = inject[ICameraController]
  private lazy val screenController      = inject[ScreenController]

  //TODO allow selection of a camera 'facing' for different cameraContexts
  private lazy val cameraPreview = returning(view[CameraPreviewTextureView](R.id.cptv__camera_preview)) {
    _.foreach(_.setObserver(this))
  }

  private lazy val cameraNotAvailableTextView = view[TextView](R.id.ttv__camera_not_available_message)

  private lazy val cameraTopControl = returning(view[CameraTopControl](R.id.ctp_top_controls)) { _.foreach { view =>
    view.setCameraTopControlCallback(this)
    view.setAlpha(0)
    view.setVisible(true)
  }}

  private lazy val cameraBottomControl = returning(view[CameraBottomControl](R.id.cbc__bottom_controls)) { _.foreach { view =>
    view.setCameraBottomControlCallback(this)
    view.setMode(cameraContext)
    view.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = {} // do nothing but consume event
    })
  }}

  private lazy val focusView = returning(view[CameraFocusView](R.id.cfv__focus)) { vh =>
    accentColorController.accentColor.onUi { color =>
      vh.foreach(_.setColor(color.color))
    }
  }

  private lazy val imagePreviewContainer = view[FrameLayout](R.id.fl__preview_container)
  private lazy val previewProgressBar = returning(view[ProgressView](R.id.pv__preview)) { vh =>
    accentColorController.accentColor.onUi { color =>
      vh.foreach(_.setTextColor(color.color))
    }
  }

  private var intentsManager: AssetIntentsManager = _
  private lazy val cameraPreviewAnimationDuration = FiniteDuration(getInt(R.integer.camera__preview__ainmation__duration), MILLISECONDS)
  private lazy val cameraControlAnimationDuration = FiniteDuration(getInt(R.integer.camera__control__ainmation__duration), MILLISECONDS)

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = { // opening from profile
    if (nextAnim == R.anim.camera__from__profile__transition)
      new ProfileToCameraAnimation(
        enter,
        getInt(R.integer.framework_animation_duration_medium),
        0,
        getDimenPx(R.dimen.camera__control__height),
        0)
    else
      super.onCreateAnimation(transit, enter, nextAnim)
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    intentsManager = new AssetIntentsManager(getActivity, new AssetIntentsManager.Callback() {
      override def onDataReceived(t: AssetIntentsManager.IntentType, uri: URI): Unit = processGalleryImage(uri)
      override def onCanceled(t: AssetIntentsManager.IntentType): Unit = showCameraFeed()
      override def onFailed(t: AssetIntentsManager.IntentType): Unit = showCameraFeed()
      override def openIntent(intent: Intent, intentType: AssetIntentsManager.IntentType): Unit =
        startActivityForResult(intent, intentType.requestCode)
    })
  }

  override def onCreateView(inflater: LayoutInflater, c: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_camera, c, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    cameraTopControl.foreach(_.setConfigOrientation(SquareOrientation.PORTRAIT_STRAIGHT))
    cameraBottomControl.foreach(_.setConfigOrientation(SquareOrientation.PORTRAIT_STRAIGHT))

    imagePreviewContainer
    previewProgressBar

    view.setBackgroundResource(R.color.black)
  }

  override def onDestroyView(): Unit = {
    hideCameraFeed()
    cameraBottomControl.foreach(
      _.animate
        .translationY(getView.getMeasuredHeight)
        .setDuration(cameraControlAnimationDuration.toMillis)
        .setInterpolator(new Expo.EaseIn)
    )
    super.onDestroyView()
  }

  override def onBackPressed(): Boolean = {
    onClose()
    true
  }

  private def disableCameraButtons(): Unit = if (Option(getView).isDefined) {
    findById[View](R.id.gtv__camera_control__take_a_picture).setVisibility(View.GONE)
    findById[View](R.id.gtv__camera__top_control__change_camera).setVisibility(View.GONE)
    findById[View](R.id.gtv__camera__top_control__flash_setting).setVisibility(View.GONE)
  }

  override def onCameraLoaded(flashModes: util.Set[FlashMode]): Unit = {
    cameraPreview.foreach { preview =>
      cameraTopControl.foreach { control =>
        control.setFlashStates(flashModes, preview.getCurrentFlashMode)
        control.enableCameraSwitchButtion(preview.getNumberOfCameras > 1)
      }
    }

    showCameraFeed()

    cameraNotAvailableTextView.foreach(_.setVisible(false))
  }

  override def onCameraLoadingFailed(): Unit = {
    cameraController.onCameraNotAvailable(cameraContext)
    disableCameraButtons()
    cameraNotAvailableTextView.foreach(_.setVisible(true))
  }

  override def onCameraReleased(): Unit = {
    //no need to override since we don't exit the app
  }

  override def onPictureTaken(imageData: Array[Byte]): Unit =
   showPreview { _.setImage(imageData) }

  override def onFocusBegin(focusArea: Rect): Unit = focusView.foreach { view =>
    view.setX(focusArea.centerX - view.getWidth / 2)
    view.setY(focusArea.centerY - view.getHeight / 2)
    view.showFocusView()
  }

  override def onFocusComplete(): Unit = focusView.foreach {
    _.hideFocusView()
  }

  def openGallery(): Unit = {
    intentsManager.openGallery()
    getActivity.overridePendingTransition(R.anim.camera_in, R.anim.camera_out)
  }

  override def nextCamera(): Unit = cameraPreview.foreach(_.nextCamera())

  override def setFlashMode(mode: FlashMode): Unit = cameraPreview.foreach(_.setFlashMode(mode))

  override def getFlashMode: FlashMode = cameraPreview.map(_.getCurrentFlashMode).getOrElse(FlashMode.OFF)

  override def onClose(): Unit = {
    cameraPreview.foreach(_.setFlashMode(FlashMode.OFF)) //set back to default off when leaving camera
    cameraController.closeCamera(cameraContext)
  }

  override def onTakePhoto(): Unit = {
    if (cameraContext != CameraContext.SIGN_UP) previewProgressBar.foreach(_.setVisible(true))
    cameraTopControl.foreach(_.fadeOut(cameraControlAnimationDuration))
    cameraPreview.foreach(_.takePicture())
  }

  override def onOpenImageGallery(): Unit = openGallery()

  override def onCancelPreview(): Unit = {
    previewProgressBar.foreach(_.setVisible(false))

    imagePreviewContainer.foreach { c =>
      val animator: ObjectAnimator = ObjectAnimator.ofFloat(c, View.ALPHA, 1, 0)
      animator.setDuration(cameraControlAnimationDuration.toMillis)
      animator.addListener(new AnimatorListenerAdapter() {
        override def onAnimationCancel(animation: Animator): Unit = hideImagePreviewOnAnimationEnd()
        override def onAnimationEnd(animation: Animator): Unit = hideImagePreviewOnAnimationEnd()
      })
      animator.start()
    }

    showCameraFeed()
  }

  override def onSketchOnPreviewPicture(image: Content, method: IDrawingController.DrawingMethod): Unit =
    screenController.showSketch ! Sketch.cameraPreview(image)

  override def onSendPictureFromPreview(image: Content): Unit =
    cameraController.onBitmapSelected(image, cameraContext)

  private def showPreview(setImage: (ImagePreviewLayout) => Unit) = {
    hideCameraFeed()

    previewProgressBar.foreach(_.setVisible(false))

    imagePreviewContainer.foreach { c =>
      c.removeAllViews()
      c.addView(returning(ImagePreviewLayout.newInstance(getContext, imagePreviewContainer.get, this)) { layout =>
        setImage(layout)
        layout.showSketch(cameraContext == CameraContext.MESSAGE)
        layout.showTitle(cameraContext == CameraContext.MESSAGE)
      })
      c.setVisible(true)
      ObjectAnimator.ofFloat(c, View.ALPHA, 0, 1).setDuration(cameraPreviewAnimationDuration.toMillis).start()
    }

    cameraBottomControl.foreach(_.setVisible(false))
  }

  private def hideImagePreviewOnAnimationEnd(): Unit = {
    imagePreviewContainer.foreach(_.setVisible(false))
    cameraBottomControl.foreach(_.setVisible(true))
  }

  private def showCameraFeed(): Unit = {
    cameraTopControl.foreach(_.fadeIn(cameraControlAnimationDuration))
    cameraPreview.foreach(_.setVisible(true))
    cameraBottomControl.foreach(_.enableShutterButton())
  }

  private def hideCameraFeed(): Unit = {
    cameraTopControl.foreach(_.fadeOut(cameraControlAnimationDuration))
    cameraPreview.foreach(_.setVisibility(View.GONE))
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    intentsManager.onActivityResult(requestCode, resultCode, data)
  }

  private def processGalleryImage(uri: URI): Unit = {
    hideCameraFeed()
    if (cameraContext != CameraContext.SIGN_UP) previewProgressBar.foreach(_.setVisible(true))
    showPreview { _.setImage(uri) }
  }
}


object CameraFragment {
  val Tag: String = classOf[CameraFragment].getName
  private val CAMERA_CONTEXT: String = "CAMERA_CONTEXT"

  def newInstance(cameraContext: CameraContext): CameraFragment = returning(new CameraFragment) {
    _.setArguments(returning(new Bundle) { bundle =>
      bundle.putInt(CAMERA_CONTEXT, cameraContext.ordinal)
    })
  }
}
