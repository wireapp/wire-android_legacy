package com.waz.zclient.common.controllers

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.teams.FeatureConfigsService
import com.waz.threading.Threading
import com.waz.zclient.security.ActivityLifecycleCallback
import com.waz.zclient.{Injectable, Injector}
import com.wire.signals.Signal

class FeatureConfigsController(implicit inj: Injector) extends Injectable with DerivedLogTag {

  import Threading.Implicits.Background

  private lazy val featureConfigs = inject[Signal[FeatureConfigsService]]

  def startUpdatingFlagsWhenEnteringForeground(): Unit =
    inject[ActivityLifecycleCallback].appInBackground.map(_._1).foreach {
      case false => featureConfigs.head.foreach(updateFlags)
      case true => ()
    }

  private def updateFlags(configsService: FeatureConfigsService)(): Unit = {
    configsService.updateFileSharing()
    configsService.updateSelfDeletingMessages()
  }
}
