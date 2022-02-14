package com.waz.service.call

object DomainUtils {
  def getDomainFromString(idString: String): String =
    if (idString.contains("@")) idString.split("@").last else ""

  def joinIdWithDomain(idString: String, domainString: String ): String =
    if(domainString.nonEmpty)
      s"$idString@$domainString"
    else idString

  def removeDomain(idString: String): String = idString.split("@").head
}
