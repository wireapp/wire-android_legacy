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
package com.waz.permissions

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.threading.{SerialDispatchQueue, Threading}
import com.waz.utils.events.{EventStream, RefreshingSignal, Signal}

import scala.concurrent.{Future, Promise}
import scala.util.Try

class PermissionsService() {

  import PermissionsService._
  private implicit val ec = new SerialDispatchQueue(name = "PermissionsService")

  protected[permissions] val providers      = Signal(Vector.empty[PermissionProvider])
  protected[permissions] val providerSignal = providers.map(_.lastOption)

  def registerProvider(provider: PermissionProvider) = providers.mutate(ps => ps.filter(_ != provider) :+ provider)
  def unregisterProvider(provider: PermissionProvider) = {
    onPermissionsResult(Set.empty)
    providers.mutate(_.filter(_ != provider))
  }

  private lazy val refresh = EventStream[Unit]()

  private lazy val knownKeys = Signal(Set.empty[PermissionKey])

  private lazy val permissions =
    (for {
      keys       <- knownKeys
      Some(prov) <- providerSignal
      res        <- RefreshingSignal(Threading.Ui(prov.hasPermissions(keys.map(Permission(_)))), refresh)
  } yield res).disableAutowiring()

  /**
    * Will return a signal representing the state of the permissions provided. When a request is made via [[requestPermissions]]
    * the backing signal will eventually be udpated and the values in the returned signal then as a consequence. This should be
    * used for parts of the code where behaviour should be blocked until a permission is granted.
 *
    * @return
    */
  def permissions(keys: Set[PermissionKey]): Signal[Set[Permission]] = {
    knownKeys.mutate(_ ++ keys)
    permissions.map(_.filter(p => keys.contains(p.key)))
  }

  def allPermissions(keys: Set[PermissionKey]): Signal[Boolean] = permissions(keys).map(_.forall(_.granted))

  private var currentRequest = Promise[Set[Permission]].success(Set.empty)

  /**
    * Requests permissions as a future, allowing you to chain the result and behave accordingly for each permission you've
    * requested. Note, if there is no permission provider set, this method does not block. Instead, all requested permissions
    * will be returned with the default status granted == false
    *
    * Calling this method will also perform a refresh of the permissions signal, updated any other parts of the app waiting
    * for those permissions.
    *
    * This method should be used if you want to perform some action immediately without necessarily waiting for a provider
    *
    * @return the same set of permissions as requested, but providing their status too.
    */
  def requestPermissions(keys: Set[PermissionKey]): Future[Set[Permission]] = {
    verbose(s"requestPermissions: $keys")
    knownKeys.mutate(_ ++ keys)
    verbose(s"Known: ${knownKeys.currentValue}")

    def request() = {
      currentRequest = Promise()
      providerSignal.head.flatMap {
        case Some(prov) =>
          verbose(s"requesting from provider: $prov")
          for {
            ps <- permissions.head
            _ = verbose(s"current ps: $ps")
            fromKeys       = ps.filter(p => keys.contains(p.key))
            toRequest      = fromKeys.filter(!_.granted)
            alreadyGranted = fromKeys -- toRequest
            _ = verbose(s"to request: $toRequest, already granted: $alreadyGranted")
            res <-
              if (toRequest.isEmpty) {
                currentRequest.tryComplete(Try(toRequest))
                currentRequest.future
              }
              else Threading.Ui(prov.requestPermissions(toRequest)).future.flatMap(_ => currentRequest.future)
          } yield {
            alreadyGranted ++ res
          }
        case None =>
          warn("Currently no permissions provider - can't request permissions at this time. Assuming all are denied")
          currentRequest.tryComplete(Try(keys.map(Permission(_))))
          currentRequest.future
      }
    }

    if (currentRequest.isCompleted) {
      verbose("no outstanding requests")
      request()
    } else {
      verbose("outstanding request, waiting for it to finish first")
      currentRequest.future.flatMap(_ => request())
    }
  }

  def onPermissionsResult(ps: Set[Permission]): Unit = {
    refresh ! ({})
    currentRequest.tryComplete(Try(ps))
  }

  //Convenience method that returns (a Future of) true if all permissions were granted, and false if not.
  def requestAllPermissions(keys: Set[PermissionKey]): Future[Boolean] =
    if (keys.isEmpty) Future.successful(true) else requestPermissions(keys).map(ps => ps.forall(_.granted) && ps.nonEmpty)(Threading.Background)

  //Non-blocking getter for java
  def checkPermission(key: String): Boolean = permissions.currentValue.map(_.filter(_.key == key)).exists(ps => ps.nonEmpty && ps.forall(_.granted))

  //Conviencce method with callback for Java classes - only allows one at a time for simplification
  def requestPermission(key: String, callback: PermissionsCallback) = {
    requestAllPermissions(Set(key)).map(callback.onPermissionResult)(Threading.Ui)
  }
}

object PermissionsService {

  trait PermissionsCallback {
    def onPermissionResult(granted: Boolean): Unit
  }

  type PermissionKey = String

  trait PermissionProvider {

    def requestPermissions(ps: Set[Permission]): Unit

    def hasPermissions(ps: Set[Permission]): Set[Permission]
  }

  case class Permission(key: PermissionKey, granted: Boolean = false)

}