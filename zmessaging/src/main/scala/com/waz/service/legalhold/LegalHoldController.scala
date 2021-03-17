package com.waz.service.legalhold

import com.waz.model.{ConvId, UserId}
import com.waz.service.legalhold.LegalHoldStatus.Disabled
import com.wire.signals.Signal

//TODO: implement status calculation
class LegalHoldController {

  def legalHoldStatus(userId: UserId): Signal[LegalHoldStatus] =
    Signal.const(Disabled)

  def legalHoldStatus(conversationId: ConvId): Signal[LegalHoldStatus] =
    Signal.const(Disabled)
}
