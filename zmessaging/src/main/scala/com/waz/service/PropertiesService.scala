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
package com.waz.service

import scala.language.implicitConversions
import com.waz.content.{PropertiesStorage, PropertyValue, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.{FoldersEvent, PropertyEvent, ReadReceiptEnabledPropertyEvent, UnknownPropertyEvent}
import com.waz.service.EventScheduler.Stage
import com.waz.service.assets.Codec
import com.waz.service.push.PushService
import com.waz.service.push.PushService.{ForceSync, SyncHistory}
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.utils.RichFuture
import com.wire.signals.Signal
import io.circe.{Decoder, Encoder}

import scala.concurrent.Future

trait PropertiesService {
  val eventProcessor: Stage.Atomic

  def updateProperty[T: Encoder: Decoder](key: PropertyKey, value: T): Future[Unit]

  def setReadReceiptsEnabled(enabled: Boolean): Future[Unit]
  def readReceiptsEnabled: Signal[Boolean]
}

class PropertiesServiceImpl(prefs: UserPreferences, syncServiceHandle: SyncServiceHandle, storage: PropertiesStorage, requestService: SyncRequestService, pushService: PushService)
  extends PropertiesService with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  val eventProcessor: Stage.Atomic = EventScheduler.Stage[PropertyEvent]({ (_, events, tag) =>
    verbose(l"SSSTAGES<TAG:$tag> PropertiesServiceImpl stage 1")
    RichFuture.traverseSequential(events)(processEvent)
  },
    name = "PropertiesService - PropertyEvent"
  )

  for {
    readReceipts <- getProperty[Int](PropertyKey.ReadReceiptsEnabled)
    if readReceipts.isEmpty
    syncId <- syncServiceHandle.syncProperties()
    _ <- requestService.await(syncId)
    _ <- pushService.syncNotifications(SyncHistory(ForceSync)) // Force fetch to clear the sync event
  } ()

  private def processEvent(event: PropertyEvent): Future[Unit] =
    event match {
      case ReadReceiptEnabledPropertyEvent(value) =>
        getProperty[Int](PropertyKey.ReadReceiptsEnabled).flatMap {
          case Some(current) if current != value =>
            verbose(l"processEvent ReadReceiptsEnabled: current:$current value:$value")
            for {
              _ <- prefs(UserPreferences.ReadReceiptsRemotelyChanged) := true
              _ <- updateProperty(PropertyKey.ReadReceiptsEnabled, value)
            } yield ()
          case other =>
            verbose(l"processEvent ReadReceiptsEnabled: other:$other value:$value")
            Future.successful({})
        }

      case FoldersEvent(folders) => Future.successful({}) // handled in FoldersService

      case UnknownPropertyEvent(key, _) =>
        verbose(l"Unhandled property event $key")
        Future.successful({})
    }

  def updateProperty[T: Encoder: Decoder](key: PropertyKey, value: T): Future[Unit] = {
    import io.circe.syntax._
    storage.save(PropertyValue(key.str, value.asJson.toString()))
  }

  def getProperty[T: Encoder: Decoder](key: PropertyKey): Future[Option[T]] = {
    import io.circe.parser._

    storage.find(key).map {
      case Some(v) => decode[T](v.value) match {
        case Left(_) => Option.empty[T]
        case Right(x) => Option(x)
      }
      case _ => Option.empty[T]
    }
  }

  def propertySignal[T: Encoder: Decoder](key: PropertyKey): Signal[Option[T]] = {
    import io.circe.parser._

    storage.optSignal(key).map {
      case Some(v) => decode[T](v.value) match {
          case Left(_) => Option.empty[T]
          case Right(x) => Option(x)
      }
      case _ => Option.empty[T]
    }
  }

  def setReadReceiptsEnabled(enabled: Boolean): Future[Unit] = {
    verbose(l"setReadReceiptsEnabled $enabled")
    val enabledInt = if (enabled) 1 else 0
    for {
      current <- getProperty[Int](PropertyKey.ReadReceiptsEnabled)
      _ = verbose(l"setReadReceiptsEnabled current: $current")
      _ <- if (!current.contains(enabledInt)) updateProperty(PropertyKey.ReadReceiptsEnabled, enabledInt) else Future.successful({})
      _ <- if (!current.contains(enabledInt)) syncServiceHandle.postProperty(PropertyKey.ReadReceiptsEnabled, enabledInt) else Future.successful({})
    } yield ()
  }

  def readReceiptsEnabled: Signal[Boolean] = propertySignal[Int](PropertyKey.ReadReceiptsEnabled).map(_.exists(_ > 0))
}

case class PropertyKey(str: String) {
  override def toString: String = str
}

object PropertyKey extends (String => PropertyKey) {
  implicit def toString(key: PropertyKey): String = key.str
  implicit def fromString(str: String): PropertyKey = PropertyKey(str)
  implicit val PropertyKeyCodec: Codec[PropertyKey, String] = Codec.create(_.str, PropertyKey.apply)

  val ReadReceiptsEnabled: PropertyKey = "WIRE_RECEIPT_MODE"
  val Folders: PropertyKey = "labels"
}
