package com.waz.service

import com.waz.content.{PropertiesStorage, PropertyValue}
import com.waz.model.{LegalHoldRequest, LegalHoldRequestEvent, UserId}
import com.waz.service.EventScheduler.Stage
import com.waz.utils.{JsonDecoder, JsonEncoder}

import scala.concurrent.Future

trait LegalHoldService {
  def legalHoldRequestEventStage: Stage.Atomic
  def fetchLegalHoldRequest: Future[Option[LegalHoldRequest]]
}

class LegalHoldServiceImpl(selfUserId: UserId, storage: PropertiesStorage)
  extends LegalHoldService {

  import com.waz.threading.Threading.Implicits.Background
  import LegalHoldService._

  override def legalHoldRequestEventStage: Stage.Atomic = EventScheduler.Stage[LegalHoldRequestEvent] { (_, events) =>
    Future.sequence {
      events
        .filter(_.targetUserId == selfUserId)
        .map(event => storeRequest(event.request))
    }.map(_ => ())
  }

  override def fetchLegalHoldRequest: Future[Option[LegalHoldRequest]] = {
    storage.find(LegalHoldRequestKey).map { property =>
      property.map(_.value).map(JsonDecoder.decode[LegalHoldRequest])
    }
  }

  private def storeRequest(request: LegalHoldRequest): Future[Unit] = {
    val value = JsonEncoder.encode[LegalHoldRequest](request).toString
    storage.save(PropertyValue(LegalHoldRequestKey, value))
  }

}

object LegalHoldService {

  val LegalHoldRequestKey: PropertyKey = PropertyKey("legal-hold-request")

}
