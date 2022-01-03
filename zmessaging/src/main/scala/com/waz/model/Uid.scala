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

import java.util.UUID

import com.waz.api.NotificationsHandler.NotificationType
import com.waz.api.NotificationsHandler.NotificationType._
import com.waz.utils.crypto.ZSecureRandom
import com.waz.utils.wrappers.URI
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject

trait Id[A] extends Ordering[A] {
  def random(): A
  def decode(str: String): A
  def encode(id: A): String = id.toString
  def empty: A = decode("")

  override def compare(x: A, y: A): Int = Ordering.String.compare(encode(x), encode(y))
}

final case class Uid(str: String) extends AnyVal {
  override def toString: String = str
}

object Uid {
  def apply(): Uid = Uid(UUID.randomUUID().toString)
  def apply(mostSigBits: Long, leastSigBits: Long): Uid = Uid(new UUID(mostSigBits, leastSigBits).toString)

  implicit object UidId extends Id[Uid] {
    override def random(): Uid = Uid()
    override def decode(str: String): Uid = Uid(str)
  }
}

final case class UserId(str: String) {
  override def toString: String = str
}

object UserId {
  def apply(): UserId = Id.random()

  implicit object Id extends Id[UserId] {
    override def random(): UserId = UserId(Uid().toString)
    override def decode(str: String): UserId = UserId(str)
  }

  implicit lazy val UserIdDecoder: JsonDecoder[UserId] = new JsonDecoder[UserId] {
    override def apply(implicit o: JSONObject): UserId = UserId(o.getString("userId"))
  }

  implicit lazy val UserIdEncoder: JsonEncoder[UserId] = new JsonEncoder[UserId] {
    override def apply(id: UserId): JSONObject = JsonEncoder { _.put("userId", id.str) }
  }
}

final case class TeamId(str: String) extends AnyVal {
  override def toString: String = str
}

object TeamId {
  val Empty: TeamId = TeamId("")

  def apply(): TeamId = Id.random()

  implicit object Id extends Id[TeamId] {
    override def random(): TeamId = TeamId(Uid().toString)
    override def decode(str: String): TeamId = TeamId(str)
  }
}

final case class AccountId(str: String) extends AnyVal {
  override def toString: String = str
}

object AccountId {
  def apply(): AccountId = Id.random()

  implicit object Id extends Id[AccountId] {
    override def random(): AccountId = AccountId(Uid().toString)
    override def decode(str: String): AccountId = AccountId(str)
  }
}

sealed trait GeneralAssetId {
  val str: String
}

final case class DownloadAssetId(str: String) extends GeneralAssetId

object DownloadAssetId {
  def apply(): DownloadAssetId = Id.random()

  implicit object Id extends Id[DownloadAssetId] {
    override def random(): DownloadAssetId = DownloadAssetId(Uid().toString)
    override def decode(str: String): DownloadAssetId = DownloadAssetId(str)
  }
}

final case class UploadAssetId(str: String) extends GeneralAssetId

object UploadAssetId {
  def apply(): UploadAssetId = Id.random()

  implicit object Id extends Id[UploadAssetId] {
    override def random(): UploadAssetId = UploadAssetId(Uid().toString)
    override def decode(str: String): UploadAssetId = UploadAssetId(str)
  }
}

final case class AssetId(str: String) extends GeneralAssetId {
  override def toString: String = str
}

object AssetId {
  def apply(): AssetId = Id.random()

  implicit object Id extends Id[AssetId] {
    override def random(): AssetId = AssetId(Uid().toString)
    override def decode(str: String): AssetId = AssetId(str)
  }
}

final case class CacheKey(str: String) extends AnyVal {
  override def toString: String = str
}

object CacheKey {
  def apply(): CacheKey = Id.random()

  //any appended strings should be url friendly
  def decrypted(key: CacheKey): CacheKey = CacheKey(s"${key.str}_decr_")
  def fromAssetId(id: AssetId): CacheKey = CacheKey(s"${id.str}")
  def fromUri(uri: URI): CacheKey = CacheKey(uri.toString)

  implicit object Id extends Id[CacheKey] {
    override def random(): CacheKey = CacheKey(Uid().toString)
    override def decode(str: String): CacheKey = CacheKey(str)
  }
}

final case class RAssetId(str: String) extends AnyVal {
  override def toString: String = str
}

object RAssetId {
  def apply(): RAssetId = Id.random()

  implicit object Id extends Id[RAssetId] {
    override def random(): RAssetId = RAssetId(Uid().toString)
    override def decode(str: String): RAssetId = RAssetId(str)
  }
}

final case class MessageId(str: String) extends AnyVal {
  def uid: Uid = Uid(str)
  override def toString: String = str
}

object MessageId {
  val Empty: MessageId = MessageId("")

  def apply(): MessageId = Id.random()
  def fromUid(uid: Uid): MessageId = MessageId(uid.str)

  implicit object Id extends Id[MessageId] {
    override def random(): MessageId = MessageId(Uid().toString)
    override def decode(str: String): MessageId = MessageId(str)
  }
}

final case class ConvId(str: String) {
  override def toString: String = str
}

object ConvId {
  def apply(): ConvId = Id.random()

  implicit object Id extends Id[ConvId] {
    override def random(): ConvId = ConvId(Uid().toString)
    override def decode(str: String): ConvId = ConvId(str)
  }
}

final case class RConvId(str: String) {
  override def toString: String = str
}

object RConvId {
  val Empty: RConvId = RConvId("")
  def apply(): RConvId = Id.random()

  implicit object Id extends Id[RConvId] {
    override def random(): RConvId = RConvId(Uid().toString)
    override def decode(str: String): RConvId = RConvId(str)
  }
}

final case class SyncId(str: String) extends AnyVal {
  override def toString: String = str
}

object SyncId {
  def apply(): SyncId = Id.random()

  implicit object Id extends Id[SyncId] {
    override def random(): SyncId = SyncId(Uid().toString)
    override def decode(str: String): SyncId = SyncId(str)
  }
}

final case class PushToken(str: String) extends AnyVal {
  override def toString: String = str
}

object PushToken {
  def apply(): PushToken = Id.random()

  implicit object Id extends Id[PushToken] {
    override def random(): PushToken = PushToken(Uid().toString)
    override def decode(str: String): PushToken = PushToken(str)
  }
}

final case class TrackingId(str: String) extends AnyVal {
  override def toString: String = str
}

object TrackingId {
  def apply(): TrackingId = Id.random()

  implicit object Id extends Id[TrackingId] {
    override def random(): TrackingId = TrackingId(Uid().toString)
    override def decode(str: String): TrackingId = TrackingId(str)
  }
}

final case class CallSessionId(str: String) extends AnyVal {
  override def toString: String = str
}

object CallSessionId {
  def apply(): CallSessionId = Id.random()

  implicit object Id extends Id[CallSessionId] {
    override def random(): CallSessionId = CallSessionId(Uid().toString)
    override def decode(str: String): CallSessionId = CallSessionId(str)
  }

  implicit object DefaultOrdering extends Ordering[CallSessionId] {
    def compare(a: CallSessionId, b: CallSessionId): Int = Ordering.String.compare(a.str, b.str)
  }
}

final case class InvitationId(str: String) extends AnyVal {
  override def toString: String = str
}

object InvitationId extends (String => InvitationId) {
  def apply(): InvitationId = Id.random()

  implicit object Id extends Id[InvitationId] {
    override def random(): InvitationId = InvitationId(Uid().toString)
    override def decode(str: String): InvitationId = InvitationId(str)
  }
}

final case class ProviderId(str: String) extends AnyVal {
  override def toString: String = str
}

object ProviderId {
  def apply(): ProviderId = Id.random()

  implicit object Id extends Id[ProviderId] {
    override def random(): ProviderId = ProviderId(Uid().toString)
    override def decode(str: String): ProviderId = ProviderId(str)
  }
}

final case class IntegrationId(str: String) extends AnyVal {
  override def toString: String = str
}

object IntegrationId {
  def apply(): IntegrationId = Id.random()

  implicit object Id extends Id[IntegrationId] {
    override def random(): IntegrationId = IntegrationId(Uid().toString)
    override def decode(str: String): IntegrationId = IntegrationId(str)
  }
}

final case class FolderId(str: String) extends AnyVal {
  override def toString: String = str
}

object FolderId {
  def apply(): FolderId = Id.random()

  implicit object Id extends Id[FolderId] {
    override def random(): FolderId = FolderId(Uid().toString)
    override def decode(str: String): FolderId = FolderId(str)
  }
}

final case class ButtonId(str: String) extends AnyVal {
  override def toString: String = str
}

object ButtonId {
  def apply(): ButtonId = Id.random()

  implicit object Id extends Id[ButtonId] {
    override def random(): ButtonId = ButtonId(Uid().toString)
    override def decode(str: String): ButtonId = ButtonId(str)
  }
}
