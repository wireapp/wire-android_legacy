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
package com.waz.sync.handler

import com.waz.content.ConversationStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.GenericContent.LastRead
import com.waz.model._
import com.waz.service.BackendConfig.FederationSupport
import com.waz.service.{BackendConfig, MetaDataService}
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.{Failure, Success}
import com.waz.sync.otr.OtrSyncHandler
import com.waz.sync.otr.OtrSyncHandler.{QTargetRecipients, TargetRecipients}
import com.waz.utils.RichWireInstant
import com.waz.zms.BuildConfig
import com.wire.signals.Signal

import scala.concurrent.Future

final class LastReadSyncHandler(selfUserId:    UserId,
                                currentDomain: Domain,
                                backend:       Signal[BackendConfig],
                                convs:         ConversationStorage,
                                metadata:      MetaDataService,
                                msgsSync:      MessagesSyncHandler,
                                otrSync:       OtrSyncHandler) extends DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  private def federationSupported: Boolean = backend.currentValue.exists { b => b.federationSupport.isSupported }

  def postLastRead(convId: ConvId, time: RemoteInstant, jobId: SyncId): Future[SyncResult] = {
    verbose(l"SSM13<JOB:$jobId> postLastRead step 1")
    convs.get(convId).flatMap {
      case Some(conv) if conv.lastRead.isAfter(time) => // no need to send this msg as lastRead was already advanced
        verbose(l"SSM13<JOB:$jobId> postLastRead step 2A")
        Future.successful(Success)
      case Some(conv) =>
        verbose(l"SSM13<JOB:$jobId> postLastRead step 2Ba")
        val msg = GenericMessage(Uid(), LastRead(conv.remoteId, time))
        verbose(l"SSM13<JOB:$jobId> postLastRead step 2Bb")
        val postMsg =
          if (federationSupported) {
            val qId = currentDomain.mapOpt(QualifiedId(selfUserId, _)).getOrElse(QualifiedId(selfUserId))
            verbose(l"SSM13<JOB:$jobId> postLastRead step 2Bc1")
            val f = otrSync.postQualifiedOtrMessage(ConvId(selfUserId.str), msg, isHidden = true, QTargetRecipients.SpecificUsers(Set(qId)), jobId = Option(jobId))
            verbose(l"SSM13<JOB:$jobId> postLastRead step 2Bc1.2")
            f
          } else {
            verbose(l"SSM13<JOB:$jobId> postLastRead step 2Bc2")
            val f = otrSync.postOtrMessage(ConvId(selfUserId.str), msg, isHidden = true, TargetRecipients.SpecificUsers(Set(selfUserId)), jobId = Option(jobId))
            verbose(l"SSM13<JOB:$jobId> postLastRead step 2Bc2.2")
            f
          }
        postMsg.map({ r =>
          verbose(l"SSM13<JOB:$jobId> postLastRead completed: ${r}")
          SyncResult(r)
        })
      case None =>
        verbose(l"SSM13<JOB:$jobId> postLastRead step 3:None")
        Future.successful(Failure(s"No conversation found for id: $convId"))
    }
  }
}
