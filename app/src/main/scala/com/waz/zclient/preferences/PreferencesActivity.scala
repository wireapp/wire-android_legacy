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
package com.waz.zclient.preferences

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.{Context, Intent}
import android.os.Bundle
import android.support.annotation.Nullable
import android.support.v4.app.{Fragment, FragmentManager, FragmentTransaction}
import android.support.v4.widget.TextViewCompat
import android.support.v7.preference.{Preference, PreferenceFragmentCompat, PreferenceScreen}
import android.support.v7.widget.{AppCompatTextView, Toolbar}
import android.view.{MenuItem, View}
import android.widget.{TextSwitcher, TextView, Toast, ViewSwitcher}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.ImageAsset
import com.waz.content.GlobalPreferences
import com.waz.content.GlobalPreferences.CurrentAccountPref
import com.waz.service.ZMessaging
import com.waz.sync.queue.SyncContentUpdater.Add
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.controllers.accentcolor.AccentColorChangeRequester
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedProfilePictureEvent
import com.waz.zclient.pages.main.profile.camera.{CameraContext, CameraFragment}
import com.waz.zclient.pages.main.profile.preferences._
import com.waz.zclient.pages.main.profile.preferences.dialogs.WireRingtonePreferenceDialogFragment
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.utils.{LayoutSpec, ViewUtils}
import com.waz.zclient.{ActivityHelper, BaseActivity, MainActivity, R}

class PreferencesActivity extends BaseActivity
  with ActivityHelper
  with CameraFragment.Container
  with PreferenceFragmentCompat.OnPreferenceStartScreenCallback
  with PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback
  with PreferenceScreenStrategy.ReplaceFragment.Callbacks {

  import PreferencesActivity._

  private lazy val replaceFragmentStrategy = new PreferenceScreenStrategy.ReplaceFragment(this, R.anim.abc_fade_in, R.anim.abc_fade_out, R.anim.abc_fade_in, R.anim.abc_fade_out)
  private lazy val toolbar: Toolbar        = findById(R.id.toolbar)

  private lazy val actionBar = returning(getSupportActionBar) { ab =>
    ab.setDisplayHomeAsUpEnabled(true)
    ab.setDisplayShowCustomEnabled(true)
    ab.setDisplayShowTitleEnabled(false)
  }

  private lazy val titleSwitcher = returning(new TextSwitcher(toolbar.getContext)) { ts =>
    ts.setFactory(new ViewSwitcher.ViewFactory() {
      def makeView: View = {
        val tv: TextView = new AppCompatTextView(toolbar.getContext)
        TextViewCompat.setTextAppearance(tv, R.style.TextAppearance_AppCompat_Widget_ActionBar_Title)
        tv
      }
    })
    ts.setCurrentText(getTitle)
    actionBar.setCustomView(ts)
    ts.setInAnimation(this, R.anim.abc_fade_in)
    ts.setOutAnimation(this, R.anim.abc_fade_out)
  }

  lazy val currentAccountPref = inject[GlobalPreferences].preference(CurrentAccountPref)
  lazy val accentColor = inject[AccentColorController].accentColor

  @SuppressLint(Array("PrivateResource"))
  override def onCreate(@Nullable savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_settings)
    setSupportActionBar(toolbar)
    titleSwitcher //initialise title switcher

    if (LayoutSpec.isPhone(this)) ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this)
    if (savedInstanceState == null) getSupportFragmentManager.beginTransaction.add(R.id.content, RootPreferences.newInstance(null, getIntent.getExtras), RootPreferences.TAG).commit

    currentAccountPref.signal.onChanged { _ =>
      startActivity(returning(new Intent(this, classOf[MainActivity]))(_.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)))
    }

    accentColor.on(Threading.Ui) { color =>
      getControllerFactory.getUserPreferencesController.setLastAccentColor(color.getColor())
      getControllerFactory.getAccentColorController.setColor(AccentColorChangeRequester.REMOTE, color.getColor())
    }
  }

  override def onStart(): Unit = {
    super.onStart()
    getControllerFactory.getCameraController.addCameraActionObserver(this)
  }

  override def onStop(): Unit = {
    super.onStop()
    getControllerFactory.getCameraController.removeCameraActionObserver(this)
  }

  override protected def onTitleChanged(title: CharSequence, color: Int) = {
    super.onTitleChanged(title, color)
    titleSwitcher.setText(title)
  }

  override def getBaseTheme: Int = R.style.Theme_Dark_Preferences

  override def onPreferenceStartScreen(preferenceFragmentCompat: PreferenceFragmentCompat, preferenceScreen: PreferenceScreen): Boolean = {
    replaceFragmentStrategy.onPreferenceStartScreen(getSupportFragmentManager, preferenceFragmentCompat, preferenceScreen)
    true
  }

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    super.onActivityResult(requestCode, resultCode, data)
    val fragment: Fragment = getSupportFragmentManager.findFragmentById(R.id.fl__root__camera)
    if (fragment != null) fragment.onActivityResult(requestCode, resultCode, data)
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case android.R.id.home =>
        onBackPressed()
      case _ =>
    }
    super.onOptionsItemSelected(item)
  }

  override def onPreferenceDisplayDialog(preferenceFragmentCompat: PreferenceFragmentCompat, preference: Preference): Boolean = {
    val key = preference.getKey
    if (Set(
      R.string.pref_options_ringtones_ping_key,
      R.string.pref_options_ringtones_text_key,
      R.string.pref_options_ringtones_ringtone_key
    ).map(getString).contains(key)) {
      returning(WireRingtonePreferenceDialogFragment.newInstance(key, preference.getExtras.getInt(WireRingtonePreferenceDialogFragment.EXTRA_DEFAULT))) { f =>
        f.setTargetFragment(preferenceFragmentCompat, 0)
        f.show(getSupportFragmentManager, key)
      }
      true
    } else false
  }

  override def onBuildPreferenceFragment(preferenceScreen: PreferenceScreen): PreferenceFragmentCompat = {
    val rootKey = preferenceScreen.getKey
    val extras  = preferenceScreen.getExtras
    val instance = rootKey match {
      case k if k == getString(R.string.pref_account_screen_key)        => AccountPreferences.newInstance(rootKey, extras)
      case k if k == getString(R.string.pref_about_screen_key)          => AboutPreferences.newInstance(rootKey, extras)
      case k if k == getString(R.string.pref_options_screen_key)        => OptionsPreferences.newInstance(rootKey, extras)
      case k if k == getString(R.string.pref_support_screen_key)        => SupportPreferences.newInstance(rootKey, extras)
      case k if k == getString(R.string.pref_advanced_screen_key)       => AdvancedPreferences.newInstance(rootKey, extras)
      case k if k == getString(R.string.pref_developer_screen_key)      => DeveloperPreferenceFragment.newInstance(rootKey, extras)
      case k if k == getString(R.string.pref_devices_screen_key)        => DevicesPreferences.newInstance(rootKey, extras)
      case k if k == getString(R.string.pref_device_details_screen_key) => DeviceDetailPreferences.newInstance(rootKey, extras)
      case _                                                            => RootPreferences.newInstance(rootKey, extras)
    }
    resetIntentExtras(preferenceScreen)
    instance
  }

  private def resetIntentExtras(preferenceScreen: PreferenceScreen) =
    Seq(ShowOtrDevices, ShowAccount, ShowUsernameEdit).foreach(preferenceScreen.getExtras.remove)

  //TODO do we need to check internet connectivity here?
  override def onBitmapSelected(imageAsset: ImageAsset, imageFromCamera: Boolean, cameraContext: CameraContext) =
    if (cameraContext == CameraContext.SETTINGS) {
      inject[Signal[ZMessaging]].head.map { zms =>
        zms.users.updateSelfPicture(imageAsset)
        inject[GlobalTrackingController].tagEvent(new ChangedProfilePictureEvent)
      } (Threading.Background)
      getSupportFragmentManager.popBackStack(CameraFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

  override def onCameraNotAvailable() =
    Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()

  override def onOpenCamera(cameraContext: CameraContext) = {
    Option(getSupportFragmentManager.findFragmentByTag(CameraFragment.TAG)) match {
      case None =>
        getSupportFragmentManager
          .beginTransaction
          .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
          .add(R.id.fl__root__camera, CameraFragment.newInstance(cameraContext), CameraFragment.TAG)
          .addToBackStack(CameraFragment.TAG)
          .commit
      case Some(_) => //do nothing
    }
  }

  def onCloseCamera(cameraContext: CameraContext) =
    getSupportFragmentManager.popBackStack(CameraFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)

}

object PreferencesActivity {
  val ShowOtrDevices   = "SHOW_OTR_DEVICES"
  val ShowAccount      = "SHOW_ACCOUNT"
  val ShowUsernameEdit = "SHOW_USERNAME_EDIT"

  def getDefaultIntent(context: Context): Intent =
    new Intent(context, classOf[PreferencesActivity])

  def getOtrDevicesPreferencesIntent(context: Context): Intent =
    returning(getDefaultIntent(context))(_.putExtra(ShowOtrDevices, true))

  def getUsernameEditPreferencesIntent(context: Context): Intent =
    returning(getDefaultIntent(context))(_.putExtra(ShowUsernameEdit, true))
}
