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
import com.waz.api.NetworkMode
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.threading.Threading
import com.waz.utils.returning
import com.wire.signals.{CancellableFuture, Signal}

import scala.concurrent.duration._

trait NetworkModeService {
  def networkMode: Signal[NetworkMode]

  def registerNetworkCallback(): Unit
  lazy val isOnline: Signal[Boolean] = networkMode.map(NetworkModeService.isOnlineMode)
}

class DefaultNetworkModeService(context: Context) extends NetworkModeService with DerivedLogTag {
  private val currentNetwork = Signal[Option[Network]]()
  private val currentCapabilities = Signal[Option[NetworkCapabilities]]()

  currentNetwork.map {
    case Some(network) => Option(connectivityManager.getNetworkCapabilities(network))
    case None => None
  }.pipeTo(currentCapabilities)

  override val networkMode: Signal[NetworkMode] = currentCapabilities.map {
    case Some(capabilities) =>
      returning(computeMode(capabilities)) { mode => info(l"new network mode: $mode") }
    case None =>
      info(l"new network mode: OFFLINE")
      NetworkMode.OFFLINE
  }.disableAutowiring()

  private def computeMode(capabilities: NetworkCapabilities): NetworkMode =
    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) NetworkMode.WIFI
    else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) NetworkMode.CELLULAR
    else NetworkMode.OFFLINE

  private lazy val connectivityManager =
    returning(context.getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]) { connectivityManager =>
      connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().build(), new ConnectivityManager.NetworkCallback() {

        override def onAvailable(network: Network): Unit =
          currentNetwork ! Some(network)

        override def onLost(network: Network): Unit =
          currentNetwork.mutate {
            case Some(n) if n == network => None
            case other => other
          }

        override def onUnavailable(): Unit =
          currentNetwork ! None

        override def onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities): Unit =
          currentCapabilities ! Some(networkCapabilities)
      })
    }

  override def registerNetworkCallback(): Unit = {
    connectivityManager

    // If the device was offline before the app was opened, `currentNetwork` will stay empty, not None,
    // because no update will come from `ConnectivityManager`. To fix this, we wait a short while,
    // and if afterwards `currentNetwork` is still empty, we switch it to None
    CancellableFuture.delay(1.second).foreach { _ =>
      if (currentNetwork.currentValue.isEmpty) currentNetwork ! None
    }(Threading.Background)
  }
}

object NetworkModeService extends DerivedLogTag {
  def isOnlineMode(mode: NetworkMode): Boolean =
    mode != NetworkMode.OFFLINE && mode != NetworkMode.UNKNOWN
}
