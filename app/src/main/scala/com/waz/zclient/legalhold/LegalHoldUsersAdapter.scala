package com.waz.zclient.legalhold

import android.content.Context
import com.waz.model.UserId
import com.waz.service.SearchQuery
import com.waz.zclient.Injector
import com.waz.zclient.participants.ParticipantsAdapter
import com.waz.zclient.participants.ParticipantsAdapter.{AllParticipants, NoResultsInfo, ParticipantData}
import com.wire.signals.{EventContext, Signal}

class LegalHoldUsersAdapter(userIds: Signal[Set[UserId]], maxParticipants: Option[Int] = None)
                           (implicit context: Context, injector: Injector, eventContext: EventContext)
  extends ParticipantsAdapter(Signal.empty, maxParticipants, showPeopleOnly = false, true, None) {

  private var numUsers = 0

  override protected lazy val users: Signal[Vector[ParticipantData]] = for {
    selfId       <- selfId
    usersStorage <- usersStorage
    teamId       <- team
    userIds      <- userIds
    users        <- usersStorage.listSignal(userIds.toList)
    filter       <- filter
  } yield
    users.filter(user => filter.isEmpty || user.matchesQuery(SearchQuery(filter)))
      .map(user => ParticipantData(
        user,
        isGuest = user.isGuest(teamId),
        isAdmin = false, // unused
        isSelf = user.id == selfId
      )).sortBy(_.userData.name.str)

  override protected lazy val positions: Signal[List[Either[ParticipantData, Int]]] =
    for {
      users   <- users
      toShow   = maxParticipants.fold(users.toList)(max => users.toList.take(max))
      hasMore  = maxParticipants.fold(false)(users.size > _)
    } yield {
      numUsers = users.size
      if (users.isEmpty) List(Right(NoResultsInfo))
      else {
        val moreUsersRow = if (hasMore) List(Right(AllParticipants)) else Nil
        toShow.map(data => Left(data)) ::: moreUsersRow
      }
    }

  override protected def allParticipantsCount: Int = numUsers
}
