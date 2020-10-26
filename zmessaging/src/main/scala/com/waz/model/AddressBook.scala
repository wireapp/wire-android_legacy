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
import com.waz.db.Dao
import com.waz.model.AddressBook.ContactHashes
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{Json, JsonDecoder, JsonEncoder}
import org.json.JSONObject

import scala.collection.{GenSeq, GenSet, breakOut}


// TODO: Left for migrations compatibility. Can be deleted after all devices are migrated
// and we delete the history of old migrations
case class AddressBook(self: Seq[String], contacts: GenSeq[AddressBook.ContactHashes]) {

  def -(other: AddressBook): AddressBook = {
    val map : Map[ContactId, GenSet[String]] = other.contacts.map(c => c.id -> c.hashes)(breakOut)
    def otherContains(c: ContactHashes) = map.get(c.id).fold(false) { hs => c.hashes.forall(hs.contains) }

    AddressBook(self, contacts filterNot otherContains)
  }

  def isEmpty = contacts.isEmpty

  def nonEmpty = contacts.nonEmpty

  /** Removes duplicate cards, which will discard some card IDs. These need to be detected as duplicates when
    * matching the contacts against upload results later. */
  def withoutDuplicates: AddressBook = copy(contacts = contacts.groupBy(_.hashes).map(_._2.head)(breakOut))

  override def toString: String = s"AddressBook($self, … (${contacts.size} contact(s))"
}

object AddressBook {
  val Empty = AddressBook(Nil, Nil)
  case class ContactHashes(id: ContactId, hashes: GenSet[String])

  def load(implicit db: DB): AddressBook = AddressBook(Seq.empty, ContactHashesDao.list)

  def save(ab: AddressBook)(implicit db: DB): Unit = {
    ContactHashesDao.deleteAll
    ContactHashesDao.insertOrReplace(ab.contacts)
  }

  implicit object ContactHashesDao extends Dao[ContactHashes, ContactId] {
    val Id = id[ContactId]('_id, "PRIMARY KEY").apply(_.id)
    val Hashes = seq[String]('hashes, _.mkString(","), _.split(','))(_.hashes.to[Vector])

    override val idCol = Id
    override val table = Table("ContactHashes", Id, Hashes)

    override def apply(implicit cursor: DBCursor): ContactHashes = ContactHashes(Id, Hashes.toSet)
  }

  import com.waz.utils.JsonDecoder._

  implicit lazy val ContactDataEncoder: JsonEncoder[ContactHashes] = JsonEncoder.build[ContactHashes] { contact => o =>
    o.put("card_id", contact.id.str)
    o.put("contact", Json(contact.hashes.to[Vector]))
  }

  implicit lazy val ContactDataDecoder: JsonDecoder[ContactHashes] = new JsonDecoder[ContactHashes] {
    override def apply(implicit js: JSONObject): ContactHashes = ContactHashes(decodeId[ContactId]('card_id), decodeStringSeq('contact).toSet)
  }

  implicit lazy val AddressBookEncoder: JsonEncoder[AddressBook] = new JsonEncoder[AddressBook] {
    override def apply(addr: AddressBook): JSONObject = JsonEncoder { o =>
      o.put("self", Json(addr.self))
      o.put("cards", JsonEncoder.arr(addr.contacts))
    }
  }

  implicit lazy val AddressBookDecoder: JsonDecoder[AddressBook] = new JsonDecoder[AddressBook] {
    override def apply(implicit js: JSONObject): AddressBook = AddressBook(decodeStringSeq('self), decodeSeq[ContactHashes]('cards))
  }
}
