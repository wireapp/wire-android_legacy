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

import android.content.Context
import android.net.{ConnectivityManager, Network, NetworkCapabilities, NetworkRequest}
import android.provider.Settings
import com.waz.api.NetworkMode
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.permissions.PermissionsService
import com.waz.threading.Threading
import com.waz.utils.returning
import com.wire.signals.Signal

import scala.collection.immutable.ListSet
import scala.concurrent.Future

trait NetworkModeService {
  def networkMode: Signal[NetworkMode]

  def registerNetworkCallback(): Future[Unit]
  def isDeviceIdleMode: Boolean

  lazy val isOnline: Signal[Boolean] = networkMode.map(NetworkModeService.isOnlineMode)
}

class DefaultNetworkModeService(context: Context, lifeCycle: UiLifeCycle, permissionService: PermissionsService)
  extends NetworkModeService with DerivedLogTag {
  import Threading.Implicits.Background

  private lazy val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]

  override val networkMode = returning(Signal[NetworkMode](NetworkMode.UNKNOWN)) { _.disableAutowiring() }

  override def registerNetworkCallback(): Future[Unit] =
    permissionService.requestAllPermissions(ListSet(android.Manifest.permission.ACCESS_NETWORK_STATE)).map {
      case true =>
        connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().build(), new ConnectivityManager.NetworkCallback() {

          override def onAvailable(network: Network): Unit = {
            val mode = computeMode(network)

            info(l"new network mode: $mode")
            networkMode ! mode
          }

          override def onLost(network: Network): Unit =
            if (Settings.Global.getInt(context.getContentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0) {
              info(l"new network mode: OFFLINE (the airplane mode is on)")
              networkMode ! NetworkMode.OFFLINE
            } else {
              val mode = computeMode(network)
              networkMode.mutate {
                case currentMode if currentMode == mode =>
                  info(l"new network mode: OFFLINE (network $mode lost)")
                  NetworkMode.OFFLINE
                case currentMode => currentMode
              }
            }

          override def onUnavailable(): Unit = {
            info(l"new network mode: OFFLINE (no network available)")
            networkMode ! NetworkMode.OFFLINE
          }
        })
      case false =>
    }

  def computeMode(network: Network): NetworkMode =
    Option(connectivityManager.getNetworkCapabilities(network)).map { networkCapabilities =>
      if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) NetworkMode.WIFI
      else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) NetworkMode.CELLULAR
      else NetworkMode.OFFLINE
    }.getOrElse(NetworkMode.OFFLINE)

  override def isDeviceIdleMode: Boolean = false

}

object NetworkModeService extends DerivedLogTag {
  def isOnlineMode(mode: NetworkMode): Boolean =
    mode != NetworkMode.OFFLINE && mode != NetworkMode.UNKNOWN
}
