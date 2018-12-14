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

import com.waz.content.{Likes, MessageAndLikesStorageImpl, MessagesStorage, ReactionsStorage}
import com.waz.specs.AndroidFreeSpec
import com.waz.utils.events.EventStream

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class MessageAndLikesStorageSpec extends AndroidFreeSpec {
  feature("Message and likes storage") {

    val self = UserId("1")

    val messages = Map(
      MessageId("1") -> MessageData(MessageId("1")),
      MessageId("2") -> MessageData(MessageId("2")),
      MessageId("3") -> MessageData(MessageId("3"), quote = Some(QuoteContent(MessageId("2"), validity = true)))
    )

    val likes = Map(
      MessageId("1") -> Likes(MessageId("1"), Map(self -> RemoteInstant.Epoch)),
      MessageId("2") -> Likes(MessageId("2"), Map(UserId("2") -> RemoteInstant.Epoch))
    )

    scenario("Get messages and relevant info from ids") {

      val messagesStorage = mock[MessagesStorage]
      val reactionsStorage = mock[ReactionsStorage]

      val ids = Seq(MessageId("1"), MessageId("2"), MessageId("3"))

      (messagesStorage.onDeleted _).expects().anyNumberOfTimes().returning(EventStream[Seq[MessageId]]())
      (messagesStorage.onChanged _).expects().anyNumberOfTimes().returning(EventStream[Seq[MessageData]]())
      (reactionsStorage.onChanged _).expects().anyNumberOfTimes().returning(EventStream[Seq[Liking]]())

      (messagesStorage.getMessages _).expects(*).anyNumberOfTimes().onCall { ids: Seq[MessageId] =>
          Future.successful(ids.map(messages.get))
      }
      (reactionsStorage.loadAll _).expects(*).anyNumberOfTimes().onCall { ids: Seq[MessageId] =>
        Future.successful(ids.flatMap(likes.get).toVector)
      }
      (messagesStorage.getMessage _).expects(*).anyNumberOfTimes().onCall { id: MessageId =>
        Future.successful(messages.get(id))
      }

      val messageAndLikesStorage = new MessageAndLikesStorageImpl(self, messagesStorage, reactionsStorage)

      val result = Await.result(messageAndLikesStorage.apply(Seq(MessageId("1"), MessageId("2"), MessageId("3"))), 10.seconds)

      result.size shouldBe 3
      result.exists(_.quote.exists(_.id == MessageId("2"))) shouldBe true
      result.count(_.likedBySelf == true) shouldBe 1
      result.count(_.likes.nonEmpty) shouldBe 2
    }
  }
}
