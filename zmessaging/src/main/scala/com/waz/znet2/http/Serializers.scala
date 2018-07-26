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

import java.io.{ByteArrayInputStream, File, FileInputStream}

import com.waz.utils.JsonEncoder
import io.circe.{Encoder, Json}
import org.json.JSONObject

trait RequestSerializer[T] {
  def serialize(request: Request[T]): Request[Body]
  def contramap[B](f: B => T): RequestSerializer[B] =
    RequestSerializer.create { request =>
      request.copy(body = this.serialize(request.copy(body = f(request.body))).body)
    }
}

object RequestSerializer {

  def apply[T](implicit rs: RequestSerializer[T]): RequestSerializer[T] = rs

  def create[T](f: Request[T] => Request[Body]): RequestSerializer[T] = new RequestSerializer[T] {
    override def serialize(request: Request[T]): Request[Body] = f(request)
  }

}

trait BodySerializer[T] {
  def serialize(body: T): Body
  def contramap[B](f: B => T): BodySerializer[B] =
    BodySerializer.create(f andThen this.serialize)
}

object BodySerializer {

  def apply[T](implicit bs: BodySerializer[T]): BodySerializer[T] = bs

  def create[T](f: T => Body): BodySerializer[T] = new BodySerializer[T] {
    override def serialize(body: T): Body = f(body)
  }

}

trait RawBodySerializer[T] {
  def serialize(value: T): RawBody
  def contramap[B](f: B => T): RawBodySerializer[B] =
    RawBodySerializer.create(f andThen this.serialize)
}

object RawBodySerializer {

  def apply[T](implicit rbs: RawBodyDeserializer[T]): RawBodyDeserializer[T] = rbs

  def create[T](f: T => RawBody): RawBodySerializer[T] = new RawBodySerializer[T] {
    override def serialize(body: T): RawBody = f(body)
  }

}

trait AutoDerivationRulesForSerializers {

  implicit val StringBodySerializer: RawBodySerializer[String] =
    RawBodySerializer.create(str => {
      val bytes = str.getBytes("utf-8")
      RawBody(Some(MediaType.PlainText), () => new ByteArrayInputStream(bytes), Some(bytes.length))
    })

  implicit val BytesBodySerializer: RawBodySerializer[Array[Byte]] =
    RawBodySerializer.create(bytes => RawBody(Some(MediaType.Bytes), () => new ByteArrayInputStream(bytes), Some(bytes.length)))

  implicit val FileBodySerializer: RawBodySerializer[File] =
    RawBodySerializer.create(file => RawBody(None, () => new FileInputStream(file), Some(file.length())))

  implicit val JsonBodySerializer: RawBodySerializer[JSONObject] =
    RawBodySerializer.create(json => {
      val bytes = json.toString.getBytes("utf8")
      RawBody(Some(MediaType.Json), () => new ByteArrayInputStream(bytes), Some(bytes.length))
    })

  implicit def objectToJsonBodySerializer[T](implicit e: JsonEncoder[T]): RawBodySerializer[T] =
    JsonBodySerializer.contramap(e.apply)

  implicit val MultipartMixedBodySerializer: BodySerializer[MultipartBodyMixed] =
    BodySerializer.create { body =>
      RawMultipartBodyMixed(body.parts.map(p => RawMultipartBodyMixed.Part(p.serialize, p.headers)))
    }

  implicit val MultipartFormDataBodySerializer: BodySerializer[MultipartBodyFormData] =
    BodySerializer.create { body =>
      RawMultipartBodyFormData(body.parts.map(p => RawMultipartBodyFormData.Part(p.serialize, p.name, p.fileName)))
    }

  implicit val CirceJsonBodySerializer: RawBodySerializer[Json] = RawBodySerializer.create(json => {
    val bytes = json.noSpaces.getBytes("utf8")
    RawBody(Some(MediaType.Json), () => new ByteArrayInputStream(bytes), Some(bytes.length))
  })

  implicit def objectToCirceJsonBodySerializer[T](implicit e: Encoder[T]): RawBodySerializer[T] =
    CirceJsonBodySerializer.contramap(e.apply)

  implicit def bodySerializerFromRawBodySerializer[T](implicit rbs: RawBodySerializer[T]): BodySerializer[T] =
    BodySerializer.create(rbs.serialize)

  implicit val EmptyBodyRequestSerializer: RequestSerializer[EmptyBody] =
    RequestSerializer.create(request => request.copy(body = EmptyBodyImpl))

  implicit def serializerFromBodySerializer[T](implicit bs: BodySerializer[T]): RequestSerializer[T] =
    RequestSerializer.create(request => request.copy(body = bs.serialize(request.body)))

}