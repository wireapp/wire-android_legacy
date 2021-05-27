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
package com.waz.zclient.calling

import android.content.{Context, Intent}
import android.os.{Build, Bundle}
import android.view.WindowManager
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.zclient._
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.utils.DeprecationUtils

class CallingActivity extends BaseActivity {
  private lazy val controller    = inject[CallController]
  private lazy val fragment =
    if (BuildConfig.LARGE_VIDEO_CONFERENCE_CALLS) NewCallingFragment()
    else CallingFragment()

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.calling_layout)

    getSupportFragmentManager
      .beginTransaction()
      .replace(R.id.calling_layout, fragment, fragment.getTag)
      .commit

    controller.shouldHideCallingUi.onUi { _ => finish()}
  }

  override def onAttachedToWindow(): Unit = {
    getWindow.addFlags(
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
        DeprecationUtils.FLAG_DISMISS_KEYGUARD)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      getWindow.addFlags(DeprecationUtils.FLAG_TURN_SCREEN_ON | DeprecationUtils.FLAG_SHOW_WHEN_LOCKED)
    }
  }

  override def onBackPressed(): Unit = {
    verbose(l"onBackPressed")

    Option(getSupportFragmentManager.findFragmentById(R.id.calling_layout)).foreach {
      case f: OnBackPressedListener if f.onBackPressed() =>
      case _ => super.onBackPressed()
    }
  }

  override def onStart(): Unit = {
    super.onStart()
    inject[GlobalTrackingController].start(this)
  }

  override def onStop(): Unit = {
    inject[GlobalTrackingController].stop()
    super.onStop()
  }

  override def onResume(): Unit = {
    super.onResume()
    controller.setVideoPause(pause = false)
  }

  override def onPause(): Unit = {
    controller.setVideoPause(pause = true)
    super.onPause()
  }

  override def getBaseTheme: Int = R.style.Theme_Calling
}

object CallingActivity extends Injectable {

  def start(context: Context): Unit = {
    val intent = new Intent(context, classOf[CallingActivity])
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }

  def startIfCallIsActive(context: WireContext): Unit = {
    import context.injector
    inject[CallController].isCallActive.head.foreach {
      case true => start(context)
      case false =>
    } (Threading.Ui)
  }
}
