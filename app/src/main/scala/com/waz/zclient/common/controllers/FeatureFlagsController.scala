package com.waz.zclient.common.controllers

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.teams.FeatureFlagsService
import com.waz.threading.Threading
import com.waz.zclient.security.ActivityLifecycleCallback
import com.waz.zclient.{Injectable, Injector}
import com.wire.signals.Signal

class FeatureFlagsController(implicit inj: Injector) extends Injectable with DerivedLogTag {

  import Threading.Implicits.Background

  private lazy val featureFlags = inject[Signal[FeatureFlagsService]]

  def startUpdatingFlagsWhenEnteringForeground(): Unit =
    inject[ActivityLifecycleCallback].appInBackground.map(_._1).foreach {
      case false => featureFlags.head.foreach(_.updateFileSharing())
      case true  => ()
    }

}
