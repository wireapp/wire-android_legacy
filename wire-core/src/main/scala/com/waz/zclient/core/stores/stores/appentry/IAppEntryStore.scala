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
package com.waz.zclient.core.stores.appentry

import android.os.Bundle
import com.waz.api.{AccentColor, ImageAsset, Invitations, Self}
import com.waz.zclient.core.controllers.tracking.attributes.RegistrationEventContext
import com.waz.zclient.core.stores.IStore

trait IAppEntryStore extends IStore {
  def onBackPressed: Boolean

  def tearDown(): Unit

  def setState(state: AppEntryState): Unit

  def triggerStateUpdate(): Unit

  def setCallback(callback: AppEntryStateCallback): Unit

  def onSaveInstanceState(outState: Bundle): Unit

  def onRestoreInstanceState(savedInstanceState: Bundle, self: Self): Unit

  def getEntryPoint: AppEntryState

  def resumeAppEntry(self: Self, personalInvitationToken: String)

  def clearCurrentState(): Unit

  def clearSavedUserInput(): Unit

  def setRegistrationPhone(countryCode: String, phone: String, errorCallback: IAppEntryStore.ErrorCallback): Unit

  def setSignInPhone(countryCode: String, phone: String, errorCallback: IAppEntryStore.ErrorCallback): Unit

  def submitCode(phoneVerificationCode: String, errorCallback: IAppEntryStore.ErrorCallback): Unit

  def registerWithPhone(name: String, accentColor: AccentColor, errorCallback: IAppEntryStore.ErrorCallback): Unit

  def setPhonePicture(imageAsset: ImageAsset): Unit

  def addEmailAndPasswordToPhone(email: String, password: String, emailErrorCallback: IAppEntryStore.ErrorCallback, passwordErrorCallback: IAppEntryStore.ErrorCallback): Unit

  def setEmailPicture(imageAsset: ImageAsset): Unit

  def registerWithEmail(email: String, password: String, name: String, accentColor: AccentColor, errorCallback: IAppEntryStore.ErrorCallback): Unit

  def acceptEmailInvitation(password: String, accentColor: AccentColor): Unit

  def acceptPhoneInvitation(accentColor: AccentColor): Unit

  def signInWithEmail(email: String, password: String, errorCallback: IAppEntryStore.ErrorCallback): Unit

  def resendEmail(): Unit

  def resendPhone(successCallback: IAppEntryStore.SuccessCallback, errorCallback: IAppEntryStore.ErrorCallback): Unit

  def triggerVerificationCodeCallToUser(successCallback: IAppEntryStore.SuccessCallback, errorCallback: IAppEntryStore.ErrorCallback): Unit

  def getCountryCode: String

  def getPhone: String

  def getEmail: String

  def getPassword: String

  def getName: String

  def getUserId: String

  def getInvitationEmail: String

  def getInvitationPhone: String

  def getInvitationName: String

  def getInvitationToken: Invitations.PersonalToken

  def setRegistrationContext(registrationEventContext: RegistrationEventContext): Unit

  def getPhoneRegistrationContext: RegistrationEventContext

  def getEmailRegistrationContext: RegistrationEventContext
}


object IAppEntryStore {

  trait ErrorCallback {
    def onError(error: AppEntryError): Unit
  }

  trait SuccessCallback {
    def onSuccess(): Unit
  }

}
