package com.waz.service.call

import com.waz.zms.BuildConfig

object DomainUtils {
  def getDomainFromString(idString: String): String =
    if(BuildConfig.FEDERATION_USER_DISCOVERY)
      idString.split("@").last
    else ""

  def joinIdWithDomain(idString: String, domainString: String ): String =
    if(domainString.nonEmpty)
      s"$idString@$domainString"
    else idString

  def removeDomain(idString: String): String = idString.split("@").head
}
