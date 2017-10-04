package com.waz.zclient.tracking

import com.waz.content.GlobalPreferences
import com.waz.content.Preferences.PrefKey
import com.waz.log.InternalLog
import com.waz.utils.events.EventContext
import com.waz.zclient.{Injectable, Injector, WireContext}

class CrashController (implicit inj: Injector, cxt: WireContext, eventContext: EventContext) extends Injectable with Thread.UncaughtExceptionHandler {

  private lazy val crashPref        = inject[GlobalPreferences].preference(PrefKey[String]("USER_PREF_APP_CRASH"))
  private lazy val crashPrefDetails = inject[GlobalPreferences].preference(PrefKey[String]("USER_PREF_APP_CRASH_DETAILS"))

  val tracking = inject[GlobalTrackingController]

  override def uncaughtException(t: Thread, e: Throwable) = {
    try {

      def getRootCause: Throwable = if (e.getCause != null) getRootCause else e

      val cause = getRootCause
      val stack = cause.getStackTrace
      val details = if (stack != null && stack.nonEmpty) stack(0).toString else null

      crashPref := cause.getClass.getSimpleName
      crashPrefDetails := details

      tracking.trackEvent()

      controllerFactory.getUserPreferencesController.setCrashException(cause.getClass.getSimpleName, details)

    }
    catch {
      case ignored: Throwable => {
      }
    }
    if (defaultUncaughtExceptionHandler != null) defaultUncaughtExceptionHandler.uncaughtException(thread, throwable)
    InternalLog.flush()
  }


}
