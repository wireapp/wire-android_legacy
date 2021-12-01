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
package com.waz.zclient.preferences.pages

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, Toast}
import androidx.fragment.app.{Fragment, FragmentTransaction}
import com.waz.content.GlobalPreferences.WsForegroundKey
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogsService
import com.waz.service.{GlobalModule, ZMessaging}
import com.wire.signals.CancellableFuture
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.preferences.dialogs.FullSyncDialog
import com.waz.zclient.preferences.views.{SwitchPreference, TextButton}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{BackStackKey, DebugUtils}
import com.waz.zclient.{BaseActivity, R, ViewHelper}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import com.waz.threading.Threading._

trait AdvancedView

class AdvancedViewImpl(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with AdvancedView
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_advanced_layout)

  private val logsService = inject[LogsService]
  private val initialLogsEnabled = logsService.logsEnabledGlobally.currentValue.getOrElse(false)

  val submitLogs = returning(findById[TextButton](R.id.preferences_debug_report)) { v =>
    setButtonEnabled(v, initialLogsEnabled)

    v.onClickEvent.onUi { _ =>
      DebugUtils.sendDebugReport(context.asInstanceOf[Activity])
    }
  }

  val loggingSwitch = returning(findById[SwitchPreference](R.id.preferences_enable_logs)) { v =>
    v.setChecked(initialLogsEnabled)

    v.onCheckedChange.onUi { enabled =>
      logsService.setLogsEnabled(enabled)
      setButtonEnabled(submitLogs, enabled)
    }
  }

  val resetPush = returning(findById[TextButton](R.id.preferences_reset_push)) { v =>
    v.onClickEvent.onUi { _ =>
      ZMessaging.currentGlobal.tokenService.resetGlobalToken()
      Toast.makeText(getContext, getString(R.string.pref_advanced_reset_push_completed)(getContext), Toast.LENGTH_LONG).show()
      setButtonEnabled(v, enabled = false)
      CancellableFuture.delay(5.seconds).map(_ => setButtonEnabled(v, enabled = true))(Threading.Ui)
    }
  }

  import com.waz.zclient.utils._

  val webSocketForegroundServiceSwitch = returning(findById[SwitchPreference](R.id.preferences_websocket_service)) { v =>
    v.setVisible(true)
    inject[GlobalModule].prefs(WsForegroundKey).signal.onUi(v.setChecked(_))
    v.setPreference(WsForegroundKey, global = true)
  }

  def setButtonEnabled(button: TextButton, enabled: Boolean): Unit = {
    button.setEnabled(enabled)
    button.setAlpha(if (enabled) 1f else .5f)
  }

  val slowSyncButton = returning(findById[TextButton](R.id.preferences_slow_sync)) { toggle =>
    toggle.onClickEvent.onUi { _ =>
      showPrefDialog(FullSyncDialog.newInstance, FullSyncDialog.Tag)
    }
  }

  private def showPrefDialog(f: Fragment, tag: String) = {
    context.asInstanceOf[BaseActivity]
      .getSupportFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(f, tag)
      .addToBackStack(tag)
      .commit
  }
}

case class AdvancedBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_advanced_screen_title

  override def layoutId = R.layout.preferences_advanced

  override def onViewAttached(v: View) = {}

  override def onViewDetached() = {}
}
