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
package com.waz.zclient.controllers

import android.content.Context
import com.waz.ZLog._
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.utils.Callback
import com.waz.zclient.{BaseActivity, Injectable, Injector}

import scala.concurrent.Future

class UserAccountsController(implicit injector: Injector, context: Context, ec: EventContext) extends Injectable {
  import Threading.Implicits.Ui
  private implicit val tag: LogTag = logTagFor[UserAccountsController]

  val zms = inject[Signal[ZMessaging]]

  val accounts = Option(ZMessaging.currentAccounts).fold(Signal.const[Seq[AccountData]](Seq()))( _.accounts )

  val currentUser = for {
    zms <- zms
    account <- ZMessaging.currentAccounts.currentAccountData
    user <- account.flatMap(_.userId).fold(Signal.const(Option.empty[UserData]))(accId => zms.usersStorage.signal(accId).map(Some(_)))
  } yield user

  //Things for java
  //TODO hacky mchackerson - needed for the conversation fragment, remove ASAP
  def setIsGroupListener(id: ConvId, callback: Callback[java.lang.Boolean]): Unit =
    for {
      Some(conv) <- zms.map(_.convsStorage).head.flatMap(_.get(id))
      isGroup    <-
        if (conv.team.isEmpty) Future.successful(conv.convType == ConversationType.Group)
        else zms.map(_.membersStorage).head.flatMap(_.getByConv(conv.id)).map(_.map(_.userId).size > 2)
    } callback.callback(isGroup)

  def createAndOpenConversation(users: Array[UserId], requester: ConversationChangeRequester,  activity: BaseActivity): Unit = {
    val createConv = for {
      z <- zms.head
      user <- z.usersStorage.get(z.selfUserId)
      conv <-
        if (users.length == 1)
          z.convsUi.getOrCreateOneToOneConversation(users.head)
        else
          z.convsUi.createGroupConversation(ConvId(), users)
    } yield conv

    createConv.map{ convData =>
      val iConv = activity.getStoreFactory.getConversationStore.getConversation(convData.id.str)
      activity.getStoreFactory.getConversationStore.setCurrentConversation(iConv, requester)
    }(Threading.Ui)
  }

  //TODO: wait for BE specs about the accounts
  def hasCreateConversationPermission: Boolean = true //TODO: Will this bee needed with the accounts stuff?
  def hasRemoveMemberPermission(convId: ConvId): Boolean = true
  def hasAddMemberPermission(convId: ConvId): Boolean = true
  def isTeamSpace: Boolean = false
}
