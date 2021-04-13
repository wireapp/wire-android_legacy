package com.waz.zclient.security.checks

import com.waz.zclient.security.SecurityChecklist

import scala.concurrent.Future

class RequestLegalHoldCheck extends SecurityChecklist.Check {
  override def isSatisfied: Future[Boolean] = Future.successful(false)
}

object RequestLegalHoldCheck {
  def apply(): RequestLegalHoldCheck = new RequestLegalHoldCheck()
}
