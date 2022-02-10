package com.waz.zclient.common.controllers

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.teams.FeatureConfigsService
import com.waz.threading.Threading
import com.waz.zclient.security.ActivityLifecycleCallback
import com.waz.zclient.{BuildConfig, Injectable, Injector}
import com.wire.signals.Signal

final class FeatureConfigsController(implicit inj: Injector) extends Injectable with DerivedLogTag {

  import Threading.Implicits.Background

  private lazy val featureConfigs = inject[Signal[FeatureConfigsService]]

  def startUpdatingFlagsWhenEnteringForeground(): Unit =
    inject[ActivityLifecycleCallback].appInBackground.map(_._1).foreach { isInBackground =>
      if(!isInBackground){
        featureConfigs.head.foreach(updateFlags)
      }
    }

  private def updateFlags(configsService: FeatureConfigsService): Unit = {
    configsService.updateFileSharing()
    configsService.updateSelfDeletingMessages()
    if(BuildConfig.CONFERENCE_CALLING_RESTRICTION)
      configsService.updateConferenceCalling()
    if (BuildConfig.FEDERATION_USER_DISCOVERY)
      configsService.updateClassifiedDomains()
    configsService.updateGuestLinks()
  }
}
