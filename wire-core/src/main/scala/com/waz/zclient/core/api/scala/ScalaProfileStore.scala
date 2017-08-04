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
package com.waz.zclient.core.api.scala

import com.waz.api._
import com.waz.api.impl.AccentColors
import com.waz.zclient.core.stores.appentry.AppEntryError
import com.waz.zclient.core.stores.profile.ProfileStore

class ScalaProfileStore(zMessagingApi: ZMessagingApi) extends ProfileStore(Option(zMessagingApi.getSelf)) {
  private val accentColors = AccentColors.getColors

  private var _myName: String = self.map(_.getName).getOrElse("")
  private var _myEmail: String = self.map(_.getEmail).getOrElse("")
  private var _myPhoneNumber: String = self.map(_.getPhone).getOrElse("")
  private var _myUsername: String = self.map(_.getUsername).getOrElse("")
  private var _myColor: Int = if (accentColors.nonEmpty) accentColors(0).getColor() else 0
  private var _isEmailVerified: Boolean = self.map(_.isEmailVerified).getOrElse(false)
  private var _isPhoneVerified: Boolean = self.map(_.isPhoneVerified).getOrElse(false)

  override def tearDown() = self.foreach { _.removeUpdateListener(this) }

  override def myName: String = _myName

  override def setMyName(myName: String): Unit = if (_myName != myName) self.foreach(_.setName(myName))

  override def myEmail: String = _myEmail

  override def myPhoneNumber: String = self.map(_.getPhone).getOrElse("")

  override def setMyPhoneNumber(myPhone: String, listener: CredentialsUpdateListener): Unit = self.foreach(_.setPhone(myPhone, listener))

  override def deleteMyPhoneNumber(listener: CredentialsUpdateListener): Unit = self.foreach(_.clearPhone(listener))

  override def setMyEmail(email: String, listener: CredentialsUpdateListener): Unit = self.foreach(_.setEmail(email, listener))

  override def deleteMyEmail(listener: CredentialsUpdateListener): Unit = self.foreach(_.clearEmail(listener))

  override def setMyEmailAndPassword(email: String, password: String, listener: CredentialsUpdateListener): Unit = self.foreach { s =>
    s.setPassword(password, new CredentialsUpdateListener() {
      override def onUpdated(): Unit = s.setEmail(email, listener)

      override def onUpdateFailed(errorCode: Int, message: String, label: String): Unit =
        if (errorCode == AppEntryError.FORBIDDEN.errorCode) s.setEmail(email, listener) // Ignore error when password is already set
        else listener.onUpdateFailed(errorCode, message, label)
    })
  }

  override def resendVerificationEmail(myEmail: String): Unit = self.foreach { _.resendVerificationEmail(myEmail) }

  override def resendPhoneVerificationCode(myPhoneNumber: String, listener: ZMessagingApi.PhoneConfirmationCodeRequestListener): Unit =
    zMessagingApi.requestPhoneConfirmationCode(myPhoneNumber, KindOfAccess.REGISTRATION, listener)

  override def getAccentColor: Int = self.map(_.getAccent.getColor).getOrElse(0)

  override def setAccentColor(sender: Any, myColor: Int): Unit = {
    _myColor = myColor

    if (accentColors.nonEmpty) self.foreach(_.setAccent(
      accentColors.find(_.getColor == myColor).getOrElse(accentColors(0))
    ))
  }

  override def setUserPicture(imageAsset: ImageAsset): Unit = self.foreach(_.setPicture(imageAsset))

  override def hasProfileImage: Boolean = self.exists(!_.getPicture.isEmpty)

  override def isEmailVerified: Boolean = self.exists(_.isEmailVerified)

  override def isPhoneVerified: Boolean = self.exists(_.isPhoneVerified)

  override def addEmailAndPassword(email: String, password: String, listener: CredentialsUpdateListener): Unit = self.foreach { s =>
    s.setPassword(password, new CredentialsUpdateListener() {
      override def onUpdated(): Unit = s.setEmail(email, listener)

      override def onUpdateFailed(errorCode: Int, message: String, label: String): Unit =
        // Edge case where password was set on another device while email/pw were being added on this one.
        if (errorCode == AppEntryError.FORBIDDEN.errorCode) s.setEmail(email, listener)
        else listener.onUpdateFailed(AppEntryError.PHONE_ADD_PASSWORD.errorCode, "", AppEntryError.PHONE_ADD_PASSWORD.label)
    })
  }

  override def submitCode(myPhoneNumber: String, code: String, listener: ZMessagingApi.PhoneNumberVerificationListener): Unit =
    zMessagingApi.verifyPhoneNumber(myPhoneNumber, code, KindOfVerification.VERIFY_ON_UPDATE, listener)

  /**
    * User has been updated in core.
    */
  override def updated(): Unit = self.foreach { s =>
    if (s.getName != _myName) {
      _myName = s.getName
      notifyNameHasChanged(this, _myName)
    }

    if (s.getUsername != _myUsername) {
      _myUsername = s.getUsername
      notifyUsernameHasChanged(_myUsername)
    }

    if (s.getEmail != myEmail || s.isEmailVerified() != _isEmailVerified) {
      _myEmail = s.getEmail
      _isEmailVerified = s.isEmailVerified
      notifyEmailHasChanged(_myEmail, _isEmailVerified)
    }

    if (s.getPhone != _myPhoneNumber || s.isPhoneVerified != _isPhoneVerified) {
      _myPhoneNumber = s.getPhone
      _isPhoneVerified = s.isPhoneVerified
      notifyPhoneHasChanged(myPhoneNumber, _isPhoneVerified)
    }

    if (s.getAccent.getColor != _myColor) {
      _myColor = s.getAccent.getColor
      notifyMyColorHasChanged(this, _myColor)
    }
  }
}

object ScalaProfileStore {
  val TAG = classOf[ScalaProfileStore].getName
}

