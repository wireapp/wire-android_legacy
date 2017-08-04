/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.core.stores.profile

import com.waz.api.{Self, UpdateListener, User}

import scala.collection.mutable

abstract class ProfileStore(protected var self: Option[Self]) extends IProfileStore with UpdateListener {

  // observers
  protected val profileStoreObservers = mutable.Set[ProfileStoreObserver]()

  // is first launch
  private var _firstLaunch = false

  override def firstLaunch: Boolean = _firstLaunch

  /* add an observer to this store */
  def addProfileStoreObserver(profileStoreObserver: ProfileStoreObserver) = profileStoreObservers.add(profileStoreObserver)

  override def addProfileStoreAndUpdateObserver(profileStoreObserver: ProfileStoreObserver): Unit = {
    profileStoreObservers.add(profileStoreObserver)
    self.foreach { self =>
      profileStoreObserver.onMyNameHasChanged(this, self.getName)
      profileStoreObserver.onMyEmailHasChanged(self.getEmail, self.isEmailVerified)
      profileStoreObserver.onMyPhoneHasChanged(self.getPhone, self.isPhoneVerified)
      profileStoreObserver.onAccentColorChangedRemotely(this, self.getAccent.getColor)
      profileStoreObserver.onMyUsernameHasChanged(self.getUsername)
    }
  }

  override def setUser(self: Option[Self]): Unit = {
    this.self.foreach { _.removeUpdateListener(this) }
    this.self = self
    this.self.foreach { s =>
      s.addUpdateListener(this)
      updated()
    }
  }

  override def selfUser: Option[User] = self.map(_.getUser)

  /* remove an observer from this store */
  def removeProfileStoreObserver(profileStoreObserver: ProfileStoreObserver) = profileStoreObservers.remove(profileStoreObserver)

  override def setFirstLaunch(firstLaunch: Boolean): Unit = this._firstLaunch = firstLaunch

  protected def notifyMyColorHasChanged(sender: Any, color: Int) =
    profileStoreObservers.foreach { _.onAccentColorChangedRemotely(sender, color) }

  protected def notifyNameHasChanged(sender: Any, myName: String) =
    profileStoreObservers.foreach { _.onMyNameHasChanged(sender, myName) }

  protected def notifyEmailHasChanged(myEmail: String, isVerified: Boolean) =
    profileStoreObservers.foreach { _.onMyEmailHasChanged(myEmail, isVerified) }

  protected def notifyPhoneHasChanged(myPhone: String, isVerified: Boolean) =
    profileStoreObservers.foreach { _.onMyPhoneHasChanged(myPhone, isVerified) }

  protected def notifyEmailAndPasswordHasChanged(email: String) =
    profileStoreObservers.foreach { _.onMyEmailAndPasswordHasChanged(email) }

  protected def notifyUsernameHasChanged(myUsername: String) =
    profileStoreObservers.foreach { _.onMyUsernameHasChanged(myUsername) }

  override def hasIncomingDevices: Boolean = self.map(_.getIncomingOtrClients.size > 0).getOrElse(false)
}
