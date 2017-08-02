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

import com.waz.api._
import com.waz.zclient.core.stores.IStore

trait IProfileStore extends IStore {
  /* add an observer to this store */
  def addProfileStoreObserver(profileStoreObserver: ProfileStoreObserver)

  def addProfileStoreAndUpdateObserver(profileStoreObserver: ProfileStoreObserver)

  /* remove an observer from this store */
  def removeProfileStoreObserver(profileStoreObserver: ProfileStoreObserver)

  def setUser(selfUser: Option[Self])

  /*  The name of the user */
  def myName: String

  def setMyName(myName: String)

  /*  The email of the user */
  def myEmail: String

  /*  The phone numer of the user */
  def myPhoneNumber: String

  def isEmailVerified: Boolean

  def isPhoneVerified: Boolean

  def hasIncomingDevices: Boolean

  def setMyPhoneNumber(phone: String, credentialsUpdateListener: CredentialsUpdateListener)

  def deleteMyPhoneNumber(credentialsUpdateListener: CredentialsUpdateListener)

  def setMyEmail(email: String, credentialsUpdateListener: CredentialsUpdateListener)

  def deleteMyEmail(credentialsUpdateListener: CredentialsUpdateListener)

  def setMyEmailAndPassword(email: String, password: String, credentialsUpdateListener: CredentialsUpdateListener)

  def resendVerificationEmail(myEmail: String)

  def resendPhoneVerificationCode(myPhoneNumber: String, confirmationListener: ZMessagingApi.PhoneConfirmationCodeRequestListener)

  def selfUser: Option[User] // for Scala
  def getSelfUser: User = selfUser.getOrElse(null) // for Java

  /* the color chosen by the user */
  def getAccentColor: Int

  /*  if the user chose a new color */
  def setAccentColor(sender: Any, color: Int)

  /*  indicates if it app is launched for the first time */
  def firstLaunch: Boolean

  /* notifies self store that app is launched for the very first time */
  def setFirstLaunch(firstLaunch: Boolean)

  def setUserPicture(imageAsset: ImageAsset)

  def hasProfileImage: Boolean

  def addEmailAndPassword(email: String, password: String, credentialUpdateListener: CredentialsUpdateListener)

  def submitCode(myPhoneNumber: String, code: String, verificationListener: ZMessagingApi.PhoneNumberVerificationListener)
}
