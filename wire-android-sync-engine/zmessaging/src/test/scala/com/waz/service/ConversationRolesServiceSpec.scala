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
import com.waz.model.{ConvId, ConversationRole, ConversationRoleAction}
import com.waz.specs.AndroidFreeSpec
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Signal

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class ConversationRolesServiceSpec extends AndroidFreeSpec with DerivedLogTag {
  import ConversationRole._
  import ConversationRolesStorage._
  import Threading.Implicits.Background

  type K = (String, String, ConvId)
  type V = ConversationRoleAction

  private val storage = mock[ConversationRolesStorage]
  private val roleActions = Signal(List.empty[V])

  private var _service = Option.empty[ConversationRolesService]

  // TODO: Unit tests working with signals (like these here) are flaky.
  // The code may publish data to the signal and it might or might not be ready to the subscribers right in the next line.
  // This is why we use so many delays in the unit tests. After we fix the flakiness, they can be removed.
  private def getService: Future[ConversationRolesService] = _service match {
    case Some(service) => Future.successful(service)
    case None =>
      val service = new ConversationRolesServiceImpl(storage)
      _service = Some(service)
      service.ensureDefaultRoles().flatMap(_ => CancellableFuture.delay(300.millis).future).map(_ => service)
  }

  private def insertAll(rs: Seq[V]) = roleActions.head.map { currentRoles =>
    val keySet = rs.map(_.id).toSet
    roleActions ! currentRoles.filterNot(r => keySet.contains(r.id)) ++ rs
  }

  (storage.getRolesByConvId _).expects(*).anyNumberOfTimes().onCall { convId: ConvId =>
    roleActions.head.map { ra =>
      val map = fromRoleActions(ra)
      map.get(convId).orElse(map.get(DefaultConvId)).getOrElse {
        insertAll(defaultRoles.flatMap(_.toRoleActions(DefaultConvId)).toSeq)
        defaultRoles
      }
    }
  }

  (storage.removeAll _).expects(*).anyNumberOfTimes().onCall { keys: Iterable[K] =>
    roleActions.head.map { currentRoles =>
      val keySet = keys.toSet
      roleActions ! currentRoles.filterNot(r => keySet.contains(r.id))
    }.map(_ => ())
  }

  (storage.insertAll(_: Traversable[V])).expects(*).anyNumberOfTimes().onCall { vs: Traversable[V] =>
    insertAll(vs.toSeq).map(_ => vs.toSet)
  }

  (storage.contents _).expects().anyNumberOfTimes().returning(roleActions.map(_.toIdMap))

  feature("Manage conversation roles") {

    scenario("Ensure default roles") {
      awaitAllTasks()
      // given

      // when
      val roles = for {
        service <- getService
        roles   <- service.defaultRoles.head
      } yield roles

      awaitAllTasks()

      // then
      result(roles) shouldEqual defaultRoles
    }

    scenario("Get default roles by convId") {
      awaitAllTasks()
      // given
      val convId = ConvId()

      // when
      val currentRoles = for {
        service <- getService
        roles   <- service.rolesByConvId(convId).head
      } yield roles

      awaitAllTasks()

      // then
      result(currentRoles) shouldEqual defaultRoles
    }

    scenario("Create roles for a conversation") {
      awaitAllTasks()
      // given
      val convId = ConvId()
      val originalRoles = Set(ConversationRole.MemberRole)

      // when
      val f = for {
        service     <- getService
        _           <- service.createOrUpdate(convId, originalRoles)
        roleActions <- roleActions.head
      } yield roleActions.groupBy(_.convId)

      awaitAllTasks()


      // then
      val res = result(f)
      res.keySet shouldEqual Set(convId, DefaultConvId)
      res(convId).toSet shouldEqual originalRoles.flatMap(_.toRoleActions(convId))
    }

    scenario("Add customized roles") {
      awaitAllTasks()
      // given
      val convId = ConvId()
      val originalRoles = Set(ConversationRole.MemberRole)

      // when
      val currentRoles = for {
        service     <- getService
        _           <- service.createOrUpdate(convId, originalRoles)
        roles       <- service.rolesByConvId(convId).head
      } yield roles

      awaitAllTasks()

      // then
      result(currentRoles) shouldEqual originalRoles
    }

    scenario("Remove customized roles") {
      awaitAllTasks()

      val convId = ConvId()
      val originalRoles = Set(ConversationRole.MemberRole)

      val afterAdding = for {
        service     <- getService
        _           <- service.createOrUpdate(convId, originalRoles)
        roles       <- service.rolesByConvId(convId).head
      } yield roles

      awaitAllTasks()

      result(afterAdding) shouldEqual originalRoles

      val afterRemoval = for {
        service     <- getService
        _           <- service.removeByConvId(convId)
        roles       <- service.rolesByConvId(convId).head
      } yield roles

      awaitAllTasks()

      result(afterRemoval) shouldEqual defaultRoles
    }
  }
}
