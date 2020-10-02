/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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

package com.waz.zclient.preferences.pages

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.content.GlobalPreferences
import com.waz.content.UserPreferences.TrackingEnabled
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.AccountManager
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.utils.returning
import com.waz.zclient.preferences.views.SwitchPreference
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.utils.{BackStackKey, ContextUtils}
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.BuildConfig
import com.waz.threading.Threading._
import com.waz.zclient.common.controllers.UserAccountsController

class DataUsagePermissionsView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import Threading.Implicits.Ui
  inflate(R.layout.data_usage_permissions_layout)

  private val am       = inject[Signal[AccountManager]]
  private lazy val uac      = inject[UserAccountsController]
  private val tracking = inject[GlobalTrackingController]

  val sendAnonymousUsageDataButton = returning(findById[SwitchPreference](R.id.preferences_send_anonymous_usage_data)) { v =>
    uac.isProUser.head.foreach { isProUser =>
      v.setPreference(TrackingEnabled)
      if (isProUser) {
        v.pref.flatMap(_.signal).onUi {
          case true  => tracking.optIn()
          case false => tracking.optOut()
        }
      } else {
        v.setVisibility(View.GONE)
      }
    }
  }

  val submitCrashReportsButton = returning(findById[SwitchPreference](R.id.preferences_send_anonymous_crash_report_data)) { v =>
    if (BuildConfig.SUBMIT_CRASH_REPORTS) {
      v.setPreference(GlobalPreferences.SendAnonymousDataEnabled, global = true)
    } else {
      v.setVisibility(View.GONE)
    }
  }

  val receiveNewsAndOffersSwitch = returning(findById[SwitchPreference](R.id.preferences_receive_news_and_offers)) { v =>

    if (BuildConfig.ALLOW_MARKETING_COMMUNICATION) {

      def setReceivingNewsAndOffersSwitchEnabled(enabled: Boolean) = {
        v.setEnabled(enabled)
        v.setAlpha(if (enabled) 1.0f else 0.5f)
      }

      setReceivingNewsAndOffersSwitchEnabled(false)
      am.head.flatMap(_.hasMarketingConsent).foreach { isSet =>
        v.setChecked(isSet, disableListener = true)
        setReceivingNewsAndOffersSwitchEnabled(true)

        v.onCheckedChange.onUi { set =>
          setReceivingNewsAndOffersSwitchEnabled(false)
          am.head.flatMap(_.setMarketingConsent(Some(set))).foreach { resp =>
            if (resp.isLeft) {
              v.setChecked(!set, disableListener = true) //set switch back to whatever it was
              ContextUtils.showGenericErrorDialog()
            }
            setReceivingNewsAndOffersSwitchEnabled(true)
          }
        }
      }
    } else {
      v.setVisibility(View.GONE)
    }
  }

}

case class DataUsagePermissionsKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_account_data_usage_permissions

  override def layoutId = R.layout.preferences_data_usage_permissions

  override def onViewAttached(v: View) = {}

  override def onViewDetached() = {}
}
