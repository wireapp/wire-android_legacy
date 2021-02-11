package com.waz.zclient.preferences.pages

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.content.UserPreferences.AppLockEnabled
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.PasswordController
import com.waz.zclient.preferences.views.{SwitchPreference, TextButton}
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.{BuildConfig, R, ViewHelper}
import com.waz.zclient.utils._
import com.waz.zclient.log.LogUI._

class AppLockView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style) with ViewHelper with DerivedLogTag {
  import Threading.Implicits.Ui

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)
  inflate(R.layout.app_lock_layout)

  private val passwordController = inject[PasswordController]

  private val appLockSwitch = returning(findById[SwitchPreference](R.id.preferences_app_lock_switch)) { appLock =>
    appLock.setPreference(AppLockEnabled)
    passwordController.appLockTimeout.foreach { timeout =>
      appLock.setSubtitle(getString(R.string.pref_options_app_lock_summary, timeout.toString))
    }
  }

  passwordController.appLockForced.map {
    case true  => true
    case false => BuildConfig.FORCE_APP_LOCK
  }.foreach(appLockSwitch.setDisabled)

  private val appLockChangeButton = returning(findById[TextButton](R.id.preferences_app_lock_change_button)) { button =>
    button.onClickEvent.foreach { _ => passwordController.changeCustomPassword() }
  }

  passwordController.appLockEnabled.foreach {
    case true =>
      verbose(l"app lock enabled")
      passwordController.setCustomPasswordIfNeeded()
      appLockChangeButton.setVisible(true)
    case false =>
      verbose(l"app lock disabled")
      passwordController.clearCustomPassword()
      appLockChangeButton.setVisible(false)
  }
}

case class AppLockKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_options_app_lock

  override def layoutId: Int = R.layout.preferences_app_lock

  override def onViewAttached(v: View): Unit = {}

  override def onViewDetached(): Unit = {}
}

