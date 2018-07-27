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
package com.waz.sync.queue

import com.waz.Generators.SyncRequests._
import com.waz.testutils.Matchers._
import com.waz.content.{SyncStorage, ZmsDatabase}
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.model.sync.SyncRequest._
import com.waz.model.sync.{SyncCommand, SyncJob, SyncRequest}
import com.waz.sync.client.{ConversationsClient, UsersClient}
import com.waz.sync.queue.SyncJobMerger.{Merged, Unchanged, Updated}
import org.robolectric.Robolectric
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}
import org.threeten.bp.Instant

import scala.concurrent.Future

@Ignore class RequestMergerSpec extends FeatureSpec with Matchers with GeneratorDrivenPropertyChecks with RobolectricTests {

  lazy val storage = new ZmsDatabase(UserId(), Robolectric.application)

  feature("Merge keys") {
    scenario("Refs should not be set for requests without ref") {
      forAll(Gen.oneOf(
        arbSimpleSyncRequest.arbitrary,
        arbitrary[SyncUser],
        arbitrary[SyncConversation])) { req =>
          whenever(req.cmd != SyncCommand.PostConnectionStatus) { req.mergeKey shouldEqual req.cmd }
        }
    }

    scenario("Conversation requests should have convId in mergeKey") {
      forAll(arbitrary[RequestForConversation]) { req =>
        req.mergeKey should beMatching({
          case (cmd, _: ConvId) if cmd == req.cmd => true
          case (cmd, _: ConvId, _) if cmd == req.cmd => true
        })
      }
    }

    scenario("User requests should have userId in mergeKey") {
      forAll(arbitrary[RequestForUser]) { req =>
        req.mergeKey should beMatching({
          case (cmd, _: UserId) if cmd == req.cmd => true
          case (cmd, _: UserId, _) if cmd == req.cmd => true
        })
      }
    }
  }

  feature("Duplicates") {
    scenario("Duplicate requests should always be merged") {
      forAll(arbitrary[SyncRequest]) { req => req.merge(req) shouldEqual Merged(req) }
    }

    scenario("Users requests are duplicates if users are a subset") {
      val users1 = SyncUser(Set(UserId("1"), UserId("2")))
      val users2 = SyncUser(Set(UserId("2"), UserId("1"), UserId("extra")))
      users1 isDuplicateOf users2 shouldEqual true
      users2 isDuplicateOf users1 shouldEqual false
    }

    scenario("Convs requests are duplicates if convs are a subset") {
      val convs1 = SyncConversation(Set(ConvId("1"), ConvId("2")))
      val convs2 = SyncConversation(Set(ConvId("2"), ConvId("1"), ConvId("extra")))
      convs1 isDuplicateOf convs2 shouldEqual true
      convs2 isDuplicateOf convs1 shouldEqual false
    }

    scenario("ConvMembers requests are duplicates if users are a subset for the same conversation id") {
      val conv = ConvId()
      val members1 = PostConvJoin(conv, Set(UserId("1"), UserId("2")))
      val members2 = PostConvJoin(conv, Set(UserId("2"), UserId("1"), UserId("extra")))
      val members3 = PostConvJoin(ConvId(), Set(UserId("1")))
      members1 isDuplicateOf members2 shouldEqual true
      members2 isDuplicateOf members1 shouldEqual false
      members3 isDuplicateOf members1 shouldEqual false
      members3 isDuplicateOf members2 shouldEqual false
    }
  }

  feature("SyncRequest.merge") {
    scenario("Requests with different commands can not be merged") {
      forAll(arbitrary[SyncRequest], arbitrary[SyncRequest]) { (req1, req2) =>
        whenever(req1.cmd != req2.cmd) {
          req1 merge req2 shouldEqual Unchanged
        }
      }
    }

    scenario("Post* requests can always be merged") {
      forAll(arbitrary[PostSelfPicture], arbitrary[PostSelfPicture]) { (s1, s2) => s1 merge s2 shouldEqual Merged(s2) }
      forAll(arbitrary[PostSelf], arbitrary[PostSelf]) { (s1, s2) => s1 merge s2 shouldEqual Merged(s2) }
    }

    scenario("PostConv* request can be merged when convId matches") {
      val convId = ConvId()
      val postConvGen = arbPostConvSyncRequest.arbitrary.map(_.copy(convId = convId))
      val postConvNameGen = arbPostConvNameSyncRequest.arbitrary.map(_.copy(convId = convId))
      val postConvStateGen = arbPostConvStateSyncRequest.arbitrary.map(_.copy(convId = convId))
      val postTypingStateGen = arbPostTypingStateSyncRequest.arbitrary.map(_.copy(convId = convId))

      forAll(postConvGen, postConvGen) { (s1, s2) => s1 merge s2 shouldEqual Merged(s2) }
      forAll(postConvNameGen, postConvNameGen) { (s1, s2) => s1 merge s2 shouldEqual Merged(s2) }
      forAll(postTypingStateGen, postTypingStateGen) { (s1, s2) => s1 merge s2 shouldEqual Merged(s2) }
      forAll(postConvStateGen, postConvStateGen) { (s1, s2) =>
        s1 merge s2 shouldEqual Merged(PostConvState(convId, SyncRequest.mergeConvState(s1.state, s2.state)))
      }
    }

    scenario("PostConnectionStatus requests can be merged for the same user") {
      val userId = UserId()
      val gen = for {
        some <- arbitrary[ConnectionStatus] map (Some(_))
        none <- Gen.const(Option.empty[ConnectionStatus])
        status <- Gen.oneOf(some, none)
      } yield PostConnectionStatus(userId, status)

      forAll(gen, gen) { (u1, u2) =>
        u1 merge u1 shouldEqual Merged(u1)
        u1 merge u2 shouldEqual Merged(u2)
      }
    }

    scenario("SyncUser requests can be merged iff there are not too many users") {
      forAll(arbitrary[SyncUser], arbitrary[SyncUser]) { (u1, u2) =>
        if ((u1.users ++ u2.users).size <= UsersClient.IdsCountThreshold)
          u1 merge u2 shouldEqual Merged(SyncUser(u1.users ++ u2.users))
        else if ((u2.users -- u1.users).isEmpty)
          u1 merge u2 shouldEqual Merged(u1)
        else if (u1.users.intersect(u2.users).isEmpty)
          u1 merge u2 shouldEqual Unchanged
        else
          u1 merge u2 shouldEqual Updated(SyncUser(u2.users -- u1.users))
      }
    }

    scenario("SyncConversation requests can be merged iff there are not too many conversations") {
      forAll(arbitrary[SyncConversation], arbitrary[SyncConversation]) { (c1, c2) =>
        if ((c1.convs ++ c2.convs).size <= ConversationsClient.IdsCountThreshold)
          c1 merge c2 shouldEqual Merged(SyncConversation(c1.convs ++ c2.convs))
        else if ((c2.convs -- c1.convs).isEmpty)
          c1 merge c2 shouldEqual Merged(c1)
        else if (c1.convs.intersect(c2.convs).isEmpty)
          c1 merge c2 shouldEqual Unchanged
        else
          c1 merge c2 shouldEqual Updated(SyncConversation(c2.convs -- c1.convs))
      }
    }

    scenario("ConvMembers requests can be merged if they are joins for the same conversation") {
      val conv = ConvId()
      val c1 = PostConvJoin(conv, Set(UserId("1"), UserId("2")))
      val c2 = PostConvJoin(conv, Set(UserId("4"), UserId("1")))
      c1 merge c2 shouldEqual Merged(PostConvJoin(conv, Set(UserId("1"), UserId("2"), UserId("4"))))
      c2 merge c1 shouldEqual Merged(PostConvJoin(conv, Set(UserId("1"), UserId("2"), UserId("4"))))
    }
  }

  feature("Merging") {
    scenario("Merge user sync requests") {
      val merger = new SyncJobMerger(SyncCommand.SyncUser, new SyncStorage(storage, Nil))
      val req = merger.insert(SyncJob(SyncId(), SyncUser(Set(UserId()))))
      for (i <- 1 to 10) {
        merger.merge(SyncJob(SyncId(), SyncUser(Set(UserId())))) should beMatching({ case Merged(_) => true })
      }

      val rs = merger.jobs.values.toList
      rs should have size 1
      rs.head.request.cmd shouldEqual SyncCommand.SyncUser
      rs.head.request.asInstanceOf[SyncUser].users should have size 11
    }

    scenario("Merge post last read requests") {
      val convId = ConvId()
      val merger = new SyncJobMerger((SyncCommand.PostConvState, convId), new SyncStorage(storage, Nil))
      merger.merge(SyncJob(SyncId(), PostLastRead(convId, RemoteInstant.ofEpochMilli(1))))
      merger.merge(SyncJob(SyncId(), PostLastRead(convId, RemoteInstant.ofEpochMilli(2))))
      merger.merge(SyncJob(SyncId(), PostLastRead(convId, RemoteInstant.ofEpochMilli(3))))
      merger.jobs should have size 1
      merger.jobs.map(_._2.request) shouldEqual Seq(PostLastRead(convId, RemoteInstant.ofEpochMilli(3)))
    }

    scenario("Adding a new Users requests will not add users that are already handled by previous Users requests") {
      def lotsOfUsers(numLessThanThreshold: Int) = Seq.fill(UsersClient.IdsCountThreshold - numLessThanThreshold)(UserId()).toSet

      val syncId1 = SyncId()
      val syncId2 = SyncId()
      val syncId3 = SyncId()

      val merger = new SyncJobMerger(SyncCommand.SyncUser, new SyncStorage(storage, Nil))
      merger.merge(SyncJob(syncId1, SyncUser(lotsOfUsers(2) ++ Set(UserId("1"), UserId("2"))))) should beMatching({ case Updated(_) => true})
      merger.merge(SyncJob(syncId2, SyncUser(lotsOfUsers(2) ++ Set(UserId("3"), UserId("4"))))) should beMatching({ case Updated(_) => true})

      val timestamp = System.currentTimeMillis()
      merger.merge(SyncJob(syncId3, SyncUser(Set(UserId("1"), UserId("3"), UserId("4"), UserId("5"), UserId("6"))), timestamp = timestamp)) shouldEqual Updated(SyncJob(syncId3, SyncUser(Set(UserId("5"), UserId("6"))), timestamp = timestamp))
    }
  }

  class TrackingSyncStorage extends SyncStorage(storage, Nil) {
    var removed = Set.empty[SyncId]
    var added = Set.empty[SyncJob]

    override def remove(id: SyncId): Future[Any] = {
      removed += id
      super.remove(id)
    }

    override def add(job: SyncJob): SyncJob = {
      added += job
      super.add(job)
    }
  }
}
