package com.waz.service.assets

import java.io.InputStream
import java.net.URI

import com.waz.model.Mime

import scala.util.Try

trait UriHelper {
  def openInputStream(uri: URI): Try[InputStream]
  def extractMime(uri: URI): Try[Mime]
  def extractSize(uri: URI): Try[Long]
  def extractFileName(uri: URI): Try[String]
}
