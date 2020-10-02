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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogShow.SafeToLog
import com.waz.log.LogSE._
import com.waz.model.errors.PermissionDeniedError
import com.wire.signals.SerialDispatchQueue
import com.waz.threading.Threading
import com.wire.signals.{EventStream, RefreshingSignal, Signal}

import scala.collection.immutable.ListSet
import scala.concurrent.{Future, Promise}
import scala.util.Try

class PermissionsService extends DerivedLogTag {

  import PermissionsService._
  private implicit val ec = SerialDispatchQueue(name = "PermissionsService")

  protected[permissions] val providers      = Signal(Vector.empty[PermissionProvider])
  protected[permissions] val providerSignal = providers.map(_.lastOption)

  def registerProvider(provider: PermissionProvider) = providers.mutate(ps => ps.filter(_ != provider) :+ provider)
  def unregisterProvider(provider: PermissionProvider) = {
    onPermissionsResult(ListSet.empty)
    providers.mutate(_.filter(_ != provider))
  }

  private lazy val refresh = EventStream[Unit]()

  private lazy val knownKeys = Signal(ListSet.empty[PermissionKey])

  private lazy val permissions =
    (for {
      keys       <- knownKeys
      Some(prov) <- providerSignal
      res        <- RefreshingSignal(Threading.Ui.apply(prov.hasPermissions(keys.map(Permission(_)))), refresh)
  } yield res).disableAutowiring()

  /**
    * Will return a signal representing the state of the permissions provided. When a request is made via [[requestPermissions]]
    * the backing signal will eventually be udpated and the values in the returned signal then as a consequence. This should be
    * used for parts of the code where behaviour should be blocked until a permission is granted.
 *
    * @return
    */
  def permissions(keys: ListSet[String]): Signal[Set[Permission]] = {
    knownKeys.mutate(_ ++ keys)
    permissions.map(_.filter(p => keys.contains(p.key)))
  }

  def allPermissions(keys: ListSet[String]): Signal[Boolean] = permissions(keys).map(_.forall(_.granted))

  private var currentRequest = Promise[ListSet[Permission]].success(ListSet.empty)

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
  def requestPermissions(keys: ListSet[String]): Future[ListSet[Permission]] = {
    verbose(l"requestPermissions: ${keys.map(showString)}")
    knownKeys.mutate(_ ++ keys)

    def request() = {
      currentRequest = Promise()
      providerSignal.head.flatMap {
        case Some(prov) =>
          verbose(l"requesting from provider: $prov")
          for {
            ps <- permissions.head
            _ = verbose(l"current ps: $ps")
            fromKeys       = ps.filter(p => keys.contains(p.key))
            toRequest      = fromKeys.filter(!_.granted)
            alreadyGranted = fromKeys -- toRequest
            _ = verbose(l"to request: $toRequest, already granted: $alreadyGranted")
            res <-
              if (toRequest.isEmpty) {
                currentRequest.tryComplete(Try(toRequest))
                currentRequest.future
              }
              else Threading.Ui.apply(prov.requestPermissions(toRequest)).future.flatMap(_ => currentRequest.future)
          } yield {
            alreadyGranted ++ res
          }
        case None =>
          warn(l"Currently no permissions provider - can't request permissions at this time. Assuming all are denied")
          currentRequest.tryComplete(Try(keys.map(Permission(_))))
          currentRequest.future
      }
    }

    if (currentRequest.isCompleted) {
      verbose(l"no outstanding requests")
      request()
    } else {
      verbose(l"outstanding request, waiting for it to finish first")
      currentRequest.future.flatMap(_ => request())
    }
  }

  def onPermissionsResult(ps: ListSet[Permission]): Unit = {
    refresh ! ({})
    currentRequest.tryComplete(Try(ps))
  }

  //Convenience method that returns (a Future of) true if all permissions were granted, and false if not.
  def requestAllPermissions(keys: ListSet[String]): Future[Boolean] =
    if (keys.isEmpty) Future.successful(true) else requestPermissions(keys).map(ps => ps.forall(_.granted) && ps.nonEmpty)(Threading.Background)

  def ensurePermissions(keys: ListSet[PermissionKey]): Future[Unit] =
    requestPermissions(keys).flatMap { ps =>
      val denied = ps.toSeq.filter(!_.granted)
      if (denied.isEmpty) Future.successful(())
      else Future.failed(PermissionDeniedError(denied.map(_.key)))
    }(Threading.Background)

  //Non-blocking getter for java
  def checkPermission(key: String): Boolean = permissions.currentValue.map(_.filter(_.key == key)).exists(ps => ps.nonEmpty && ps.forall(_.granted))

  //Conviencce method with callback for Java classes - only allows one at a time for simplification
  def requestPermission(key: String, callback: PermissionsCallback) = {
    requestAllPermissions(ListSet(key)).map(callback.onPermissionResult)(Threading.Ui)
  }
}

object PermissionsService {

  type PermissionKey = String

  trait PermissionsCallback {
    def onPermissionResult(granted: Boolean): Unit
  }

  trait PermissionProvider extends SafeToLog {

    def requestPermissions(ps: ListSet[Permission]): Unit

    def hasPermissions(ps: ListSet[Permission]): ListSet[Permission]
  }

  case class Permission(key: PermissionKey, granted: Boolean = false) extends SafeToLog
}
