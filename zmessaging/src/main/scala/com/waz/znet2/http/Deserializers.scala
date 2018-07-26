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
package com.waz.znet2.http

import java.io.{File, FileOutputStream}

import com.waz.utils.{IoUtils, JsonDecoder, returning}
import io.circe.{Decoder, Json}
import org.json.{JSONArray, JSONObject}

trait ResponseDeserializer[T] {
  def deserialize(response: Response[Body]): T
  def map[B](f: T => B): ResponseDeserializer[B] =
    ResponseDeserializer.create(body => f(this.deserialize(body)))
}

object ResponseDeserializer {

  def apply[T](implicit rd: ResponseDeserializer[T]): ResponseDeserializer[T] = rd

  def create[T](f: Response[Body] => T): ResponseDeserializer[T] = new ResponseDeserializer[T] {
    override def deserialize(response: Response[Body]): T = f(response)
  }

}

trait BodyDeserializer[T] {
  def deserialize(body: Body): T
  def map[B](f: T => B): BodyDeserializer[B] =
    BodyDeserializer.create(body => f(this.deserialize(body)))
}

object BodyDeserializer {

  def apply[T](implicit bd: BodyDeserializer[T]): BodyDeserializer[T] = bd

  def create[T](f: Body => T): BodyDeserializer[T] = new BodyDeserializer[T] {
    override def deserialize(body: Body): T = f(body)
  }

}

trait RawBodyDeserializer[T] {
  def deserialize(body: RawBody): T
  def map[B](f: T => B): RawBodyDeserializer[B] =
    RawBodyDeserializer.create(body => f(this.deserialize(body)))
}

object RawBodyDeserializer {

  def apply[T](implicit rbd: RawBodyDeserializer[T]): RawBodyDeserializer[T] = rbd

  def create[T](f: RawBody => T): RawBodyDeserializer[T] = new RawBodyDeserializer[T] {
    override def deserialize(body: RawBody): T = f(body)
  }

  def createFileRawBodyDeserializer(targetFile: => File): RawBodyDeserializer[File] =
    RawBodyDeserializer.create { body =>
      returning(targetFile) { file =>
        IoUtils.copy(body.data(), new FileOutputStream(file))
      }
    }
}

trait AutoDerivationRulesForDeserializers {

  implicit val identity: RawBodyDeserializer[RawBody] = RawBodyDeserializer.create(body => body)

  implicit val BytesRawBodyDeserializer: RawBodyDeserializer[Array[Byte]] =
    RawBodyDeserializer.create(body => IoUtils.toByteArray(body.data()))

  implicit val StringRawBodyDeserializer: RawBodyDeserializer[String] =
    BytesRawBodyDeserializer.map(new String(_))

  implicit val JsonRawBodyDeserializer: RawBodyDeserializer[JSONObject] =
    StringRawBodyDeserializer.map(new JSONObject(_))

  implicit val JsonArrayRawBodyDeserializer: RawBodyDeserializer[JSONArray] =
    StringRawBodyDeserializer.map(new JSONArray(_))

  implicit def objectFromJsonRawBodyDeserializer[T](implicit d: JsonDecoder[T]): RawBodyDeserializer[T] =
    JsonRawBodyDeserializer.map(d(_))

  implicit val Unit: BodyDeserializer[Unit] = BodyDeserializer.create(_ => ())

  implicit val EmptyBodyDeserializer: BodyDeserializer[EmptyBody] = BodyDeserializer.create {
    case body: EmptyBody => body
    case _               => throw new IllegalArgumentException("Body is not empty")
  }

  implicit def bodyDeserializerFrom[T](implicit d: RawBodyDeserializer[T]): BodyDeserializer[T] =
    BodyDeserializer.create {
      case body: RawBody => d.deserialize(body)
      case _: EmptyBody => throw new IllegalArgumentException("Body is empty")
      case obj =>
        throw new IllegalArgumentException(s"Can not decode ${obj.getClass.getSimpleName}")
    }

  implicit val CirceJsonRawBodyDeserializer: RawBodyDeserializer[Json] =
    StringRawBodyDeserializer.map(str => io.circe.parser.parse(str) match {
      case Right(json) => json
      case Left(error) => throw new IllegalArgumentException(error.message)
    })

  implicit def objectFromCirceJsonRawBodyDeserializer[T](implicit d: Decoder[T]): RawBodyDeserializer[T] =
    CirceJsonRawBodyDeserializer.map(json => d.decodeJson(json) match {
      case Right(result) => result
      case Left(error) => throw new IllegalArgumentException(error.message)
    })

  implicit def optionBodyDeserializerFrom[T](implicit d: RawBodyDeserializer[T]): BodyDeserializer[Option[T]] =
    BodyDeserializer.create {
      case body: RawBody => Some(d.deserialize(body))
      case _: EmptyBody  => None
      case obj =>
        throw new IllegalArgumentException(s"Can not decode ${obj.getClass.getSimpleName}")
    }

  implicit def responseDeserializerFrom[T](implicit bd: BodyDeserializer[T]): ResponseDeserializer[T] =
    responseDeserializerFrom2(bd).map(_.body)

  implicit def responseDeserializerFrom2[T](implicit bd: BodyDeserializer[T]): ResponseDeserializer[Response[T]] =
    ResponseDeserializer.create(response => response.copy(body = bd.deserialize(response.body)))

}
