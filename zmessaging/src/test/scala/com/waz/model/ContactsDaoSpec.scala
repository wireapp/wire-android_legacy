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

import java.util.UUID.randomUUID

import com.waz.db._
import com.waz.model.Contact._
import com.waz.model.UserData.UserDataDao
import com.waz.service.SearchKey
import com.waz.utils.wrappers.{DB}
import org.robolectric.Robolectric
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

class ContactsDaoSpec extends FeatureSpec with Matchers with RobolectricTests {

  scenario("Loading contact information") (withDB { implicit db =>
    ContactsDao.insertOrReplace(meep)
    EmailAddressesDao.insertOrReplace((meep.id, EmailAddress("meep@moop.me")))
    EmailAddressesDao.insertOrReplace((meep.id, EmailAddress("meep.moop@gmail.not")))
    PhoneNumbersDao.insertOrReplace((meep.id, PhoneNumber("123")))
    PhoneNumbersDao.insertOrReplace((meep.id, PhoneNumber("456")))
    PhoneNumbersDao.insertOrReplace((meep.id, PhoneNumber("789")))

    ContactsDao.insertOrReplace(coyote)
    EmailAddressesDao.insertOrReplace((coyote.id, EmailAddress("coyote@wile.me")))
    EmailAddressesDao.insertOrReplace((coyote.id, EmailAddress("wile.e.coyote@gmail.not")))

    ContactsDao.list shouldEqual Vector(
      meep.copy(
        phoneNumbers = Set(PhoneNumber("123"), PhoneNumber("456"), PhoneNumber("789")),
        emailAddresses = Set(EmailAddress("meep@moop.me"), EmailAddress("meep.moop@gmail.not"))),
      coyote.copy(
        emailAddresses = Set(EmailAddress("coyote@wile.me"), EmailAddress("wile.e.coyote@gmail.not"))))
  })

  scenario("Saving and re-loading contact information") (withDB { implicit db =>
    ContactsDao.save(contacts)
    ContactsDao.list shouldEqual contacts

    ContactsOnWireDao.insertOrReplace((UserId("a"), meep.id))

    ContactsDao.list shouldEqual contacts
  })

  scenario("Matching users on save") (withDB { implicit db =>
    UserDataDao.insertOrIgnore(meepUser)
    ContactsDao.save(contacts)
    ContactsOnWireDao.list should contain.theSameElementsAs(Vector((meepUser.id, meep.id)))

    UserDataDao.insertOrIgnore(coyoteUser)
    ContactsDao.save(contacts)
    ContactsOnWireDao.list should contain.theSameElementsAs(Vector((meepUser.id, meep.id), (coyoteUser.id, coyote.id)))
  })

  lazy val meep = contact("Meep")
  lazy val coyote = contact("Wile E. Coyote")

  lazy val contacts = Vector(
    meep.copy(
      phoneNumbers = Set(PhoneNumber("123"), PhoneNumber("456"), PhoneNumber("789")),
      emailAddresses = Set(EmailAddress("meep@moop.me"), EmailAddress("meep.moop@gmail.not"))),
    coyote.copy(
      emailAddresses = Set(EmailAddress("coyote@wile.me"), EmailAddress("wile.e.coyote@gmail.not"))))

  lazy val meepUser = UserData(UserId("meepuser"), None, "Meep Moop", None, Some(PhoneNumber("123")), searchKey = SearchKey("Meep Moop"), handle = Some(Handle.random))
  lazy val coyoteUser = UserData(UserId("coyoteuser"), None, "Wile E. Coyote", Some(EmailAddress("wile.e.coyote@gmail.not")), None, searchKey = SearchKey("Wile E. Coyote"), handle = Some(Handle.random))

  def contact(name: String) = Contact(ContactId(), name, NameSource.StructuredName, name, SearchKey(name), Set.empty, Set.empty)

  def withDB(f: DB => Unit): Unit = {
    val dbHelper = new ZMessagingDB(Robolectric.application, s"dbName-$randomUUID")
    try f(dbHelper.getWritableDatabase) finally dbHelper.close
  }
}
