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
package com.waz.permissions

import com.waz.permissions.PermissionsService.{Permission, PermissionProvider}
import com.waz.specs.AndroidFreeSpec
import com.waz.threading.Threading
import com.waz.utils.events.{Signal, SourceSignal}
import com.waz.ZLog.ImplicitTag._

import scala.collection.immutable.ListSet

class PermissionsServiceSpec extends AndroidFreeSpec {

  val service = new PermissionsService()

  val requestInput  = new SourceSignal[ListSet[Permission]]

  //Some system permission we haven't yet discovered
  val systemState = Signal(ListSet(
    Permission("WriteExternal"),
    Permission("ReadExternal")
  ))

  def getSystemState = systemState.currentValue("").get

  var requestCalls = 0
  var checkCalls = 0

  def newProvider = new PermissionProvider {
    def requestPermissions(ps: ListSet[PermissionsService.Permission]) = {
      println(s"requestPermissions from Provider: $ps")
      requestInput ! ps
      requestCalls += 1
      systemState.head
    }

    def hasPermissions(ps: ListSet[PermissionsService.Permission]) = {
      Threading.assertUiThread()
      checkCalls += 1
      getSystemState
    }
  }

  val provider = newProvider

  scenario("Provider references are not duplicated and kept in order") {

    val prov1 = newProvider
    val prov2 = newProvider

    service.registerProvider(prov1)
    service.registerProvider(prov1)

    result(service.providers.filter(_.contains(prov1)).head)

    service.unregisterProvider(prov1)

    result(service.providers.filter(_.isEmpty).head)

    service.registerProvider(prov1)
    service.registerProvider(prov2)

    result(service.providerSignal.filter(_.contains(prov2)).head)

  }

  scenario("Wait for provider") {

    val signal = service.permissions(ListSet("WriteExternal", "ReadExternal")).disableAutowiring()

    awaitAllTasks
    signal.currentValue("") shouldEqual None

    service.registerProvider(provider)

    result(signal.head) should have size 2
    result(signal.head) shouldEqual getSystemState
    checkCalls shouldEqual 1

  }

  scenario("Direct request call with provider") {

    val expectedPerms = ListSet(
      Permission("WriteExternal", granted = true),
      Permission("ReadExternal", granted = true)
    )

    service.registerProvider(provider)

    val req = service.requestPermissions(ListSet("WriteExternal", "ReadExternal"))

    userAccepts(expectedPerms)

    result(req) shouldEqual expectedPerms
    requestCalls shouldEqual 1
  }

  scenario("Unregistering permissions provider with current outstanding request should fail request and not block future requests") {
    val expectedPerms = ListSet(
      Permission("WriteExternal", granted = true),
      Permission("ReadExternal", granted = true)
    )

    service.registerProvider(provider)

    val req = service.requestPermissions(ListSet("WriteExternal", "ReadExternal"))

    await(requestInput.filter(_.map(_.key) == ListSet("WriteExternal", "ReadExternal")).head) //wait for request to be asked
    service.unregisterProvider(provider) //user minimises app
    result(req) shouldEqual ListSet.empty

    val provider2 = newProvider
    service.registerProvider(provider2)
    val req2 = service.requestPermissions(ListSet("WriteExternal", "ReadExternal"))

    userAccepts(expectedPerms)
    result(req2) shouldEqual expectedPerms
    requestCalls shouldEqual 2
  }

  scenario("Direct call without provider should return denied requests") {
    val perms = ListSet(
      Permission("WriteExternal", granted = true),
      Permission("ReadExternal",  granted = true)
    )

    systemState ! perms

    result(service.requestPermissions(ListSet("WriteExternal", "ReadExternal"))) shouldEqual perms.map(_.copy(granted = false))
    //call a second time to make sure previous request was cleared
    //TODO should be a separate test...
    result(service.requestPermissions(ListSet("WriteExternal", "ReadExternal"))) shouldEqual perms.map(_.copy(granted = false))
    requestCalls shouldEqual 0
  }

  scenario("Requesting for permissions eventually updates anywhere waiting on signals of those same permissions") {
    val expectedPerms = ListSet(
      Permission("WriteExternal", granted = true),
      Permission("ReadExternal", granted = true)
    )

    service.registerProvider(provider)

    val signal = service.permissions(ListSet("WriteExternal", "ReadExternal"))
    result(signal.head) shouldEqual getSystemState

    val req = service.requestPermissions(ListSet("WriteExternal", "ReadExternal"))

    userAccepts(expectedPerms)

    result(req) shouldEqual expectedPerms
    result(signal.filter { p =>
      println(s"Testing $p")
      p == expectedPerms
    }.head)

    requestCalls  shouldEqual 1
    checkCalls    shouldEqual 2
  }

  scenario("Only denied permissions are requested") {
    val expectedPerms = ListSet(
      Permission("WriteExternal", granted = true),
      Permission("ReadExternal",  granted = true)
    )

    systemState ! ListSet(
      Permission("WriteExternal"), //we should expect to see only this one requested and returned
      Permission("ReadExternal",  granted = true)
    )

    service.registerProvider(provider)
    val req = service.requestPermissions(ListSet("WriteExternal", "ReadExternal"))

    userAccepts(ListSet(Permission("WriteExternal", granted = true)))

    result(req) shouldEqual expectedPerms
    requestCalls shouldEqual 1
  }

  scenario("Two requests made simultaneously only call once to the PermissionsProvider at a time") {

    systemState ! ListSet(
      Permission("WriteExternal"),
      Permission("ReadExternal"),
      Permission("Location")
    )

    val expectedPerms1 = ListSet(
      Permission("WriteExternal", granted = true),
      Permission("ReadExternal", granted = true)
    )

    val location = ListSet(Permission("Location", granted = true))

    service.registerProvider(provider)

    val req = service.requestPermissions(ListSet("WriteExternal", "ReadExternal"))

    val req2 = service.requestPermissions(ListSet("Location"))

    userAccepts(expectedPerms1)
    requestCalls shouldEqual 1
    result(req) shouldEqual expectedPerms1

    userAccepts(location)
    result(req2) shouldEqual location
    requestCalls shouldEqual 2
  }

  //FIXME - currently there are small race conditions in the permissions signal and simultaneous requests
  ignore("Requesting same permissions twice simultaneously should only return once") {

    val expectedPerms = ListSet(
      Permission("WriteExternal", granted = true),
      Permission("ReadExternal", granted = true)
    )

    service.registerProvider(provider)

    val req = service.requestPermissions(ListSet("WriteExternal", "ReadExternal"))
    val req2 = service.requestPermissions(ListSet("WriteExternal", "ReadExternal"))

    userAccepts(expectedPerms)
    result(req) shouldEqual expectedPerms
    requestCalls shouldEqual 1

    result(req2) shouldEqual expectedPerms
    requestCalls shouldEqual 1
  }

  scenario("When all requests are already granted, complete request immediately") {
    systemState ! ListSet(
      Permission("WriteExternal", granted = true),
      Permission("ReadExternal", granted = true)
    )

    service.registerProvider(provider)

    val req = service.requestPermissions(ListSet("WriteExternal", "ReadExternal"))

    result(req) shouldEqual getSystemState
    requestCalls shouldEqual 0
  }

  scenario("Inlined getter returns false if no provider or permission is unknown to the service") {

    service.checkPermission("WriteExternal") shouldEqual false

    service.registerProvider(provider)

    service.checkPermission("WriteExternal") shouldEqual false

    val req = service.requestPermissions(ListSet("WriteExternal"))
    userAccepts(ListSet(Permission("WriteExternal", granted = true)))
    await(req)

    awaitAllTasks
    service.checkPermission("WriteExternal") shouldEqual true

    requestCalls shouldEqual 1
  }

  /**
    * user accepts/denies permissions: updates the "system" state, and then returns a response to our service on the UI thread
    */
  def userAccepts(toGrant: ListSet[Permission]) = {
    await(requestInput.filter(_.map(_.key) == toGrant.map(_.key)).head)
    println(s"User will accept: $toGrant")
    systemState.mutate(ps => ps.map(p => toGrant.find(_.key == p.key).getOrElse(p)))
    Threading.Ui(service.onPermissionsResult(toGrant))
    requestInput ! ListSet.empty
  }
}
