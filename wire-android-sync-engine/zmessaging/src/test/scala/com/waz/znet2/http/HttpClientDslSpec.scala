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
package com.waz.znet2.http

import java.net.URL

import com.waz.specs.ZSpec
import com.waz.znet2.http
import com.waz.znet2.http.HttpClient.ProgressCallback
import com.waz.znet2.http.HttpClient.AutoDerivationOld._

class HttpClientDslSpec extends ZSpec {
  import http.HttpClient.dsl._

  private val testRequest = Request.create(method = Method.Get, url = new URL("http://test.com"))

  feature("Http response codes should be passed properly throw all wrappers.") {
    val testCodes = Set(1,2,3)

    val resultResponseCodes = testRequest
      .withResultHttpCodes(testCodes)
      .withResultType[Unit]
      .withErrorType[Unit]
      .resultResponseCodes

    resultResponseCodes shouldBe testCodes
  }

  feature("Download callback should be passed properly throw all wrappers.") {
    val testCallback: ProgressCallback = null

    val callback = testRequest
      .withDownloadCallback(testCallback)
      .withResultType[Unit]
      .withErrorType[Unit]
      .downloadCallback

    callback shouldBe Some(testCallback)
  }

  feature("Upload callback should be passed properly throw all wrappers.") {
    val testCallback: ProgressCallback = null

    val callback = testRequest
      .withUploadCallback(testCallback)
      .withResultType[Unit]
      .withErrorType[Unit]
      .uploadCallback

    callback shouldBe Some(testCallback)
  }

  feature("Download callback should be not set if it has not been set.") {
    val testCallback: ProgressCallback = null

    val callback = testRequest
      .withUploadCallback(testCallback)
      .withResultType[Unit]
      .withErrorType[Unit]
      .downloadCallback

    callback shouldBe None
  }

  feature("Upload callback should be not set if it has not been set.") {
    val testCallback: ProgressCallback = null

    val callback = testRequest
      .withDownloadCallback(testCallback)
      .withResultType[Unit]
      .withErrorType[Unit]
      .uploadCallback

    callback shouldBe None
  }

}
