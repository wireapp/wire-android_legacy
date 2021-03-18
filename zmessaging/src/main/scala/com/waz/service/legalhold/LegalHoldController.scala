package com.waz.service.legalhold

import com.waz.model.{ConvId, UserId}
import com.wire.signals.Signal

//TODO: implement status calculation
class LegalHoldController {

  def isLegalHoldActive(userId: UserId): Signal[Boolean] =
    Signal.const(false)

  def isLegalHoldActive(conversationId: ConvId): Signal[Boolean] =
    Signal.const(false)
}
