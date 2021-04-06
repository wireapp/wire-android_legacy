package com.waz.zclient.legalhold

import com.waz.model.{ConvId, UserId}
import com.waz.service.LegalHoldService
import com.waz.zclient.{Injectable, Injector}
import com.wire.signals.Signal

//TODO: implement status calculation
class LegalHoldController(implicit injector: Injector)
  extends Injectable {

  private lazy val legalHoldService = inject[Signal[LegalHoldService]]

  def isLegalHoldActive(userId: UserId): Signal[Boolean] =
    Signal.const(false)

  def isLegalHoldActive(conversationId: ConvId): Signal[Boolean] =
    Signal.const(false)
}
