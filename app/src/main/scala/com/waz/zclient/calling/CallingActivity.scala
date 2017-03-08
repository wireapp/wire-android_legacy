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
package com.waz.zclient.calling

import android.content.{Context, Intent}
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.TextView
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.threading.Threading
import com.waz.zclient._
import com.waz.zclient.calling.controllers.GlobalCallingController
import com.waz.zclient.calling.views.VideoCallingView
import com.waz.zclient.common.controllers.PermissionActivity
import com.waz.zclient.utils.RichView

class CallingActivity extends AppCompatActivity with ActivityHelper with PermissionActivity {

  private lazy val controller = inject[GlobalCallingController]
  import controller._

  lazy val degradedWarningTextView      = findById[TextView](R.id.degraded_warning)
  lazy val degradedConfirmationTextView = findById[TextView](R.id.degraded_confirmation)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    verbose("Creating CallingActivity")
    getWindow.setBackgroundDrawableResource(R.color.calling_background)

    activeCall.on(Threading.Ui) {
      case false =>
        verbose("call no longer exists, finishing activity")
        finish()
      case _ =>
    }

    //ensure activity gets killed to allow content to change if the conv degrades (no need to kill activity on audio call)
    (for {
      degraded <- convDegraded
      video    <- videoCall
    } yield degraded && video).onChanged.filter(_ == true).on(Threading.Ui)(_ => finish())

    //can only set content view once - so do so on first value of `showVideoView`
    showVideoView.head.map {
      case true =>
        verbose("Setting video view")
        setContentView(new VideoCallingView(this), new LayoutParams(MATCH_PARENT, MATCH_PARENT))
      case _ =>
        verbose("Setting audio view")
        setContentView(R.layout.calling_audio)

        convDegraded.on(Threading.Ui){ degraded =>
          degradedWarningTextView.setVisible(degraded)
          degradedConfirmationTextView.setVisible(degraded)
        }
        degradationWarningText.on(Threading.Ui)(degradedWarningTextView.setText)
        degradationConfirmationText.on(Threading.Ui)(degradedConfirmationTextView.setText)
    }(Threading.Ui)
  }

  //don't allow user to go back during call - no way to re-enter call
  override def onBackPressed(): Unit = ()

  override def onAttachedToWindow(): Unit = {
    getWindow.addFlags(
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
    )
  }
}

object CallingActivity extends Injectable {

  def start(context: Context): Unit = {
    val intent = new Intent(context, classOf[CallingActivity])
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }

  def startIfCallIsActive(context: WireContext) = {
    import context.injector
    inject[GlobalCallingController].activeCall.head.foreach {
      case true => start(context)
      case false =>
    } (Threading.Ui)
  }
}
