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
package com.waz.model

import android.database.Cursor
import com.waz.db.ZMessagingDB
import com.waz.model.ConversationData.{ConversationDataDao, ConversationType}
import com.waz.utils.wrappers.DB
import org.robolectric.Robolectric
import org.scalatest._
import org.threeten.bp.Instant

import scala.util.Random

@Ignore class ConversationDataDaoSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {

  lazy val dbHelper = new ZMessagingDB(Robolectric.application, "dbName")

  lazy val convId = ConvId()

  def conv(name: String, ct: ConversationType = ConversationType.Group, archived: Boolean = false) =
    ConversationData(ConvId(), RConvId(), Some(name), UserId(), ct, archived = archived, lastEventTime = RemoteInstant.ofEpochMilli(Random.nextInt(10000)))

  lazy val conversations = List(
    conv("self", ConversationType.Self),
    conv("group 1"),
    conv("group 2"),
    conv("onetoone 3", ConversationType.OneToOne),
    conv("group 4"),
    conv("group 5 test"),
    conv("archived test", archived = true),
    conv("archived", ConversationType.OneToOne, archived = true)
  )

  lazy val users = List(UserData("From"), UserData("To"))

  lazy val conversationMembers = List(
    ConversationMemberData(users(0).id, conversations(3).id),
    ConversationMemberData(users(1).id, conversations(3).id)
  )

  after {
    dbHelper.close()
  }

  implicit def db: DB = dbHelper.getWritableDatabase
  import ConversationDataDao._

  feature("conversations ordering") {

    scenario("self conversation should be first in result list") {
      insertOrReplace(conversations)

      list(allConversations).head shouldEqual conversations(0)
    }

    scenario("archived conversations should be at the bottom") {
      insertOrReplace(conversations)

      val archived = conversations.filter(_.archived)
      list(allConversations).reverse.take(archived.length).toSet shouldEqual archived.toSet
    }

    scenario("conversations should be sorted by lastEventTime - fresh first") {
      insertOrReplace(conversations)

      list(allConversations) shouldEqual conversations.sortBy(c =>
        if (c.convType == ConversationType.Self) Long.MaxValue
        else if (c.archived) c.lastEventTime.toEpochMilli + 1 - Long.MaxValue
        else c.lastEventTime.toEpochMilli).reverse
    }

    scenario("incoming connection request convs should be below self") {
      val convs = conv("incoming", ConversationType.Incoming) :: conv("incoming 2", ConversationType.Incoming) :: conversations
      insertOrReplace(convs)

      list(allConversations) shouldEqual convs.sortBy(c =>
        if (c.convType == ConversationType.Self) Long.MaxValue
        else if (c.convType == ConversationType.Incoming) Long.MaxValue - 100000 + c.lastEventTime.toEpochMilli
        else if (c.archived) c.lastEventTime.toEpochMilli + 1 - Long.MaxValue
        else c.lastEventTime.toEpochMilli).reverse
    }
  }

  def printCursor(c: Cursor, cols: String*) = {
    while(c.moveToNext()) {
      println(cols.map { col => col + ": " + c.getString(c.getColumnIndex(col))}.mkString(", "))
    }
    c.close()
  }
}
