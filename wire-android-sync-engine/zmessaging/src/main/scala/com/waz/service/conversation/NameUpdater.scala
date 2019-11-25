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
package com.waz.service.conversation

import android.text.Html
import androidx.core.text.HtmlCompat
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.{ConvId, Name, UserData, UserId}
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.events.EventContext
import com.waz.utils.{BiRelation, ThrottledProcessingQueue}

import scala.collection.{GenTraversable, breakOut}
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Updates conversation names when any dependency changes (members list, user names).
 */
class NameUpdater(selfUserId:     UserId,
                  usersStorage:   UsersStorage,
                  convs:          ConversationStorage,
                  membersStorage: MembersStorage) {

  private implicit val ev = EventContext.Global
  private implicit val dispatcher = new SerialDispatchQueue(name = "NameUpdaterQueue")

  // unnamed group conversations with active members
  // we are keeping that in memory, it should be fine,
  // ppl usually don't have many unnamed group conversations (especially with many users)
  private var groupConvs = Set.empty[ConvId]
  private var groupMembers = BiRelation.empty[ConvId, UserId]

  private val queue = new ThrottledProcessingQueue[Any](500.millis, { ids => updateGroupNames(ids.toSet) }, "GroupConvNameUpdater")

  // load groups and members
  lazy val init = for {
    all <- convs.list()
    groups = all.filter(c => c.convType == ConversationType.Group && c.name.isEmpty)
    members <- Future.traverse(groups) { c => membersStorage.getActiveUsers(c.id) map (c.id -> _) }
  } yield {
    groupConvs ++= groups.map(_.id)
    addMembers(members)
  }

  def registerForUpdates(): Unit = {

    usersStorage.onAdded { onUsersChanged(_) }
    usersStorage.onUpdated { updates =>
      onUsersChanged(updates.collect {
        case (prev, current) if prev.name != current.name || prev.displayName != current.displayName => current
      })
    }

    convs.onAdded { cs =>
      val unnamedGroups = cs.collect { case c if c.convType == ConversationType.Group && c.name.isEmpty => c.id }
      if (unnamedGroups.nonEmpty) {
        init map { _ =>
          groupConvs ++= unnamedGroups
          addMembersForConvs(unnamedGroups)
        }
      }
    }

    convs.onUpdated { updates =>
      val changedGroups = updates.collect {
        case (prev, conv) if conv.convType == ConversationType.Group && prev.name.isDefined != conv.name.isDefined => conv
      }

      if (changedGroups.nonEmpty) {
        val (named, unnamed) = changedGroups.partition(_.name.isDefined)
        val namedIds = named.map(_.id)
        val unnamedIds = unnamed.map(_.id)

        init map { _ =>
          groupConvs = groupConvs -- namedIds ++ unnamedIds
          if (named.nonEmpty)
            groupMembers = groupMembers.removeAllLeft(namedIds)
          if (unnamed.nonEmpty)
            addMembersForConvs(unnamedIds) map { _ => queue.enqueue(unnamedIds)}
        }
      }
    }

    membersStorage.onAdded { members =>
      init map { _ =>
        val ms = members.filter(m => groupConvs(m.convId))
        groupMembers = groupMembers ++ ms.map(m => m.convId -> m.userId)

        queue.enqueue(ms.map(_.convId).distinct)
      }
    }

    membersStorage.onDeleted { members =>
      init map { _ =>
        val ms = members.filter(m => groupConvs(m._2))
        groupMembers = groupMembers -- ms.map(m => m._2 -> m._1)

        queue.enqueue(ms.map(_._2).distinct)
      }
    }
  }

  def forceNameUpdate(id: ConvId, defaultName: String) = convs.get(id) flatMap {
    case Some(conv) if conv.convType == ConversationType.Group =>
      for {
        members <- membersStorage.getByConv(conv.id)
        users <- usersStorage.getAll(members.map(_.userId).filter(_ != selfUserId))
        name = generatedName(users.map {
          case Some(u) if !u.deleted => Some(u.getDisplayName)
          case _                     => None
        })
        newName = if(name.isEmpty) Name(defaultName) else name
        res <- convs.update(conv.id,  _.copy(generatedName = newName))
      } yield res
    case Some(conv) => // one to one conv should use full user name
      usersStorage.get(UserId(conv.id.str)) flatMap {
        case Some(user) if !user.deleted => convs.update(conv.id, _.copy(generatedName = user.name))
        case None => Future successful None
      }
    case None =>
      Future successful None
  }

  private def addMembersForConvs(convs: Traversable[ConvId]) =
    Future.traverse(convs) { c => membersStorage.getActiveUsers(c) map (c -> _) } map addMembers

  private def addMembers(members: Traversable[(ConvId, Seq[UserId])]) =
    groupMembers ++= members.flatMap { case (c, us) => us.map(c -> _) }

  private def onUsersChanged(users: Seq[UserData]) = {

    def updateGroups() = queue.enqueue(users.map(_.id))

    def updateOneToOnes() = {
      val names: Map[ConvId, Name] = users.collect {
        case u if u.connection != ConnectionStatus.Unconnected && !u.deleted => ConvId(u.id.str) -> u.name // one to one use full name
      } (breakOut)

      if (names.isEmpty) Future successful Nil
      else convs.updateAll2(names.keys, { c => c.copy(generatedName = names(c.id)) })
    }

    updateGroups()
    updateOneToOnes()
  }


  private def updateGroupNames(ids: Set[Any]) = init flatMap { _ =>
    val convIds = ids flatMap {
      case id: ConvId => Seq(id)
      case id: UserId => groupMembers.foreset(id)
      case _ => Nil
    }

    val members: Map[ConvId, Seq[UserId]] = convIds.map { id => id -> groupMembers.afterset(id).toSeq } (breakOut)
    val users = members.flatMap(_._2).toSeq.distinct.filter(_ != selfUserId)

    usersStorage.getAll(users) flatMap { uds =>
      val names: Map[UserId, Option[Name]] = users.zip(uds.map(_.flatMap {
        case u if !u.deleted => Some(u.getDisplayName)
        case _               => None
      }))(breakOut)
      val convNames = members.mapValues { us => generatedName(us.filter(_ != selfUserId) map { names.get(_).flatten }) }
      convs.updateAll2(convIds, { c => convNames.get(c.id).fold(c) { name => c.copy(generatedName = name) } })
    }
  }

  private def generatedName(userNames: GenTraversable[Option[Name]]): Name = {
    Name(getName(userNames).toString)
  }

  private def getName(userNames: GenTraversable[Option[Name]]): String = {
    val name = userNames.flatten.filter(_.nonEmpty).mkString(", ")
    HtmlCompat.fromHtml(name, HtmlCompat.FROM_HTML_MODE_LEGACY).toString
  }
}

object NameUpdater {
  def generatedName(convType: ConversationType)(users: GenTraversable[UserData]): Name = {
    val us = users.filter(u => u.connection != ConnectionStatus.Self && !u.deleted)
    if (convType == ConversationType.Group) Name(us.map(user => user.getDisplayName).filter(_.nonEmpty).mkString(", "))
    else us.headOption.fold(Name.Empty)(_.name)
  }
}
