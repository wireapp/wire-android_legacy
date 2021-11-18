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
package com.waz.service

import com.waz.api.ConnectionStatus
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.conversation.{ConversationsService, ConversationsUiService}
import com.waz.service.teams.TeamsService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.testutils.TestUserPreferences
import com.waz.utils.Managed
import com.wire.signals.{EventStream, Signal, SourceSignal}
import com.waz.utils.wrappers.DB
import com.waz.zms.BuildConfig

import scala.collection.breakOut
import scala.collection.generic.CanBuild
import scala.concurrent.Future

class UserSearchServiceSpec extends AndroidFreeSpec with DerivedLogTag {

  val emptyTeamId       = Option.empty[TeamId]
  val teamId            = Option(TeamId("59bbc94c-2618-491a-8dba-cf6f94c65873"))
  val externalPermissions: Long = 1025
  val memberPermissions: Long = 1587
  val adminPermissions: Long = 5951
  val currentDomain: Domain = Domain("staging.zinfra.io")

  val userService       = mock[UserService]
  val usersStorage      = mock[UsersStorage]
  val membersStorage    = mock[MembersStorage]
  val teamsService      = mock[TeamsService]
  val sync              = mock[SyncServiceHandle]
  val messagesStorage   = mock[MessagesStorage]
  val convsUi           = mock[ConversationsUiService]
  val convsStorage      = mock[ConversationStorage]
  val convs             = mock[ConversationsService]
  val timeouts          = new Timeouts
  val userPrefs         = new TestUserPreferences

  private def userWithName(idSymbol: Symbol, name: String): UserData =
    if (BuildConfig.FEDERATION_USER_DISCOVERY) {
      UserData.withName(id(idSymbol), name).copy(domain = currentDomain)
    } else {
      UserData.withName(id(idSymbol), name)
    }

  lazy val users = Map(
    id('me) -> userWithName('me, "A user"),
    id('a) -> userWithName('a, "other user 1"),
    id('b) -> userWithName('b, "other user 2"),
    id('c) -> userWithName('c, "some name"),
    id('d) -> userWithName('d, "related user 1").copy(relation = Relation.Second), // TODO: relation does not exists anymore, can be removed!
    id('e) -> userWithName('e, "related user 2").copy(relation = Relation.Second),
    id('f) -> userWithName('f, "other related").copy(relation = Relation.Third),
    id('g) -> userWithName('g, "friend user 1").copy(connection = ConnectionStatus.ACCEPTED),
    id('h) -> userWithName('h, "friend user 2").copy(connection = ConnectionStatus.ACCEPTED),
    id('i) -> userWithName('i, "some other friend").copy(connection = ConnectionStatus.ACCEPTED),
    id('j) -> userWithName('j, "meep moop").copy(email = Some(EmailAddress("moop@meep.me"))),
    id('k) -> userWithName('k, "unconnected user").copy(connection = ConnectionStatus.UNCONNECTED),
    id('l) -> userWithName('l, "Bjorn-Rodrigo Smith"),
    id('m) -> userWithName('m, "John Smith"),
    id('n) -> userWithName('n, "Jason-John Mercier"),
    id('o) -> userWithName('o, "Captain Crunch").copy(handle = Some(Handle.from("john"))),
    id('p) -> userWithName('p, "Peter Pan").copy(handle = Some(Handle.from("john"))),
    id('q) -> userWithName('q, "James gjohnjones"),
    id('r) -> userWithName('r, "Liv Boeree").copy(handle = Some(Handle.from("testjohntest"))),
    id('s) -> userWithName('s, "blah").copy(handle = Some(Handle.from("mores"))),
    id('t) -> userWithName('t, "test handle").copy(handle = Some(Handle.from("smoresare"))),
    id('u) -> userWithName('u, "Wireless").copy(expiresAt = Some(RemoteInstant.ofEpochMilli(12345L))),
    id('v) -> userWithName('v, "Wireful"),
    id('z) -> userWithName('z, "Francois francois"),
    id('pp1) -> userWithName('pp1, "External 1").copy(
      permissions = (externalPermissions, externalPermissions),
      teamId = teamId,
      handle = Some(Handle.from("pp1")),
      createdBy = Some(id('aa1))
    ),
    id('pp2) -> userWithName('pp2, "External 2").copy(
      permissions = (externalPermissions, externalPermissions),
      teamId = teamId,
      handle = Some(Handle.from("pp2")),
      createdBy = Some(id('aa2))
    ),
    id('pp3) -> userWithName('pp3, "External 3").copy(
      permissions = (externalPermissions, externalPermissions),
      teamId = teamId,
      handle = Some(Handle.from("pp3"))
    ),
    id('mm1) -> userWithName('mm1, "Member 1").copy(
      permissions = (memberPermissions, memberPermissions),
      teamId = teamId,
      handle = Some(Handle.from("mm1")),
      createdBy = Some(id('aa1))
    ),
    id('mm2) -> userWithName('mm2, "Member 2").copy(
      permissions = (memberPermissions, memberPermissions),
      teamId = teamId,
      handle = Some(Handle.from("mm2")),
      createdBy = Some(id('aa1))
    ),
    id('mm3) -> userWithName('mm3, "Member 3").copy(
      permissions = (memberPermissions, memberPermissions),
      teamId = teamId,
      handle = Some(Handle.from("mm3")),
      createdBy = Some(id('aa1))
    ),
    id('aa1) -> userWithName('aa1, "Admin 1").copy(
      permissions = (adminPermissions, adminPermissions),
      teamId = teamId,
      handle = Some(Handle.from("aa1"))
    ),
    id('aa2) -> userWithName('aa2, "Admin 2").copy(
      permissions = (adminPermissions, adminPermissions),
      teamId = teamId,
      handle = Some(Handle.from("aa2"))
    ),
    id('me1) -> userWithName('me1, "Team Member With Email").copy(
      email = Some(EmailAddress("a_member@wire.com")),
      teamId = teamId
    ),
    id('pe1) -> userWithName('pe1, "Person With Email").copy(
      email = Some(EmailAddress("a_person@wire.com"))
    )
  )

  // Mock search in team
  (teamsService.searchTeamMembers _).expects(*).anyNumberOfTimes().onCall { query: SearchQuery =>
    Signal.const(
      users
        .filter(u => u._2.teamId == teamId)
        .filter(_._2.matchesQuery(query))
        .values.toSet
    )
  }

  scenario("search conversation with token starting with query") {

    val convMembers = Set(id('l), id('b))

    (membersStorage.activeMembers _).expects(*).anyNumberOfTimes().returning(Signal.const(convMembers))
    (usersStorage.listSignal _).expects(*).once().returning(Signal.const(convMembers.map(users).toVector))

    val res = getService(false, id('me)).mentionsSearchUsersInConversation(ConvId("123"),"rod")
    result(res.filter(_.nonEmpty).head)
  }

  scenario("search conversation with name starting with query") {

    val convMembers = Set(id('l), id('b))

    (membersStorage.activeMembers _).expects(*).anyNumberOfTimes().returning(Signal.const(convMembers))
    (usersStorage.listSignal _).expects(*).once().returning(Signal.const(convMembers.map(users).toVector))

    val res = getService(false, id('me)).mentionsSearchUsersInConversation(ConvId("123"),"bjo")
    result(res.filter(_.nonEmpty).head)
  }

  scenario("search conversation with name containing query") {

    val convMembers = Set(id('l), id('m))

    (membersStorage.activeMembers _).expects(*).anyNumberOfTimes().returning(Signal.const(convMembers))
    (usersStorage.listSignal _).expects(*).once().returning(Signal.const(convMembers.map(users).toVector))

    val res = getService(false, id('me)).mentionsSearchUsersInConversation(ConvId("123"),"rn")
    result(res.filter{u => u.size == 1}.head)
  }

  scenario("search conversation with handle containing query") {

    val convMembers = Set(id('s), id('t))

    (membersStorage.activeMembers _).expects(*).anyNumberOfTimes().returning(Signal.const(convMembers))
    (usersStorage.listSignal _).expects(*).once().returning(Signal.const(convMembers.map(users).toVector))

    val res = getService(false, id('me)).mentionsSearchUsersInConversation(ConvId("123"),"mores")
    result(res.filter(_.size == 2).head)
  }

  scenario("search conversation handle beginning with query") {

    val convMembers = Set(id('s), id('t))

    (membersStorage.activeMembers _).expects(*).anyNumberOfTimes().returning(Signal.const(convMembers))
    (usersStorage.listSignal _).expects(*).once().returning(Signal.const(convMembers.map(users).toVector))

    val res = getService(false, id('me)).mentionsSearchUsersInConversation(ConvId("123"),"smores")
    result(res.filter(_.size == 1).head)
  }

  scenario("search conversation people ordering") {

    val convMembers = Set(id('q), id('r),id('p), id('n), id('m), id('o))
    val correctOrder = IndexedSeq(ud('o), ud('q), ud('n), ud('m), ud('r), ud('p))
    // sorting is by name: ('o,Captain Crunch),('q,James gjohnjones),('n,Jason-John Mercier),('m,John Smith),('r,Liv Boeree),('p,Peter Pan)

    (membersStorage.activeMembers _).expects(*).anyNumberOfTimes().returning(Signal.const(convMembers))
    (usersStorage.listSignal _).expects(*).once().returning(Signal.const(convMembers.map(users).toVector))

    val res = getService(false, id('me)).mentionsSearchUsersInConversation(ConvId("123"),"john")

    result(res.filter(_.equals(correctOrder)).head)
  }

  def id(s: Symbol) = UserId(s.toString)
  def ids(s: Symbol*) = s.map(id)(breakOut).toSet
  def ud(s: Symbol) = users(id(s))

  feature("Search by searchState") {
    scenario("search for top people"){
      val expected = ids('g, 'h, 'i)

      (convs.onlyFake1To1ConvUsers _).expects().anyNumberOfTimes().returning(Signal.const(Seq.empty[UserData]))

      (usersStorage.find(_: UserData => Boolean, _: DB => Managed[TraversableOnce[UserData]], _: UserData => UserData)(_: CanBuild[UserData, Vector[UserData]]))
        .expects(*, *, *, *).once().returning(Future.successful(expected.map(users).toVector))

      (userService.acceptedOrBlockedUsers _).expects().returns(Signal.const(Map.empty[UserId, UserData]))
      (userService.selfUser _).expects().anyNumberOfTimes().returning(Signal.const(users(id('me))))
      (messagesStorage.countLaterThan _).expects(*, *).repeated(3).returning(Future.successful(1L))
      (usersStorage.onAdded _).expects().anyNumberOfTimes().returning(EventStream[Seq[UserData]]())
      (usersStorage.onUpdated _).expects().anyNumberOfTimes().returning(EventStream[Seq[(UserData, UserData)]]())
      (usersStorage.onDeleted _).expects().anyNumberOfTimes().returning(EventStream[Seq[UserId]]())

      val res = getService(false, id('me)).search("").map(_.top.map(_.id).toSet)

      result(res.filter(_ == expected).head)
    }

    scenario("search for local results"){
      val expected = ids('g, 'h)
      val query = SearchQuery("fr")

      val querySignal = SourceSignal[Option[IndexedSeq[UserData]]]()
      val queryResults = IndexedSeq.empty[UserData]

      (convs.onlyFake1To1ConvUsers _).expects().anyNumberOfTimes().returning(Signal.const(Seq.empty[UserData]))

      (userService.acceptedOrBlockedUsers _).expects().once().returning(Signal.const(expected.map(key => key -> users(key)).toMap))
      (userService.selfUser _).expects().anyNumberOfTimes().returning(Signal.const(users(id('me))))

      (convsStorage.findGroupConversations _).expects(*, *, *, *).returns(Future.successful(IndexedSeq.empty[ConversationData]))

      if (BuildConfig.FEDERATION_USER_DISCOVERY) {
        (sync.syncSearchQuery _).expects(query.withDomain(currentDomain.str)).once().onCall { _: SearchQuery =>
          Future.successful[SyncId] {
            querySignal ! Some(queryResults)
            result(querySignal.filter(_.contains(queryResults)).head)
            SyncId()
          }
        }
      } else {
        (sync.syncSearchQuery _).expects(query).once().onCall { _: SearchQuery =>
          Future.successful[SyncId] {
            querySignal ! Some(queryResults)
            result(querySignal.filter(_.contains(queryResults)).head)
            SyncId()
          }
        }
      }

      val res = getService(false, id('me)).search("fr").map(_.local.map(_.id).toSet)

      result(res.filter(_ == expected).head)
    }

    scenario("search for local results with a fake 1-to-1 conversation"){
      val expected = ids('g, 'h)
      val fake1To1Id = id('z)
      val query = SearchQuery("fr")

      val querySignal = SourceSignal[Option[IndexedSeq[UserData]]]()
      val queryResults = IndexedSeq.empty[UserData]

      (convs.onlyFake1To1ConvUsers _).expects().anyNumberOfTimes().returning(Signal.const(Seq(users(fake1To1Id))))

      (userService.acceptedOrBlockedUsers _).expects().once().returning(Signal.const(expected.map(key => key -> users(key)).toMap))
      (userService.selfUser _).expects().anyNumberOfTimes().returning(Signal.const(users(id('me))))

      (convsStorage.findGroupConversations _).expects(*, *, *, *).returns(Future.successful(IndexedSeq.empty[ConversationData]))

      if (BuildConfig.FEDERATION_USER_DISCOVERY) {
        (sync.syncSearchQuery _).expects(query.withDomain(currentDomain.str)).once().onCall { _: SearchQuery =>
          Future.successful[SyncId] {
            querySignal ! Some(queryResults)
            result(querySignal.filter(_.contains(queryResults)).head)
            SyncId()
          }
        }
      } else {
        (sync.syncSearchQuery _).expects(query).once().onCall { _: SearchQuery =>
          Future.successful[SyncId] {
            querySignal ! Some(queryResults)
            result(querySignal.filter(_.contains(queryResults)).head)
            SyncId()
          }
        }
      }

      val res = getService(false, id('me)).search("fr").map(_.local.map(_.id).toSet)

      result(res.filter(_ == (expected ++ Set(fake1To1Id))).head)
    }
  }

  feature("search inside the team") {

    /**
    * Helper class to keep track of mocked query
      */
    case class PreparedSearch(inTeam: Boolean, selfId: UserId, query: String) {

      def perform() = {
        val service = getService(this.inTeam, this.selfId)
        service.search(query).map(_.local.map(_.id).toSet).head
      }
    }

    /**
    * Will mock all services, instantiate a UserSearchService to test, and store the query to expect
      */
    def prepareTestSearch(query: String,
                          selfId: UserId,
                          conversationMembers: Set[UserId] = Set(),
                          connectedUsers: Set[UserId] = Set()): PreparedSearch = {
      val convId = ConvId("e7969e91-366d-4ec5-9d85-4e8a4f9d53e6")

      val querySignal = SourceSignal[Option[Vector[UserId]]]()
      val queryResults = Vector.empty[UserId]

      (convs.onlyFake1To1ConvUsers _).expects().anyNumberOfTimes().returning(Signal.const(Seq.empty[UserData]))
      (usersStorage.get _).stubs(*).onCall { id: UserId =>
        Future.successful(users.get(id))
      }
      (usersStorage.find(_: UserData => Boolean, _: DB => Managed[TraversableOnce[UserData]], _: UserData => UserData)(_: CanBuild[UserData, Vector[UserData]]))
        .stubs(*, *, *, *).returning(Future.successful(Vector.empty[UserData]))
      (userService.acceptedOrBlockedUsers _).stubs().returning(Signal.const(users.filterKeys(connectedUsers.contains)))
      (userService.getSelfUser _).stubs().onCall(_ => Future.successful(users.get(selfId)))
      (userService.selfUser _).expects().anyNumberOfTimes().returning(Signal.const(users(selfId)))

      (convsStorage.findGroupConversations _).stubs(*, *, *, *).returns(Future.successful(IndexedSeq.empty[ConversationData]))

      (membersStorage.getByUsers _).stubs(*).onCall { ids: Set[UserId] =>
        Future.successful(ids.intersect(conversationMembers).map(ConversationMemberData(_, convId, ConversationRole.AdminRole.label)).toIndexedSeq)
      }

      (sync.syncSearchQuery _).stubs(*).onCall { _: SearchQuery =>
        Future.successful[SyncId] {
          querySignal ! Some(queryResults)
          result(querySignal.filter(_.contains(queryResults)).head)
          SyncId()
        }
      }

      (usersStorage.onAdded _).expects().anyNumberOfTimes().returning(EventStream[Seq[UserData]]())
      (usersStorage.onUpdated _).expects().anyNumberOfTimes().returning(EventStream[Seq[(UserData, UserData)]]())
      (usersStorage.onDeleted _).expects().anyNumberOfTimes().returning(EventStream[Seq[UserId]]())

      // Set up user permissions according to role
      val user = users(selfId)
      userPrefs.setValue(UserPreferences.SelfPermissions, user.permissions._1)

      PreparedSearch(true, selfId, query)
    }

//    scenario("as a member, search externals that are not in a conversation with me") {
//      // GIVEN
//      val preparedSearch = prepareTestSearch(
//        query = "external",
//        selfId = id('mm1),
//        conversationMembers = ids('a, 'mm1)
//      )
//
//      // WHEN
//      val res = result(preparedSearch.perform())
//
//      // THEN
//      res shouldBe ids()
//    }

//    scenario("as a member, search externals that are in a conversation with me") {
//
//      // GIVEN
//      val preparedSearch = prepareTestSearch(
//        query = "external",
//        selfId = id('mm1),
//        conversationMembers = ids('pp1, 'k, 'mm1)
//      )
//
//      // WHEN
//      val res = result(preparedSearch.perform())
//
//      // THEN
//      res shouldBe ids('pp1)
//    }

    scenario("as a member, search externals that are not in a conversation with me by exact handle") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "pp1",
        selfId = id('mm1),
        conversationMembers = ids('a, 'k, 'mm1)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('pp1)
    }

    scenario("as a member, search team members whether they are in a conversation with me or not") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Member",
        selfId = id('mm3),
        conversationMembers = ids('mm2, 'pp1)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('mm1, 'mm2, 'me1)
    }

    scenario("as a member, search connected guests whether they are in a conversation with me or not") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "related",
        selfId = id('mm1),
        conversationMembers = ids('mm2, 'pp1, 'e),
        connectedUsers = ids('d, 'e)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('d, 'e)
    }

    scenario("as a member, search not connected guests") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "related",
        selfId = id('mm1),
        conversationMembers = ids('mm2, 'pp1, 'e)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids()
    }

    scenario("as a external, search team members that are not in a conversation with me") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Member",
        selfId = id('pp1),
        conversationMembers = ids('pp1, 'k)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids()
    }

    scenario("as a external, show no team members") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "",
        selfId = id('pp3),
        conversationMembers = ids('pp3, 'k)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids()
    }

    scenario("as a external, search team members that are in a conversation with me") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Member",
        selfId = id('pp1),
        conversationMembers = ids('mm1, 'k)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('mm1)
    }

    scenario("as a external, search externals that are in a conversation with me") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "external",
        selfId = id('pp2),
        conversationMembers = ids('pp1, 'pp2)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('pp1)
    }

    scenario("as a external, search externals that are not in a conversation with me") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "external",
        selfId = id('pp1),
        conversationMembers = ids('mm1)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids()
    }

    scenario("as a external, search connected guests whether they are in a conversation with me or not") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "related",
        selfId = id('pp1),
        conversationMembers = ids('mm2, 'pp1, 'e),
        connectedUsers = ids('d, 'e)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('e)
    }

    scenario("as a external, search not connected guests") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "related",
        selfId = id('pp1),
        conversationMembers = ids('mm2, 'pp1, 'e)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids()
    }

    scenario("as an admin, search the externals that I invited") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "external",
        selfId = id('aa1)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('pp1)

    }

    scenario("as an admin, see the externals that I invited") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "",
        selfId = id('aa1)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids(
        'aa2, 'mm1, 'mm2, 'mm3, 'me1, // all non-External team members
        'pp1 // External that I invited
      )

    }

    scenario("as a external, see the admin that invited me") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "",
        selfId = id('pp2)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('aa2)

    }

    scenario("do not return wireless guests as results") {
      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Wire",
        selfId = id('aa1),
        connectedUsers = ids('u, 'v)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('v) // the user 'u also has the username starting with Wire, but is wireless
    }
  }

  def getService(inTeam: Boolean, selfId: UserId): UserSearchService =
    new UserSearchServiceImpl(
      selfId,
      if (inTeam) teamId else emptyTeamId,
      currentDomain,
      userService,
      usersStorage,
      teamsService,
      membersStorage,
      timeouts,
      sync,
      messagesStorage,
      convsStorage,
      convsUi,
      convs,
      userPrefs
    )

}
