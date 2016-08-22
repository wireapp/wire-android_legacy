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
package com.waz.zclient.camera

import java.util.concurrent.{CountDownLatch, TimeUnit}

import android.content.Context
import android.graphics
import android.graphics.{Rect, SurfaceTexture}
import android.view.OrientationEventListener
import com.waz.testutils.TestUtils.{PrintValues, RichLatch}
import com.waz.testutils.TestWireContext
import com.waz.utils.events.EventContext
import com.waz.zclient.Module
import com.waz.zclient.camera.controllers._
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.scalatest.RobolectricSuite
import org.scalatest.junit.JUnitSuite

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

@RunWith(classOf[RobolectricTestRunner])
@Config(manifest = Config.NONE)
class GlobalCameraControllerTest extends JUnitSuite with RobolectricSuite {

  implicit val defaultDuration = Duration(30, TimeUnit.SECONDS)

  implicit val printSignalVals: PrintValues = false
  implicit val context = mock(classOf[TestWireContext])
  implicit val eventContext = EventContext.Global
  implicit val executionContext = ExecutionContext.Implicits.global

  implicit lazy val module = new Module {
    bind[Context] to context
  }

  val cameraFactory = mock(classOf[TestCameraFactory])

  val defaultInfo = CameraInfo(0, CameraFacing.BACK, 270)
  val defaultPreviewSize = PreviewSize(10, 10)

  @Test
  def nextCamera(): Unit = {
    val infos = Seq(
      CameraInfo(0, CameraFacing.BACK, 270),
      CameraInfo(1, CameraFacing.BACK, 270)
    )
    val controller = createController(infos)

    assertEquals(infos.size, controller.camInfos.size)
    assertEquals(Some(infos.head), controller.currentCamInfo)
    controller.setNextCamera()
    assertEquals(Some(infos(1)), controller.currentCamInfo)
  }

  @Test
  def currentCameraFacing(): Unit =
    assertEquals(Some(CameraFacing.FRONT), createController(Seq(CameraInfo(0, CameraFacing.FRONT, -1))).getCurrentCameraFacing)


  @Test
  def openCamera(): Unit = {
    val mockCamera = createMockCamera()
    val ctrl = createController()
    assertEquals((defaultPreviewSize, Set.empty[FlashMode]), Await.result(ctrl.openCamera(null, 0, 0), defaultDuration))
  }

  @Test
  def releaseCamera(): Unit = {
    val mockCamera = createMockCamera()
    val ctrl = createController()
    Await.result(ctrl.openCamera(null, 0, 0), defaultDuration)

    val latch = new CountDownLatch(1)

    //Note: Hard to test the callback version because the response uses Threading.UI as the execution context, which doesn't play nicely here.
    ctrl.releaseCamera().andThen {
      case _ => latch.countDown()
    }

    latch.waitDuration
    assertEquals(None, ctrl.currentCamera)
    assertEquals(0, latch.getCount)
  }

  @Test
  def openNextCamera(): Unit = {
    val infos = Seq(
      CameraInfo(0, CameraFacing.BACK, 270),
      CameraInfo(1, CameraFacing.FRONT, 270)
    )
    val backCamera = createMockCamera(infos.head)
    val frontCamera = createMockCamera(infos(1))

    val ctrl = createController(infos)

    Await.ready(ctrl.openCamera(null, 0, 0), defaultDuration)
    assertEquals(Some(backCamera), ctrl.currentCamera)

    ctrl.setNextCamera()
    assertEquals(Some(backCamera), ctrl.currentCamera)

    Await.ready(ctrl.releaseCamera(), defaultDuration)
    Await.ready(ctrl.openCamera(null, 0, 0), defaultDuration)

    assertEquals(Some(CameraFacing.FRONT), ctrl.getCurrentCameraFacing)
    assertEquals(Some(frontCamera), ctrl.currentCamera)
  }

  @Test
  def setFocus(): Unit = {
    val cam = createMockCamera()
    val ctrl = createController()

    val promise = Promise[Unit]()
    when(cam.setFocusArea(any(classOf[Rect]), any(classOf[Int]), any(classOf[Int]))).thenReturn(promise.future)

    val latch = new CountDownLatch(1)
    ctrl.setFocusArea(new graphics.Rect(0, 0, 10, 10), 10, 10).onComplete {
      case _ =>
        latch.countDown()
    }

    promise.success(())

    latch.waitDuration
    assertEquals(0, latch.getCount)
  }

  @Test
  def settingFocusAfterCameraCloseFails(): Unit = {
    val cam = createMockCamera()
    val ctrl = createController()

    Await.ready(ctrl.openCamera(null, 0, 0), defaultDuration)

    val latch = new CountDownLatch(1)

    ctrl.releaseCamera()
    ctrl.setFocusArea(new Rect(0, 0, 10, 10), 10, 10).onComplete {
      case Success(_) => fail("Set focus should not have succeeded")
      case Failure(e) => latch.countDown()
    }

    latch.waitDuration
    assertEquals(0, latch.getCount)

  }

  @Test
  def testOrientationCalculation(): Unit = {
    assertEquals(Portrait_0, Orientation(0))
    assertEquals(Portrait_0, Orientation(OrientationEventListener.ORIENTATION_UNKNOWN))
    assertEquals(Portrait_0, Orientation(2000))
    assertEquals(Landscape_90, Orientation(90))
    assertEquals(Portrait_180, Orientation(180))
    assertEquals(Landscape_270, Orientation(270))
  }

  def createController(camInfos: Seq[CameraInfo] = Seq(defaultInfo)) = {
    when(cameraFactory.getCameraInfos).thenReturn(camInfos)
    new GlobalCameraController(module.inject[Context], cameraFactory)
  }

  def createMockCamera(info: CameraInfo = defaultInfo, previewSize: PreviewSize = defaultPreviewSize, flashModes: Set[FlashMode] = Set.empty) = {
    val mockCamera = mock(classOf[TestCamera])
    when(cameraFactory.apply(Matchers.eq(info), any(classOf[SurfaceTexture]), any(classOf[Int]), any(classOf[Int]), any(classOf[Context]), any(classOf[Orientation]), any(classOf[FlashMode]))).thenReturn(mockCamera)
    when(mockCamera.getPreviewSize).thenReturn(previewSize)
    when(mockCamera.getSupportedFlashModes).thenReturn(flashModes)
    mockCamera
  }

  class TestCameraFactory extends CameraFactory {
    override def getCameraInfos: Seq[CameraInfo] = ???

    override def apply(info: CameraInfo, texture: SurfaceTexture, w: Int, h: Int, cxt: Context, devOrientation: Orientation, flashMode: FlashMode): WireCamera = ???
  }

  class TestCamera extends WireCamera {
    override def getPreviewSize: PreviewSize = ???

    override def getSupportedFlashModes: Set[FlashMode] = ???

    override def setOrientation(o: Orientation): Unit = ???

    override def takePicture(shutter: => Unit): Future[Array[Byte]] = ???

    override def release(): Unit = ???

    override def setFlashMode(fm: FlashMode): Unit = ???

    override def setFocusArea(touchRect: Rect, w: Int, h: Int): Future[Unit] = ???
  }

}
