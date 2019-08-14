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
package com.waz.db

import java.io.File
import java.util.Date

import com.google.protobuf.nano.MessageNano
import com.waz.db.Col.{blob, text}
import com.waz.model._
import com.waz.service.assets2.Codec
import com.waz.utils.wrappers.{DBContentValues, DBCursor, DBProgram}
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.threeten.bp.Instant

import scala.collection.generic._
import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

case class Col[A](name: String, sqlType: String, modifiers: String = "")(implicit translator: DbTranslator[A]) {
  def load(cursor: DBCursor, index: Int): A = translator.load(cursor, index)
  def save(value: A, values: DBContentValues): Unit = translator.save(value, name, values)
  def bind(value: A, index: Int, stmt: DBProgram): Unit = translator.bind(value, index, stmt)
  def sqlLiteral(value: A): String = translator.literal(value)
}

object Col {
  def opt[A](c: Col[A]): Col[Option[A]] = Col[Option[A]](c.name, c.sqlType, c.modifiers)(new DbTranslator[Option[A]] {
    override def load(cursor: DBCursor, index: Int): Option[A] = if (cursor.isNull(index)) None else Some(c.load(cursor, index))
    override def save(value: Option[A], name: String, values: DBContentValues): Unit = value match {
      case Some(v) => c.save(v, values)
      case None => values.putNull(name)
    }
    override def bind(value: Option[A], index: Int, stmt: DBProgram): Unit = value match {
      case Some(v) => c.bind(v, index, stmt)
      case None => stmt.bindNull(index)
    }
    override def literal(value: Option[A]): String = value.fold("NULL")(c.sqlLiteral)
  })
  def seq[A](name: Symbol, enc: Seq[A] => String, dec: String => Seq[A]): Col[Seq[A]] = Col[Seq[A]](name.name, "TEXT")(new DbTranslator[Seq[A]] {
    override def load(cursor: DBCursor, index: Int): Seq[A] = if (cursor.isNull(index)) Nil else dec(cursor.getString(index))
    override def save(value: Seq[A], name: String, values: DBContentValues): Unit = value match {
      case Nil => values.putNull(name)
      case _ => values.put(name, enc(value))
    }
    override def bind(value: Seq[A], index: Int, stmt: DBProgram): Unit = value match {
      case Nil => stmt.bindNull(index)
      case _ => stmt.bindString(index, enc(value))
    }
    override def literal(value: Seq[A]): String = enc(value)
  })
  def set[A](name: Symbol, enc: Set[A] => String, dec: String => Set[A]): Col[Set[A]] = Col[Set[A]](name.name, "TEXT")(new DbTranslator[Set[A]] {
    override def load(cursor: DBCursor, index: Int): Set[A] = if (cursor.isNull(index)) Set.empty[A] else dec(cursor.getString(index))
    override def save(value: Set[A], name: String, values: DBContentValues): Unit =
      if (value.isEmpty) values.putNull(name) else values.put(name, enc(value))
    override def bind(value: Set[A], index: Int, stmt: DBProgram): Unit =
      if (value.isEmpty) stmt.bindNull(index) else stmt.bindString(index, enc(value))
    override def literal(value: Set[A]): String = enc(value)
  })
  def json[A: JsonDecoder : JsonEncoder](name: Symbol) = Col[A](name.name, "TEXT")(DbTranslator.jsonTranslator[A]())
  def jsonArr[A, B[C] <: Traversable[C]](name: Symbol)(implicit enc: JsonEncoder[A], dec: JsonDecoder[A], cbf: CanBuild[A, B[A]]) = Col[B[A]](name.name, "TEXT")(DbTranslator.jsonArrTranslator[A, B]())
  def jsonArray[A, B[C] <: Traversable[C], D[E] <: B[E]](name: Symbol)(implicit enc: JsonEncoder[A], dec: JsonDecoder[A], cbf: CanBuild[A, D[A]]): Col[B[A]] = jsonArr[A, B](name)(enc, dec, cbf)

  def proto[A <: MessageNano](name: Symbol)(implicit dec: ProtoDecoder[A]) = Col[A](name.name, "BLOB")(DbTranslator.protoTranslator[A]())
  def protoSeq[A <: MessageNano, B[C] <: Traversable[C], D[E] <: B[E]](name: Symbol)(implicit dec: ProtoDecoder[A], cbf: CanBuild[A, D[A]]): Col[B[A]] = Col[B[A]](name.name, "BLOB")(DbTranslator.protoSeqTranslator[A, B]())

  def text(name: Symbol) = Col[String](name.name, "TEXT")
  def text(name: Symbol, modifiers: String) = Col[String](name.name, "TEXT", modifiers)
  def text[A](name: Symbol, enc: A => String, dec: String => A, modifiers: String = ""): Col[A] = Col[A](name.name, "TEXT")(new DbTranslator[A] {
    override def save(value: A, name: String, values: DBContentValues): Unit = values.put(name, enc(value))
    override def bind(value: A, index: Int, stmt: DBProgram): Unit = stmt.bindString(index, enc(value))
    override def load(cursor: DBCursor, index: Int): A = dec(cursor.getString(index))
    override def literal(value: A): String = enc(value)
  })
  def id[A: Id](name: Symbol, modifiers: String = "") = Col[A](name.name, "TEXT", modifiers)(DbTranslator.idTranslator())
  def uid(name: Symbol, modifiers: String = "") = Col[Uid](name.name, "TEXT", modifiers)
  def int(name: Symbol, modifiers: String = "") = Col[Int](name.name, "INTEGER", modifiers)
  def int[A](name: Symbol, enc: A => Int, dec: Int => A) = Col[A](name.name, "INTEGER")(new DbTranslator[A] {
    override def save(value: A, name: String, values: DBContentValues): Unit = values.put(name, Integer.valueOf(enc(value)))
    override def bind(value: A, index: Int, stmt: DBProgram): Unit = stmt.bindLong(index, enc(value))
    override def load(cursor: DBCursor, index: Int): A = dec(cursor.getInt(index))
    override def literal(value: A): String = enc(value).toString
  })
  def on[A, B](c: Col[B], enc: A => B, dec: B => A) = Col[A](c.name, c.sqlType, c.modifiers)(new DbTranslator[A] {
    override def save(value: A, name: String, values: DBContentValues): Unit = c.save(enc(value), values)
    override def bind(value: A, index: Int, stmt: DBProgram): Unit = c.bind(enc(value), index, stmt)
    override def load(cursor: DBCursor, index: Int): A = dec(c.load(cursor, index))
    override def literal(value: A): String = c.sqlLiteral(enc(value))
  })
  def phoneNumber(name: Symbol, modifiers: String = "") = Col[PhoneNumber](name.name, "TEXT", modifiers)
  def emailAddress(name: Symbol, modifiers: String = "") = Col[EmailAddress](name.name, "TEXT", modifiers)
  def handle(name: Symbol, modifiers: String = "") = Col[Handle](name.name, "TEXT", modifiers)
  def date(name: Symbol, modifiers: String = "") = Col[Date](name.name, "INTEGER", modifiers)
  def finiteDuration(name: Symbol, modifiers: String = "") = Col[FiniteDuration](name.name, "INTEGER", modifiers)
  def timestamp(name: Symbol, modifiers: String = "") = Col[Instant](name.name, "INTEGER", modifiers)
  def remoteTimestamp(name: Symbol, modifiers: String = "") = Col[RemoteInstant](name.name, "INTEGER", modifiers)
  def localTimestamp(name: Symbol, modifiers: String = "") = Col[LocalInstant](name.name, "INTEGER", modifiers)
  def long(name: Symbol, modifiers: String = "") = Col[Long](name.name, "INTEGER", modifiers)
  def long[A](name: Symbol, enc: A => Long, dec: Long => A) = Col[A](name.name, "INTEGER")(new DbTranslator[A] {
    override def save(value: A, name: String, values: DBContentValues): Unit = values.put(name, java.lang.Long.valueOf(enc(value)))
    override def bind(value: A, index: Int, stmt: DBProgram): Unit = stmt.bindLong(index, enc(value))
    override def load(cursor: DBCursor, index: Int): A = dec(cursor.getLong(index))
    override def literal(value: A): String = enc(value).toString
  })
  def double(name: Symbol, modifiers: String = "") = Col[Double](name.name, "REAL", modifiers)
  def float(name: Symbol, modifiers: String = "") = Col[Float](name.name, "REAL", modifiers)

  def bool(name: Symbol, modifiers: String = "") = Col[Boolean](name.name, "INTEGER", modifiers)

  def file(name: Symbol, modifiers: String = "") = Col[File](name.name, "TEXT", modifiers)
  def blob(name: Symbol, modifiers: String = ""): Col[Array[Byte]] = Col[Array[Byte]](name.name, "BLOB", modifiers)
}

trait ColumnBuilders[T] {

  def asInt[A](extractor: T => A)(name: Symbol)(implicit codec: Codec[A, Int]): ColBinder[A, T] =
    ColBinder(Col.int[A](name, codec.serialize, codec.deserialize), extractor)

  def asText[A](extractor: T => A)(name: Symbol, modifiers: String = "")(implicit codec: Codec[A, String]): ColBinder[A, T] =
    ColBinder(Col.text[A](name, codec.serialize, codec.deserialize, modifiers), extractor)

  def text(extractor: T => String)(name: Symbol, modifiers: String = ""): ColBinder[String, T] =
    ColBinder(Col.text(name, modifiers), extractor)

  def textOpt(extractor: T => Option[String])(name: Symbol, modifiers: String = ""): ColBinder[Option[String], T] =
    ColBinder(Col.opt(Col.text(name, modifiers)), extractor)

  def asTextOpt[A](extractor: T => Option[A])(name: Symbol, modifiers: String = "")(implicit codec: Codec[A, String]): ColBinder[Option[A], T] =
    ColBinder(Col.opt(Col.text[A](name, codec.serialize, codec.deserialize, modifiers)), extractor)

  def long(extractor: T => Long)(name: Symbol, modifiers: String = ""): ColBinder[Long, T] =
    ColBinder(Col.long(name, modifiers), extractor)

  def bool(extractor: T => Boolean)(name: Symbol, modifiers: String = ""): ColBinder[Boolean, T] =
    ColBinder(Col.bool(name, modifiers), extractor)

  def asBlob[A](extractor: T => A)(name: Symbol, modifiers: String = "")(implicit codec: Codec[A, Array[Byte]]): ColBinder[A, T] =
    ColBinder(Col(name.name, "BLOB")(new DbTranslator[A] {
      override def save(value: A, name: String, values: DBContentValues): Unit = values.put(name, codec.serialize(value))
      override def bind(value: A, index: Int, stmt: DBProgram): Unit = stmt.bindBlob(index, codec.serialize(value))
      override def load(cursor: DBCursor, index: Int): A = codec.deserialize(cursor.getBlob(index))
      override def literal(value: A): String = codec.serialize(value).mkString(",")
    }), extractor)

}
