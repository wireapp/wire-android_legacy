package com.waz.service.call

import com.waz.zms.BuildConfig

object DomainUtils {
  def getDomainFromString(id: String): String =
    if(BuildConfig.FEDERATION_USER_DISCOVERY)
      id.split("@").last
    else ""

  def getFederatedId(id: String, domain: String): String = {
    if(BuildConfig.FEDERATION_USER_DISCOVERY)
      s"$id@$domain"
    else id
  }
}
