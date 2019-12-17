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
package com.waz.service

import com.waz.content.ConversationRolesStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.{ConvId, ConversationRole}
import com.waz.threading.Threading
import com.waz.utils.events.Signal

import scala.concurrent.Future

trait ConversationRolesService {
  def ensureDefaultRoles(): Future[Unit]

  def defaultRoles: Signal[Set[ConversationRole]]
  def rolesByConvId(convId: ConvId): Signal[Set[ConversationRole]]

  def createOrUpdate(convId: ConvId, roles: Set[ConversationRole]): Future[Unit]
  def setDefaultRoles(roles: Set[ConversationRole]): Future[Unit]

  def removeByConvId(convId: ConvId): Future[Unit]
}

class ConversationRolesServiceImpl(storage: ConversationRolesStorage) extends ConversationRolesService with DerivedLogTag {
  import ConversationRolesStorage.DefaultConvId
  import Threading.Implicits.Background

  override lazy val defaultRoles: Signal[Set[ConversationRole]] = rolesByConvId(DefaultConvId)

  override def rolesByConvId(convId: ConvId): Signal[Set[ConversationRole]] =
    for {
      contents          <- storage.contents
      convActions       =  contents.filterKeys(_._3 == convId).values
      (roleActions, id) =  if (convActions.nonEmpty) (convActions, convId)
                           else (contents.filterKeys(_._3 == DefaultConvId).values, DefaultConvId)
    } yield ConversationRole.fromRoleActions(roleActions).getOrElse(id, Set.empty)

  override def createOrUpdate(convId: ConvId, newRoles: Set[ConversationRole]): Future[Unit] =
    for {
      currentRoles         <- storage.getRolesByConvId(convId)
      newRolesAreDifferent =  currentRoles != newRoles
      _                    <- if (newRolesAreDifferent && currentRoles.nonEmpty)
                                storage.removeAll(currentRoles.flatMap(_.toRoleActions(convId).map(_.id)))
                              else Future.successful(())
      defaultRoles         <- if (newRolesAreDifferent) defaultRoles.head else Future.successful(newRoles)
      newRolesAreDifferent =  defaultRoles != newRoles
      _                    <- if (newRolesAreDifferent && newRoles.nonEmpty)
                                storage.insertAll(newRoles.flatMap(_.toRoleActions(convId)))
                              else Future.successful(())
    } yield ()

  override def setDefaultRoles(newRoles: Set[ConversationRole]): Future[Unit] =
    storage.getRolesByConvId(DefaultConvId).map { defaultRoles =>
      if (defaultRoles != newRoles) {
        storage.removeAll(defaultRoles.flatMap(_.toRoleActions(DefaultConvId).map(_.id)))
          .map(_ => storage.insertAll(newRoles.flatMap(_.toRoleActions(DefaultConvId))))
          .map(_ => verbose(l"new default roles set: $newRoles"))
      }
    }

  override def ensureDefaultRoles(): Future[Unit] = storage.getRolesByConvId(DefaultConvId).map { roles =>
    if (roles.isEmpty) storage.insertAll(ConversationRole.defaultRoles.flatMap(_.toRoleActions(DefaultConvId)))
  }

  override def removeByConvId(convId: ConvId): Future[Unit] = createOrUpdate(convId, Set.empty)
}