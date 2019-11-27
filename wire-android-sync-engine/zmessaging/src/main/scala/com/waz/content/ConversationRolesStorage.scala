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
import com.waz.model.ConversationRoleAction.ConversationRoleActionDao
import com.waz.model.{ConvId, ConversationAction, ConversationRole, ConversationRoleAction}
import com.waz.threading.Threading
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}

import scala.concurrent.Future

trait ConversationRolesStorage extends CachedStorage[(String, String, Option[ConvId]), ConversationRoleAction] {
  def rolesByLabel(label: String, convId: Option[ConvId] = None): Future[ConversationRole]
  def rolesByConvId(convId: ConvId): Future[Set[ConversationRole]]
  def defaultRoles: Future[Set[ConversationRole]]

  def createOrUpdate(convId: ConvId, roles: Set[ConversationRole]): Future[Unit]
  def setDefault(roles: Set[ConversationRole]): Future[Unit]

  def removeByConvId(convId: ConvId): Future[Unit]
}

class ConversationRolesStorageImpl(context: Context, storage: ZmsDatabase)
  extends CachedStorageImpl[(String, String, Option[ConvId]), ConversationRoleAction](
    new TrimmingLruCache(context, Fixed(1024)), storage)(ConversationRoleActionDao, LogTag("ConversationRolesStorage_Cached")
  ) with ConversationRolesStorage {
  import Threading.Implicits.Background

  override def rolesByLabel(label: String, convId: Option[ConvId] = None): Future[ConversationRole] =
    find({ cra => cra.label == label && cra.convId == convId }, ConversationRoleActionDao.findForRoleAndConv(label, convId)(_), identity).map { res =>
      val actionNames = res.map(_.action).toSet
      ConversationRole(label, ConversationAction.allActions.filter(ca => actionNames.contains(ca.name)))
    }(Threading.Background)

  override def rolesByConvId(convId: ConvId): Future[Set[ConversationRole]] = rolesByConvId(Some(convId))

  override def defaultRoles: Future[Set[ConversationRole]] = rolesByConvId(None)

  override def createOrUpdate(convId: ConvId, newRoles: Set[ConversationRole]): Future[Unit] = for {
    currentRoles         <- rolesByConvId(convId)
    newRolesAreDifferent =  currentRoles != newRoles
    _                    <- if (newRolesAreDifferent && currentRoles.nonEmpty) removeAll(unapply(Some(convId), currentRoles)) else Future.successful(())
    defaultRoles         <- if (newRolesAreDifferent) defaultRoles else Future.successful(newRoles)
    newRolesAreDifferent =  defaultRoles != newRoles
    _                    <- if (newRolesAreDifferent) insertAll(newRoles.flatMap(_.toRoleActions(Some(convId)))) else Future.successful(())
  } yield ()

  override def setDefault(newRoles: Set[ConversationRole]): Future[Unit] = for {
    defaultRoles <- defaultRoles
    _            <- removeAll(unapply(None, defaultRoles))
    _            <- insertAll(newRoles.flatMap(_.toRoleActions(None)))
  } yield ()

  override def removeByConvId(convId: ConvId): Future[Unit] = createOrUpdate(convId, Set.empty)

  private def rolesByConvId(convId: Option[ConvId]) =
    find({ _.convId == convId }, ConversationRoleActionDao.findForConv(convId)(_), identity).map {
      _.groupBy(_.label).map { case (label, actions) =>
        val actionNames = actions.map(_.action).toSet
        ConversationRole(label, ConversationAction.allActions.filter(ca => actionNames.contains(ca.name)))
      }.toSet
    }(Threading.Background)

  private def unapply(convId: Option[ConvId], roles: Set[ConversationRole]) =
    roles.flatMap(role => role.actions.map(action => (role.label, action.name, convId)))
}
