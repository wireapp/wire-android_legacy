/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient

import android.support.test.runner.AndroidJUnit4
import com.waz.model.UserId
import com.waz.sync.client.AuthenticationManager.Cookie
import com.waz.zclient.appentry.SSOWebViewWrapper
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(classOf[AndroidJUnit4])
class SSOTest {

  @Test
  def ignoreNonWireURls(): Unit ={
    val url = "https://www.wire.com"
    assert(SSOWebViewWrapper.parseURL(url).isEmpty)
  }

  @Test
  def parseSuccessURI(): Unit ={
    val url = s"${SSOWebViewWrapper.ResponseSchema}://something/?${SSOWebViewWrapper.UserIdQuery}=123&${SSOWebViewWrapper.CookieQuery}=321"

    val result = SSOWebViewWrapper.parseURL(url)
    assert(result.exists{
      case Right((Cookie("321"), UserId("123"))) => true
      case _ => false
    })
  }

  @Test
  def parseErrorURI(): Unit ={
    val url = s"${SSOWebViewWrapper.ResponseSchema}://something/?${SSOWebViewWrapper.FailureQuery}=oops"
    val result = SSOWebViewWrapper.parseURL(url)
    assert(result.exists{
      case Left(-1) => true
      case _ => false
    })
  }
}
