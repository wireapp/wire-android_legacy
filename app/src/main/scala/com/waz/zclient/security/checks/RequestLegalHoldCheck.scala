package com.waz.zclient.security.checks

import com.waz.zclient.legalhold.LegalHoldController
import com.waz.zclient.security.SecurityChecklist
import com.waz.threading.Threading.Implicits.Background

import scala.concurrent.Future

class RequestLegalHoldCheck(legalHoldController: LegalHoldController) extends SecurityChecklist.Check {

  override def isSatisfied: Future[Boolean] =
    legalHoldController.legalHoldRequest.head.map(_.isEmpty)
}

object RequestLegalHoldCheck {
  def apply(legalHoldController: LegalHoldController): RequestLegalHoldCheck =
    new RequestLegalHoldCheck(legalHoldController)
}
