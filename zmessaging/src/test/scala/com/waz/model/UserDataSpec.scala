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

import com.waz.model.UserInfo.Service
import com.waz.specs.AndroidFreeSpec
import com.waz.zms.BuildConfig

class UserDataSpec extends AndroidFreeSpec {

  val referenceInfo = UserInfo(
    UserId(),
    if (BuildConfig.FEDERATION_USER_DISCOVERY) Domain("staging.zinfra.io") else Domain.Empty,
    Some(Name("Atticus")),
    Some(4),
    Some(EmailAddress("atticus@wire.com")),
    Some(PhoneNumber("+0099223344556677")),
    None, // ignoring pictures for now
    Some(TrackingId("123454fsdf")),
    false,
    Some(Handle.from("atticus")),
    Some(false),
    Some(Service(
      IntegrationId("f0f83af0-c7d3-42b7-ab8b-7fc137ee7173"),
      ProviderId("148668b1-e393-419d-b4ab-bf021e300262"))
    ),
    Some(TeamId("7d49b132-03b2-4124-bb18-9388577a6bb2")),
    Some(RemoteInstant.ofEpochSec(10000)),
    Some(SSOId("foo", "bar")),
    Some(ManagedBy("wire")),
    Some(Seq(UserField("Department", "Sales & Marketing"), UserField("Favorite color", "Blue")))
  )

  feature("Update from user info") {

    scenario("Creation transfers all data") {

      // WHEN
      val data = UserData(referenceInfo, false)

      // THEN
      data.id.shouldEqual(referenceInfo.id)
      if (BuildConfig.FEDERATION_USER_DISCOVERY) {
        data.domain.shouldEqual(referenceInfo.domain)
      } else {
        data.domain.shouldEqual(Domain.Empty)
      }
      data.name.shouldEqual(referenceInfo.name.get)
      data.accent.shouldEqual(referenceInfo.accentId.get)
      data.email.shouldEqual(referenceInfo.email)
      data.phone.shouldEqual(referenceInfo.phone)
      data.trackingId.shouldEqual(referenceInfo.trackingId)
      data.providerId.shouldEqual(referenceInfo.service.map(_.provider))
      data.integrationId.shouldEqual(referenceInfo.service.map(_.id))
      data.handle.shouldEqual(referenceInfo.handle)
      data.deleted.shouldEqual(referenceInfo.deleted)
      data.teamId.shouldEqual(referenceInfo.teamId)
      data.expiresAt.shouldEqual(referenceInfo.expiresAt)
      data.managedBy.shouldEqual(referenceInfo.managedBy)
      data.fields.shouldEqual(referenceInfo.fields.get)
    }

    scenario("Updating with empty UserInfo preserves data") {

      // GIVEN
      val oldData = UserData(referenceInfo, withSearchKey = false)
      val info = UserInfo(referenceInfo.id, Domain.Empty, referenceInfo.name)

      // WHEN
      val data = oldData.updated(info)

      // THEN
      data.id.shouldEqual(referenceInfo.id)
      data.domain.shouldEqual(referenceInfo.domain)
      data.name.shouldEqual(referenceInfo.name.get)
      data.accent.shouldEqual(referenceInfo.accentId.get)
      data.email.shouldEqual(referenceInfo.email)
      data.phone.shouldEqual(referenceInfo.phone)
      data.trackingId.shouldEqual(referenceInfo.trackingId)
      data.providerId.shouldEqual(referenceInfo.service.map(_.provider))
      data.integrationId.shouldEqual(referenceInfo.service.map(_.id))
      data.handle.shouldEqual(referenceInfo.handle)
      data.deleted.shouldEqual(referenceInfo.deleted)
      data.teamId.shouldEqual(referenceInfo.teamId)
      data.expiresAt.shouldEqual(referenceInfo.expiresAt)
      data.managedBy.shouldEqual(referenceInfo.managedBy)
      data.fields.shouldEqual(referenceInfo.fields.get)
    }
  }

  feature ("User isGuest/isInTeam") {
    val template1 = UserData.withName(UserId(), "user1")
    val template2 = UserData.withName(UserId(), "user2")

    scenario("The user IS NOT a guest if their team id is the same as ours - no federation") {
      if (!BuildConfig.FEDERATION_USER_DISCOVERY) {
        val ourTeamId = TeamId()
        val user = template1.copy(teamId = Some(ourTeamId))
        user.isGuest(Some(ourTeamId)) shouldBe false
      }
    }

    scenario("The user IS a guest if their team id is different from ours - no federation") {
      if (!BuildConfig.FEDERATION_USER_DISCOVERY) {
        val ourTeamId = TeamId()
        val otherTeamId = TeamId()
        val user = template1.copy(teamId = Some(otherTeamId))
        user.isGuest(Some(ourTeamId)) shouldBe true
      }
    }

    scenario("The user IS NOT a guest if their team id is the same as ours AND they are from the same domain - federation") {
      if (BuildConfig.FEDERATION_USER_DISCOVERY) {
        val ourTeamId = TeamId()
        val ourDomain = Domain("anta.wire.link")
        val user = template1.copy(teamId = Some(ourTeamId), domain = ourDomain)
        user.isGuest(Some(ourTeamId), ourDomain) shouldBe false
      }
    }

    scenario("The user IS a guest if their team id is different from ours - federation") {
      if (BuildConfig.FEDERATION_USER_DISCOVERY) {
        val ourTeamId = TeamId()
        val ourDomain = Domain("anta.wire.link")
        val otherTeamId = TeamId()
        val otherDomain = Domain("bella.wire.link")
        val user = template1.copy(teamId = Some(otherTeamId), domain = otherDomain)
        user.isGuest(Some(ourTeamId), ourDomain) shouldBe true
      }
    }

    scenario("The user IS a guest even if their team id is the same as ours but the domain is different - federation") {
      if (BuildConfig.FEDERATION_USER_DISCOVERY) {
        val ourTeamId = TeamId()
        val ourDomain = Domain("anta.wire.link")
        val otherDomain = Domain("bella.wire.link")
        val user = template1.copy(teamId = Some(ourTeamId), domain = otherDomain)
        user.isGuest(Some(ourTeamId), ourDomain) shouldBe true
      }
    }

    scenario("The user IS a guest if they're a private user") {
      val ourTeamId = TeamId()
      val user = template1.copy(teamId = None)
      user.isGuest(Some(ourTeamId)) shouldBe true
    }
  }
}
