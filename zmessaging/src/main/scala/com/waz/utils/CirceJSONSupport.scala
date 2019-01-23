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
import java.net.URL

import com.waz.model.{RAssetId, UserId}
import io.circe.generic.AutoDerivation
import io.circe.{Decoder, Encoder}
import org.threeten.bp.Duration

trait CirceJSONSupport extends AutoDerivation {

  implicit def UrlDecoder: Decoder[URL] = Decoder[String].map(new URL(_))
  implicit def UrlEncoder: Encoder[URL] = Encoder[String].contramap(_.toString)

  implicit def DurationDecoder: Decoder[Duration] = Decoder[Long].map(Duration.ofMillis)
  implicit def DurationEncoder: Encoder[Duration] = Encoder[Long].contramap(_.toMillis)

  implicit def RAssetIdDecoder: Decoder[RAssetId] = Decoder[String].map(RAssetId.apply)
  implicit def UserIdDecoder: Decoder[UserId] = Decoder[String].map(UserId.apply)

}
