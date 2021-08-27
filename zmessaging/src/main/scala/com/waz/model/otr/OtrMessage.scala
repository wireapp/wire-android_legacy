package com.waz.model.otr

import java.io.ByteArrayInputStream

import com.google.protobuf.ByteString
import com.waz.model.{QualifiedId, UserId}
import com.waz.sync.client.OtrClient
import com.waz.sync.client.OtrClient.{EncryptedContent, QEncryptedContent}
import com.waz.znet2.http.{MediaType, RawBody, RawBodySerializer}
import com.wire.messages.Otr
import com.wire.messages.Otr.ClientMismatchStrategy.ReportOnly

import scala.collection.JavaConverters._

final case class OtrMessage(sender:         ClientId,
                            recipients:     EncryptedContent,
                            external:       Option[Array[Byte]] = None,
                            nativePush:     Boolean = true,
                            report_missing: Option[Set[UserId]] = None)

object OtrMessage {
  implicit val OtrMessageSerializer: RawBodySerializer[OtrMessage] = RawBodySerializer.create { m =>
    val builder = Otr.NewOtrMessage.newBuilder()
      .setSender(OtrClient.clientId(m.sender))
      .setNativePush(m.nativePush)
      .addAllRecipients(m.recipients.userEntries.toIterable.asJava)

    m.external.foreach { ext => builder.setBlob(ByteString.copyFrom(ext)) }
    m.report_missing.foreach { missing => builder.addAllReportMissing(missing.map(OtrClient.userId).asJava) }

    val bytes = builder.build.toByteArray
    RawBody(mediaType = Some(MediaType.Protobuf), () => new ByteArrayInputStream(bytes), dataLength = Some(bytes.length))
  }
}

final case class QualifiedOtrMessage(sender:         ClientId,
                                     recipients:     QEncryptedContent,
                                     external:       Option[Array[Byte]] = None,
                                     nativePush:     Boolean = true,
                                     reportMissing:  Option[Set[QualifiedId]] = None,
                                     reportAll:      Boolean = true
                                    )

object QualifiedOtrMessage {
  implicit val QualifiedOtrMessageSerializer: RawBodySerializer[QualifiedOtrMessage] = RawBodySerializer.create { m =>
    val builder = Otr.QualifiedNewOtrMessage.newBuilder()
      .setSender(OtrClient.clientId(m.sender))
      .setNativePush(m.nativePush)
      .addAllRecipients(m.recipients.entries.toIterable.asJava)

    m.external.foreach { ext => builder.setBlob(ByteString.copyFrom(ext)) }

    (m.reportAll, m.reportMissing) match {
      case (true, _) =>
        builder.setReportAll(Otr.ClientMismatchStrategy.ReportAll.getDefaultInstance)
      case (false, None) =>
        builder.setIgnoreAll(Otr.ClientMismatchStrategy.IgnoreAll.getDefaultInstance)
      case (false, Some(missing)) =>
        val reportOnlyBuilder = ReportOnly.newBuilder()
        reportOnlyBuilder.addAllUserIds(missing.map(OtrClient.qualifiedId).asJava)
        builder.setReportOnly(reportOnlyBuilder.build)
    }

    val bytes = builder.build.toByteArray
    RawBody(mediaType = Some(MediaType.Protobuf), () => new ByteArrayInputStream(bytes), dataLength = Some(bytes.length))
  }
}

