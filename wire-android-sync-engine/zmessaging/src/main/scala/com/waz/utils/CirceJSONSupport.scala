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
package com.waz.utils
import java.net.{URI, URL}

import com.waz.model._
import com.waz.model.otr.ClientId
import io.circe.generic.AutoDerivation
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import org.threeten.bp.{Duration, Instant}

trait CirceJSONSupport extends AutoDerivation {

  implicit def UrlDecoder: Decoder[URL] = Decoder[String].map(new URL(_))
  implicit def UrlEncoder: Encoder[URL] = Encoder[String].contramap(_.toString)

  implicit def UriDecoder: Decoder[URI] = Decoder[String].map(URI.create)
  implicit def UriEncoder: Encoder[URI] = Encoder[String].contramap(_.toString)

  implicit def Sha256Decoder: Decoder[Sha256] = Decoder[String].map(Sha256.apply)
  implicit def Sha256Encoder: Encoder[Sha256] = Encoder[String].contramap(_.str)

  implicit def DurationDecoder: Decoder[Duration] = Decoder[Long].map(Duration.ofMillis)
  implicit def DurationEncoder: Encoder[Duration] = Encoder[Long].contramap(_.toMillis)

  implicit def InstantDecoder: Decoder[Instant] = Decoder[String].map(Instant.parse)

  implicit def NameDecoder: Decoder[Name] = Decoder[String].map(Name.apply)

  implicit def UserIdDecoder: Decoder[UserId] = Decoder[String].map(UserId.apply)
  implicit def TeamIdDecoder: Decoder[TeamId] = Decoder[String].map(TeamId.apply)
  implicit def ConvIdDecoder: Decoder[ConvId] = Decoder[String].map(ConvId.apply)
  implicit def ClientIdDecoder: Decoder[ClientId] = Decoder[String].map(ClientId.apply)

  implicit def RAssetIdDecoder: Decoder[RAssetId] = Decoder[String].map(RAssetId.apply)
  implicit def AssetIdDecoder: Decoder[AssetId] = Decoder[String].map(AssetId.apply)
  implicit def AssetTokenDecoder: Decoder[AssetToken] = Decoder[String].map(AssetToken.apply)

  implicit def FolderIdKeyDecoder: KeyDecoder[FolderId] = KeyDecoder[String].map(FolderId.apply)
  implicit def FolderIdKeyEncoder: KeyEncoder[FolderId] = KeyEncoder[String].contramap(_.str)
}
