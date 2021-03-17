package com.waz.service.legalhold

sealed trait LegalHoldStatus

object LegalHoldStatus {
  case object Degraded extends LegalHoldStatus
  case object Enabled extends LegalHoldStatus
  case object Disabled extends LegalHoldStatus
}
