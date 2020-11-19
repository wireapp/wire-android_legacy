package com.waz.zclient.preferences.pages

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.content.GlobalPreferences.AppLockEnabled
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.PasswordController
import com.waz.zclient.preferences.views.{SwitchPreference, TextButton}
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.{BuildConfig, R, ViewHelper}
import com.waz.zclient.utils._
import com.wire.signals.Signal

class AppLockView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style) with ViewHelper {
  import Threading.Implicits.Ui

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)
  inflate(R.layout.app_lock_layout)

  private val passwordController = inject[PasswordController]

  private val appLockSwitch = returning(findById[SwitchPreference](R.id.preferences_app_lock_switch)) { appLock =>
    appLock.setPreference(AppLockEnabled, global = true)
    appLock.setSubtitle(getString(R.string.pref_options_app_lock_summary, BuildConfig.APP_LOCK_TIMEOUT.toString))
  }

  appLockSwitch.setDisabled(BuildConfig.FORCE_APP_LOCK)

  private val appLockChangeButton = returning(findById[TextButton](R.id.preferences_app_lock_change_button)) { button =>
    button.onClickEvent.foreach { _ => passwordController.changeSSOPassword() }
  }

  Signal.zip(passwordController.ssoEnabled, passwordController.appLockEnabled).foreach {
    case (true, true) =>
      appLockChangeButton.setVisible(true)
      passwordController.setSSOPasswordIfNeeded()
    case _ =>
      appLockChangeButton.setVisible(false)
  }
}

case class AppLockKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_options_app_lock

  override def layoutId: Int = R.layout.preferences_app_lock

  override def onViewAttached(v: View): Unit = {}

  override def onViewDetached(): Unit = {}
}

