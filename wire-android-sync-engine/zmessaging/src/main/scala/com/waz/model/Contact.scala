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

import com.waz.db.Col._
import com.waz.db._
import com.waz.model.UserData.{ConnectionStatus, UserDataDao}
import com.waz.service.SearchKey
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{NameParts, RichOption}

import scala.collection.{GenSet, breakOut, mutable}
import scala.collection.generic.CanBuild
import scala.language.higherKinds

// TODO: Left for migrations compatibility. Can be deleted after all devices are migrated
// and we delete the history of old migrations
case class Contact(id: ContactId, name: String, nameSource: NameSource, sortKey: String, searchKey: SearchKey, phoneNumbers: GenSet[PhoneNumber], emailAddresses: GenSet[EmailAddress]) {
  lazy val initials = NameParts.parseFrom(name).initials
  def hasProperName = name.nonEmpty && nameSource != NameSource.Other
}

sealed abstract class NameSource(val serial: Int)
object NameSource {
  case object StructuredName extends NameSource(2)
  case object Nickname extends NameSource(1)
  case object Other extends NameSource(0)

  def bySerial(serial: Int): NameSource = serial match {
    case StructuredName.serial => StructuredName
    case Nickname.serial => Nickname
    case _ => Other
  }
}

object Contact extends ((ContactId, String, NameSource, String, SearchKey, GenSet[PhoneNumber], GenSet[EmailAddress]) => Contact) {

  implicit object ContactsDao extends Dao[Contact, ContactId] {
    val Id = id[ContactId]('_id, "PRIMARY KEY").apply(_.id)
    val Name = text('name)(_.name)
    val Source = int[NameSource]('name_source, _.serial, NameSource.bySerial)(_.nameSource)
    val Sorting = text('sort_key)(_.sortKey)
    val Searching = text[SearchKey]('search_key, _.asciiRepresentation, SearchKey.unsafeRestore)(_.searchKey)

    override val idCol = Id
    override val table = Table("Contacts", Id, Name, Source, Sorting, Searching)

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS Contacts_sorting on Contacts ( ${Sorting.name} )")
    }

    def save(contacts: Iterable[Contact])(implicit db: DB): Unit = inTransaction {
      PhoneNumbersDao.deleteAll
      EmailAddressesDao.deleteAll
      ContactsDao.deleteAll
      contacts foreach { contact =>
        ContactsDao.insertOrIgnore(contact)
        contact.phoneNumbers foreach (p => PhoneNumbersDao.insertOrIgnore((contact.id, p)))
        contact.emailAddresses foreach (e => EmailAddressesDao.insertOrIgnore((contact.id, e)))
      }
      ContactsOnWireDao.deleteNonExisting
      ContactsOnWireDao.fillFromUsers
    }

    import com.waz.model.Contact.{EmailAddressesDao => E, PhoneNumbersDao => P}

    override def listCursor(implicit db: DB): DBCursor = listCursorWithLimit(None)

    def listCursorWithLimit(limit: Option[Int])(implicit db: DB): DBCursor =
      db.rawQuery(
      s"""   SELECT ${table.columns.map(_.name).mkString("c.", ", c.", ",")}
         |          (SELECT group_concat(${P.Phone.name}) FROM ${P.table.name} WHERE ${P.Contact.name} = c.${Id.name}) AS phones,
         |          (SELECT group_concat(${E.Email.name}) FROM ${E.table.name} WHERE ${E.Contact.name} = c.${Id.name}) AS emails
         |     FROM ${ContactsDao.table.name} c
         | ${limit.fold2("", l => s"ORDER BY c.${Sorting.name} COLLATE LOCALIZED ASC LIMIT $l")}
       """.stripMargin)

    override def apply(implicit c: DBCursor): Contact = Contact(Id, Name, Source, Sorting, Searching, split[PhoneNumber, mutable.HashSet]('phones, PhoneNumber), split[EmailAddress, mutable.HashSet]('emails, EmailAddress))

    private def split[A, B[_]](n: Symbol, f: String => A)(implicit c: DBCursor, cb: CanBuild[A, B[A]]): B[A] = {
      val value = c.getString(c.getColumnIndex(n.name))
      if (value ne null) value.split(",").map(f)(breakOut) else cb.apply().result
    }
  }

  object PhoneNumbersDao extends BaseDao[(ContactId, PhoneNumber)] {
    val Contact = id[ContactId]('contact).apply(_._1)
    val Phone = text[PhoneNumber]('phone_number, _.str, PhoneNumber).apply(_._2)

    override val table = Table("PhoneNumbers", Contact, Phone)
    override def apply(implicit cursor: DBCursor): (ContactId, PhoneNumber) = (Contact, Phone)

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS PhoneNumbers_contact on PhoneNumbers ( ${Contact.name} )")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS PhoneNumbers_phone on PhoneNumbers ( ${Phone.name} )")
    }

    def findBy(p: PhoneNumber)(implicit db: DB): Set[ContactId] = iteratingWithReader(contactIdReader)(find(Phone, p)).acquire(_.toSet)
    private val contactIdReader = readerFor(Contact)
  }

  object EmailAddressesDao extends BaseDao[(ContactId, EmailAddress)] {
    val Contact = id[ContactId]('contact).apply(_._1)
    val Email = text[EmailAddress]('email_address, _.str, EmailAddress).apply(_._2)

    override val table = Table("EmailAddresses", Contact, Email)
    override def apply(implicit cursor: DBCursor): (ContactId, EmailAddress) = (Contact, Email)

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS EmailAddresses_contact on EmailAddresses ( ${Contact.name} )")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS EmailAddresses_email on EmailAddresses ( ${Email.name} )")
    }

    def findBy(e: EmailAddress)(implicit db: DB): Set[ContactId] = iteratingWithReader(contactIdReader)(find(Email, e)).acquire(_.toSet)
    private val contactIdReader = readerFor(Contact)
  }

  object ContactsOnWireDao extends Dao2[(UserId, ContactId), UserId, ContactId] {
    val User = id[UserId]('user).apply(_._1)
    val Contact = id[ContactId]('contact).apply(_._2)

    override val idCol = (User, Contact)
    override val table = Table("ContactsOnWire", User, Contact)

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS ContactsOnWire_contact on ContactsOnWire ( ${Contact.name} )")
    }

    import UserData.{UserDataDao => U}
    import com.waz.model.Contact.{ContactsDao => C, EmailAddressesDao => E, PhoneNumbersDao => P}

    def deleteNonExisting(implicit db: DB): Unit =
      db.execSQL(s"DELETE FROM ${table.name} WHERE NOT EXISTS ( SELECT c.${C.Id.name} FROM ${C.table.name} c WHERE c.${C.Id.name} = ${Contact.name} )")

    def fillFromUsers(implicit db: DB): Unit = db.execSQL(
      s"""INSERT OR IGNORE INTO ${table.name} (${User.name}, ${Contact.name})
         |  SELECT u.${U.Id.name} as user, e.${E.Contact.name} as contact
         |    FROM ${U.table.name} u, ${E.table.name} e
         |   WHERE u.${U.Email.name} = e.${E.Email.name}
         |  UNION
         |  SELECT u.${U.Id.name} as user, p.${P.Contact.name} as contact
         |    FROM ${U.table.name} u, ${P.table.name} p
         |   WHERE u.${U.Phone.name} = ${P.Phone.name}""".stripMargin)

    lazy val Connected = s"IN (${Vector(ConnectionStatus.Self, ConnectionStatus.Accepted, ConnectionStatus.Blocked).map(UserDataDao.Conn.col.sqlLiteral).mkString(", ")})"
    override def apply(implicit cursor: DBCursor): (UserId, ContactId) = (User, Contact)
  }
}
