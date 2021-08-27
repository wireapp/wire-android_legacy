package com.waz.sync.handler

import com.waz.model.GenericContent.DataTransfer
import com.waz.model.{ConvId, GenericMessage, TrackingId, Uid, UserId}
import com.waz.sync.SyncResult
import com.waz.sync.otr.OtrSyncHandler
import com.waz.sync.otr.OtrSyncHandler.TargetRecipients

import scala.concurrent.Future

class TrackingSyncHandler(selfUserId: UserId, otrSync: OtrSyncHandler) {
  import com.waz.threading.Threading.Implicits.Background

  def postNewTrackingId(trackingId: TrackingId): Future[SyncResult] =
    otrSync
      .postOtrMessage(
        ConvId(selfUserId.str),
        GenericMessage(Uid(), DataTransfer(trackingId)),
        isHidden = true,
        TargetRecipients.SpecificUsers(Set(selfUserId))
      )
      .map(SyncResult(_))
}
