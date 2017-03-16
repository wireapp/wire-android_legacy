/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.permissions

import java.lang.ref.WeakReference

import android.app.Activity
import android.support.v4.app.ActivityCompat
import com.waz.api.{Permission, PermissionProvider}
import java.util
import scala.collection.JavaConverters._

class PermissionRequest(activity: WeakReference[Activity], target: WeakReference[PermissionProvider#ResponseHandler], permissions: Set[Permission]) {

  import PermissionRequest._

  def proceed() =
    Option(activity.get).foreach(ActivityCompat.requestPermissions(_, permissions.map(_.id).toArray, SERequestId))

  def cancel() = respondAll(Permission.Status.DENIED)

  def grant() = respondAll(Permission.Status.GRANTED)

  private def respondAll(resp: Permission.Status) =
    Option(target.get).foreach(_.handleResponse(permissions.map(_ -> resp).toMap.asJava))

  def getTargetResponseHandler: PermissionProvider#ResponseHandler = target.get

  def getPermissionIds: Array[String] = permissions.map(_.id).toArray
}

object PermissionRequest {
  val SERequestId = 162

  def apply(activity: Activity, target: PermissionProvider#ResponseHandler, permissions: util.Set[Permission]): PermissionRequest =
    new PermissionRequest(new WeakReference(activity), new WeakReference(target), permissions.asScala.toSet)
}
