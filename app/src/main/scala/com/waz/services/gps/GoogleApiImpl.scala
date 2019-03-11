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
package com.waz.services.gps

import java.io.IOException

import android.app.Activity
import android.content.Context
import com.google.android.gms.common.ConnectionResult.{SERVICE_VERSION_UPDATE_REQUIRED, SUCCESS}
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.iid.FirebaseInstanceId
import com.waz.content.GlobalPreferences
import com.waz.content.GlobalPreferences.GPSErrorDialogShowCount
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.PushToken
import com.waz.service.BackendConfig
import com.waz.service.FirebaseOptions
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.utils.wrappers.GoogleApi
import com.waz.zclient.log.LogUI._

import scala.util.Try

class GoogleApiImpl private (context: Context, beConfig: BackendConfig, prefs: GlobalPreferences)
  extends GoogleApi with DerivedLogTag {

  import GoogleApiImpl._

  private val api = GoogleApiAvailability.getInstance()

  private val firebaseApp = initFirebase(context, beConfig.firebaseOptions)

  private def isGPSAvailable = Try(api.isGooglePlayServicesAvailable(context) == SUCCESS).toOption.contains(true)

  override val isGooglePlayServicesAvailable = Signal[Boolean](isGPSAvailable)

  override def checkGooglePlayServicesAvailable(activity: Activity) = api.isGooglePlayServicesAvailable(activity) match {
    case SUCCESS =>
      info(l"Google Play Services available")
      isGooglePlayServicesAvailable ! true
    case SERVICE_VERSION_UPDATE_REQUIRED if prefs.getFromPref(GPSErrorDialogShowCount) <= MaxErrorDialogShowCount =>
      info(l"Google Play Services requires update - prompting user")
      prefs.preference(GPSErrorDialogShowCount).mutate(_ + 1)
      api.getErrorDialog(activity, SERVICE_VERSION_UPDATE_REQUIRED, RequestGooglePlayServices).show()
    case code =>
      isGooglePlayServicesAvailable ! false
      warn(l"Google Play Services not available: error code: $code")
  }

  override def onActivityResult(requestCode: Int, resultCode: Int) = requestCode match {
    case RequestGooglePlayServices =>
      info(l"Google Play Services request completed, result: $resultCode")
      //It's quite natural for the user to click the back button after updating GPS, which results in a ACTIVITY_CANCELLED
      //result code. So just check if the services are available again on any result.
      isGooglePlayServicesAvailable ! isGPSAvailable
    case _ => //
  }

  @throws(classOf[IOException])
  override def getPushToken = withFcmInstanceId(id => PushToken(id.getToken(beConfig.pushSenderId, "FCM")))

  @throws(classOf[IOException])
  override def deleteAllPushTokens(): Unit = withFcmInstanceId(_.deleteInstanceId())

  private def withFcmInstanceId[A](f: FirebaseInstanceId => A): A = firebaseApp.fold(throw new IllegalStateException("No Firebase app instance available"))(app => f(FirebaseInstanceId.getInstance(app)))
}

object GoogleApiImpl {
  val RequestGooglePlayServices = 7976
  val MaxErrorDialogShowCount = 3

  private[GoogleApiImpl] def initFirebase(context: Context, options: FirebaseOptions) = Try {
    FirebaseApp.initializeApp(context, new com.google.firebase.FirebaseOptions.Builder()
      .setApplicationId(options.appId)
      .setApiKey(options.apiKey)
      .setGcmSenderId(options.pushSenderId)
      .build())
  }.toOption

  private var instance = Option.empty[GoogleApiImpl]

  def apply(context: Context, beConfig: BackendConfig, prefs: GlobalPreferences): GoogleApiImpl = synchronized {
    instance match {
      case Some(api) => api
      case None => returning(new GoogleApiImpl(context, beConfig, prefs)){ api: GoogleApiImpl => instance = Some(api) }
    }
  }

}
