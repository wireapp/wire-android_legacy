package com.waz.zclient.deeplinks

import android.content.Context
import com.waz.log.BasicLogging.LogTag.DerivedLogTag

import scala.concurrent.ExecutionContext

class DeepLinkService(implicit ec: ExecutionContext) extends DerivedLogTag {

  /**
    * @return true in case if dep link was opened
    */
  def openDeepLink(link: DeepLink)(implicit context: Context): Boolean = link match {
    case DeepLink.SSOLogin(token) => openSSOLogin(token)
  }

  private def openSSOLogin(token: String)(implicit context: Context): Boolean = ???

}
