/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.utils.wrappers

import java.io.File

import android.net.Uri

import scala.collection.JavaConverters._

trait URI {
  def buildUpon: URIBuilder
  def getPath: String
  def getScheme: String
  def getAuthority: String
  def getHost: String
  def getPathSegments: List[String]
  def getLastPathSegment: String
  def getQueryParameter(key: String): String
  def normalizeScheme: URI

  override def equals(obj: scala.Any): Boolean = obj match {
    case uri: URI => this.toString == uri.toString
    case _ => false
  }

  override def hashCode(): Int = this.toString.hashCode
}

trait URIUtil {
  def parse(uri: String): URI

  def fromFile(file: File): URI

  /**
    * This method should only be used in Android-specific code. Even then, it's use is a bad smell - really any code
    * using it should also be wrapped and pushed to the edges of our business logic to limit dependencies on Android.
    */
  def unwrap(uri: URI): Uri = uri match {
    case uri: AndroidURI => uri.uri
    case _ => throw new IllegalArgumentException(s"Expected Android URI, but tried to unwrap: $uri")
  }
}

trait URIBuilder {
  def appendPath(path: String): URIBuilder
  def encodedPath(path: String): URIBuilder
  def appendEncodedPath(path: String): URIBuilder
  def appendQueryParameter(key: String, value: String): URIBuilder
  def build: URI
}

//Default Android dependencies
class AndroidURI(val uri: Uri) extends URI {
  override def buildUpon                      = new AndroidURIBuilder(uri.buildUpon())
  override def getPath                        = uri.getPath
  override def getScheme                      = uri.getScheme
  override def getAuthority                   = uri.getAuthority
  override def getHost                        = uri.getHost
  override def getPathSegments                = uri.getPathSegments.asScala.toList
  override def getLastPathSegment             = uri.getLastPathSegment
  override def normalizeScheme                = new AndroidURI(uri.normalizeScheme())
  override def getQueryParameter(key: String) = uri.getQueryParameter(key)
  override def toString                       = uri.toString
}

class AndroidURIBuilder(var uriBuilder: Uri.Builder) extends URIBuilder {
  override def appendPath(newSegment: String)                   = new AndroidURIBuilder(uriBuilder.appendPath(newSegment))
  override def encodedPath(path: String)                        = new AndroidURIBuilder(uriBuilder.encodedPath(path))
  override def appendEncodedPath(newSegment: String)            = new AndroidURIBuilder(uriBuilder.appendEncodedPath(newSegment))
  override def appendQueryParameter(key: String, value: String) = new AndroidURIBuilder(uriBuilder.appendQueryParameter(key, value))
  override def build                                            = new AndroidURI(uriBuilder.build())

}

object AndroidURIUtil extends URIUtil {
  override def parse(uri: String) = new AndroidURI(Uri.parse(uri))
  override def fromFile(file: File) = new AndroidURI(Uri.fromFile(file))
}

object URI {
  private var util: URIUtil = AndroidURIUtil

  def setUtil(util: URIUtil) = {
    this.util = util
  }

  def parse(uri: String) = util.parse(uri)

  def fromFile(file: File) = util.fromFile(file)

  def unwrap(uri: URI) = util.unwrap(uri)

  def toJava(uri: URI): java.net.URI = java.net.URI.create(util.unwrap(uri).toString)

}