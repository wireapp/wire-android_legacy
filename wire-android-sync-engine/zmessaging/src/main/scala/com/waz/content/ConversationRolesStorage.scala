/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.content

import android.content.Context
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationRoleAction.ConversationRoleActionDao
import com.waz.model.{ConvId, ConversationAction, ConversationRole, ConversationRoleAction}
import com.waz.threading.Threading
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.events.{RefreshingSignal, Signal}
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}
import com.waz.threading.CancellableFuture
import com.waz.log.LogSE._

import scala.concurrent.Future

trait ConversationRolesStorage extends CachedStorage[(String, String, Option[ConvId]), ConversationRoleAction] {
  def getRoleByLabel(label: String, convId: Option[ConvId] = None): Future[ConversationRole]
  def getRolesByConvId(convId: ConvId): Future[Set[ConversationRole]]

  def defaultRoles: Signal[Set[ConversationRole]]
  def rolesByConvId(convId: Option[ConvId]): Signal[Set[ConversationRole]]

  def createOrUpdate(convId: ConvId, roles: Set[ConversationRole]): Future[Unit]
  def setDefault(roles: Set[ConversationRole]): Future[Unit]

  def removeByConvId(convId: ConvId): Future[Unit]
}

class ConversationRolesStorageImpl(context: Context, storage: ZmsDatabase)
  extends CachedStorageImpl[(String, String, Option[ConvId]), ConversationRoleAction](
    new TrimmingLruCache(context, Fixed(1024)), storage)(ConversationRoleActionDao, LogTag("ConversationRolesStorage_Cached")
  ) with ConversationRolesStorage with DerivedLogTag {
  import Threading.Implicits.Background

  defaultRoles.head.foreach(roles => if (roles.isEmpty) setDefault(ConversationRole.defaultRoles))

  override def getRoleByLabel(label: String, convId: Option[ConvId] = None): Future[ConversationRole] =
    find({ cra => cra.label == label && cra.convId == convId }, ConversationRoleActionDao.findForRoleAndConv(label, convId)(_), identity).map { res =>
      val actionNames = res.map(_.action).toSet
      ConversationRole(label, ConversationAction.allActions.filter(ca => actionNames.contains(ca.name)))
    }

  override def getRolesByConvId(convId: ConvId): Future[Set[ConversationRole]] = getRolesByConvId(Some(convId))

  override def defaultRoles: Signal[Set[ConversationRole]] = rolesByConvId(None)

  override def rolesByConvId(convId: Option[ConvId]): Signal[Set[ConversationRole]] = new RefreshingSignal[Set[ConversationRole]](
    loader       = CancellableFuture.lift(getRolesByConvId(convId)),
    refreshEvent = this.onChanged.map(_.filter(_.convId == convId)).filter(_.nonEmpty)
  )

  override def createOrUpdate(convId: ConvId, newRoles: Set[ConversationRole]): Future[Unit] = for {
    currentRoles         <- getRolesByConvId(convId)
    newRolesAreDifferent =  currentRoles != newRoles
    _                    <- if (newRolesAreDifferent && currentRoles.nonEmpty) removeAll(unapply(Some(convId), currentRoles)) else Future.successful(())
    defaultRoles         <- if (newRolesAreDifferent) defaultRoles.head else Future.successful(newRoles)
    newRolesAreDifferent =  defaultRoles != newRoles
    _                    <- if (newRolesAreDifferent) insertAll(newRoles.flatMap(_.toRoleActions(Some(convId)))) else Future.successful(())
  } yield ()

  override def setDefault(newRoles: Set[ConversationRole]): Future[Unit] = for {
    defaultRoles <- defaultRoles.head
    _            <- removeAll(unapply(None, defaultRoles))
    _            <- insertAll(newRoles.flatMap(_.toRoleActions(None)))
  } yield ()

  override def removeByConvId(convId: ConvId): Future[Unit] = createOrUpdate(convId, Set.empty)

  private def getRolesByConvId(convId: Option[ConvId]): Future[Set[ConversationRole]] =
    find({ _.convId == convId }, ConversationRoleActionDao.findForConv(convId)(_), identity).map {
      _.groupBy(_.label).map { case (label, actions) =>
        val actionNames = actions.map(_.action).toSet
        ConversationRole(label, ConversationAction.allActions.filter(ca => actionNames.contains(ca.name)))
      }.toSet
    }.flatMap { roles =>
      if (roles.nonEmpty) Future.successful(roles)
      else if (convId.isDefined) getRolesByConvId(None)
      else {
        warn(l"No default conversation roles found")
        Future.successful(ConversationRole.defaultRoles)
      }
    }

  private def unapply(convId: Option[ConvId], roles: Set[ConversationRole]) =
    roles.flatMap(role => role.actions.map(action => (role.label, action.name, convId)))

}
