/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import android.annotation.TargetApi
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.net.{ConnectivityManager, NetworkInfo}
import android.telephony.TelephonyManager
import com.waz.api.NetworkMode
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.wire.signals.{EventContext, Signal}
import com.waz.utils.returning

trait NetworkModeService {
  def networkMode: Signal[NetworkMode]
  def getNetworkOperatorName: String
  def isDeviceIdleMode: Boolean

  lazy val isOnline: Signal[Boolean] = networkMode.map(NetworkModeService.isOnlineMode)
}

class DefaultNetworkModeService(context: Context, lifeCycle: UiLifeCycle) extends NetworkModeService with DerivedLogTag {
  import NetworkModeService._

  private lazy val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
  private lazy val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]

  override val networkMode = returning(Signal[NetworkMode](NetworkMode.UNKNOWN)) { _.disableAutowiring() }

  lifeCycle.uiActive {
    case true => updateNetworkMode()
    case _    => //
  }

  val receiver = new BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent): Unit = updateNetworkMode()
  }
  context.registerReceiver(receiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
  updateNetworkMode()

  def updateNetworkMode(): Unit = {
    val mode = Option(connectivityManager.getActiveNetworkInfo) match {
      case Some(info) if info.isConnected => computeMode(info, telephonyManager)
      case _                              => NetworkMode.OFFLINE
    }
    verbose(l"updateNetworkMode: $mode")

    networkMode ! mode
  }

  override def getNetworkOperatorName = Option(telephonyManager.getNetworkOperatorName).filter(_.nonEmpty).getOrElse("unknown")

  //this method doesn't really belong here, but it's only used for tracking - just tells us if the device was in doze mode or not
  //fixme get the version code from android sdk
  @TargetApi(23)
  override def isDeviceIdleMode = false
  //    if (android.os.Build.VERSION.SDK_INT >= M)
  //      Option(context.getSystemService(Context.POWER_SERVICE)).map(_.asInstanceOf[PowerManager]).exists(_.isDeviceIdleMode)
  //    else false
}

object NetworkModeService extends DerivedLogTag {

  def isOnlineMode(mode: NetworkMode): Boolean =
    mode != NetworkMode.OFFLINE && mode != NetworkMode.UNKNOWN

  /*
   * This part (the mapping of mobile data network types to the networkMode enum) of the Wire software
   * uses source coded posted on the StackOverflow site.
   * (http://stackoverflow.com/a/18583089/1751834)
   *
   * That work is licensed under a Creative Commons Attribution-ShareAlike 2.5 Generic License.
   * (http://creativecommons.org/licenses/by-sa/2.5)
   *
   * Contributors on StackOverflow:
   *  - Anonsage (http://stackoverflow.com/users/887894/anonsage)
   */
  def computeMode(ni: NetworkInfo, tm: => TelephonyManager): NetworkMode = Option(ni.getType).map {
    case ConnectivityManager.TYPE_WIFI | ConnectivityManager.TYPE_ETHERNET => NetworkMode.WIFI
    case _ =>
      tm.getNetworkType match {
        case TelephonyManager.NETWORK_TYPE_GPRS |
             TelephonyManager.NETWORK_TYPE_1xRTT |
             TelephonyManager.NETWORK_TYPE_IDEN |
             TelephonyManager.NETWORK_TYPE_CDMA =>
          NetworkMode._2G
        case TelephonyManager.NETWORK_TYPE_EDGE =>
          NetworkMode.EDGE
        case TelephonyManager.NETWORK_TYPE_EVDO_0 |
             TelephonyManager.NETWORK_TYPE_EVDO_A |
             TelephonyManager.NETWORK_TYPE_HSDPA |
             TelephonyManager.NETWORK_TYPE_HSPA |
             TelephonyManager.NETWORK_TYPE_HSUPA |
             TelephonyManager.NETWORK_TYPE_UMTS |
             TelephonyManager.NETWORK_TYPE_EHRPD |
             TelephonyManager.NETWORK_TYPE_EVDO_B |
             TelephonyManager.NETWORK_TYPE_HSPAP =>
          NetworkMode._3G
        case TelephonyManager.NETWORK_TYPE_LTE =>
          NetworkMode._4G
        case _ =>
          info(l"Unknown network type, defaulting to Wifi")
          NetworkMode.WIFI
      }
  }.getOrElse(NetworkMode.OFFLINE)
}
