package com.waz.zclient.tracking

import java.io.{File, FilenameFilter}
import java.lang.ref.WeakReference

import android.app.Activity
import android.content.Context
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.content.GlobalPreferences
import com.waz.content.Preferences.PrefKey
import com.waz.log.InternalLog
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.zclient.{Injectable, Injector, WireContext}
import net.hockeyapp.android._
import net.hockeyapp.android.utils.Util
import timber.log.Timber

import scala.util.control.NonFatal

class CrashController (implicit inj: Injector, cxt: WireContext, eventContext: EventContext) extends Injectable with Thread.UncaughtExceptionHandler {

  private lazy val crashPref        = inject[GlobalPreferences].preference(PrefKey[String]("USER_PREF_APP_CRASH"))
  private lazy val crashPrefDetails = inject[GlobalPreferences].preference(PrefKey[String]("USER_PREF_APP_CRASH_DETAILS"))

  val tracking = inject[GlobalTrackingController]

  val defaultHandler = Option(Thread.getDefaultUncaughtExceptionHandler) //reference to previously set handler
  Thread.setDefaultUncaughtExceptionHandler(this) //override with this

  override def uncaughtException(t: Thread, e: Throwable) = {
    try {
      def getRootCause: Throwable = if (e.getCause != null) getRootCause else e

      val cause = getRootCause
      val stack = cause.getStackTrace
      val details = if (stack != null && stack.nonEmpty) stack(0).toString else null

      crashPref := cause.getClass.getSimpleName
      crashPrefDetails := details
    }
    catch {
      case NonFatal(_) =>
    }
    defaultHandler.foreach(_.uncaughtException(t, e))
    InternalLog.flush()
  }
}

object CrashController {


  def checkForCrashes(context: Context, deviceId: String, tracking: GlobalTrackingController) = {
    verbose("checkForCrashes - registering...")
    val listener = new CrashManagerListener() {

      override def shouldAutoUploadCrashes: Boolean = true
      override def getUserID: String = deviceId
    }

    CrashManager.initialize(context, Util.getAppIdentifier(context), listener)
    val nativeCrashFound = NativeCrashManager.loggedDumpFiles(Util.getAppIdentifier(context))
    if (nativeCrashFound) tracking.trackEvent(CrashEvent("NDK", s"${Constants.PHONE_MANUFACTURER}/${Constants.PHONE_MODEL}"))

    // execute crash manager in background, it does IO and can take some time
    // XXX: this works because we use auto upload (and app context), so hockey doesn't try to show a dialog
    Threading.IO {
      // check number of crash reports, will drop them if there is too many
      val traces: Array[String] = new File(Constants.FILES_PATH).list(new FilenameFilter() {
        def accept(dir: File, filename: String): Boolean = filename.endsWith(".stacktrace")
      })
      if (traces != null && traces.length > 256) {
        Timber.v("checkForCrashes - found too many crash reports: %d, will drop them", traces.length)
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
