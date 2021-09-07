package com.waz.model.otr

import com.waz.model.RemoteInstant
import com.waz.utils.JsonDecoder
import org.json.JSONObject

sealed trait MessageResponse {
  def mismatch: ClientMismatch

  def deleted: OtrClientIdMap = mismatch.deleted

  def missing: OtrClientIdMap = mismatch.missing
}

object MessageResponse {
  final case class Success(mismatch: ClientMismatch) extends MessageResponse
  final case class Failure(mismatch: ClientMismatch) extends MessageResponse
}

final case class ClientMismatch(redundant: OtrClientIdMap = OtrClientIdMap.Empty,
                                missing:   OtrClientIdMap = OtrClientIdMap.Empty,
                                deleted:   OtrClientIdMap = OtrClientIdMap.Empty,
                                time:     RemoteInstant)

object ClientMismatch {
  implicit lazy val Decoder: JsonDecoder[ClientMismatch] = new JsonDecoder[ClientMismatch] {
    import JsonDecoder._
    import OtrClientIdMap.decodeMap

    override def apply(implicit js: JSONObject): ClientMismatch =
      ClientMismatch(
        decodeMap('redundant),
        decodeMap('missing),
        decodeMap('deleted),
        decodeOptUtcDate('time).map(t => RemoteInstant.ofEpochMilli(t.getTime)).getOrElse(RemoteInstant.Epoch)
      )
  }
}

sealed trait QMessageResponse {
  def mismatch: QClientMismatch

  def deleted: QOtrClientIdMap = mismatch.deleted

  def missing: QOtrClientIdMap = mismatch.missing
}

object QMessageResponse {
  final case class Success(mismatch: QClientMismatch) extends QMessageResponse
  final case class Failure(mismatch: QClientMismatch) extends QMessageResponse
}

final case class QClientMismatch(redundant: QOtrClientIdMap = QOtrClientIdMap.Empty,
                                 missing:   QOtrClientIdMap = QOtrClientIdMap.Empty,
                                 deleted:   QOtrClientIdMap = QOtrClientIdMap.Empty,
                                 time:     RemoteInstant)

object QClientMismatch {
  implicit lazy val Decoder: JsonDecoder[QClientMismatch] = new JsonDecoder[QClientMismatch] {
    import JsonDecoder._
    import QOtrClientIdMap.decodeMap

    override def apply(implicit js: JSONObject): QClientMismatch =
      QClientMismatch(
        decodeMap('redundant),
        decodeMap('missing),
        decodeMap('deleted),
        decodeOptUtcDate('time).map(t => RemoteInstant.ofEpochMilli(t.getTime)).getOrElse(RemoteInstant.Epoch)
      )
  }
}
