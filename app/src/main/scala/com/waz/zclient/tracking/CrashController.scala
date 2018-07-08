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
package com.waz.zclient.tracking

import java.io.{File, FilenameFilter}
import java.lang.ref.WeakReference

import android.app.Activity
import android.content.Context
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.log.InternalLog
import com.waz.service.ZMessaging
import com.waz.service.tracking.CrashEvent
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.zclient.{Injectable, Injector, WireContext}
import net.hockeyapp.android._
import net.hockeyapp.android.utils.Util

import scala.util.control.NonFatal

class CrashController (implicit inj: Injector, cxt: WireContext, eventContext: EventContext) extends Injectable with Thread.UncaughtExceptionHandler {

  val defaultHandler = Option(Thread.getDefaultUncaughtExceptionHandler) //reference to previously set handler
  Thread.setDefaultUncaughtExceptionHandler(this) //override with this

  override def uncaughtException(t: Thread, e: Throwable) = {
    try {
      ZMessaging.globalModule.map(_.trackingService.crash(e))(Threading.Ui)
    }
    catch {
      case NonFatal(_) =>
    }
    defaultHandler.foreach(_.uncaughtException(t, e))
    InternalLog.flush()
  }
}

object CrashController {


  def checkForCrashes(context: Context, deviceId: String) = {
    verbose("checkForCrashes - registering...")
    val listener = new CrashManagerListener() {

      override def shouldAutoUploadCrashes: Boolean = true
      override def getUserID: String = deviceId
    }

    CrashManager.initialize(context, Util.getAppIdentifier(context), listener)
    val nativeCrashFound = NativeCrashManager.loggedDumpFiles(Util.getAppIdentifier(context))
    if (nativeCrashFound) ZMessaging.globalModule.map(_.trackingService.track(CrashEvent("NDK", s"${Constants.PHONE_MANUFACTURER}/${Constants.PHONE_MODEL}")))(Threading.Ui)

    // execute crash manager in background, it does IO and can take some time
    // XXX: this works because we use auto upload (and app context), so hockey doesn't try to show a dialog
    Threading.IO {
      // check number of crash reports, will drop them if there is too many
      val traces: Array[String] = new File(Constants.FILES_PATH).list(new FilenameFilter() {
        def accept(dir: File, filename: String): Boolean = filename.endsWith(".stacktrace")
      })
      if (traces != null && traces.length > 256) {
        verbose(s"checkForCrashes - found too many crash reports: ${traces.length}, will drop them")
        CrashManager.deleteStackTraces(new WeakReference[Context](context))
      }
      CrashManager.execute(context, listener)
    }
  }

  def deleteCrashReports(context: Context) = {
    Threading.IO {
      try CrashManager.deleteStackTraces(new WeakReference[Context](context))
      catch {
        case NonFatal(_) =>
      }
    }
  }

  def checkForUpdates(activity: Activity) = UpdateManager.register(activity)

}
