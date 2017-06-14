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

import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import android.support.v4.app.{Fragment, FragmentTransaction}
import android.support.v7.preference.Preference
import android.support.v7.preference.Preference.{OnPreferenceChangeListener, OnPreferenceClickListener}
import android.support.v7.preference.PreferenceFragmentCompat._
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import com.waz.api.impl.AccentColor
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.controllers.tracking.events.profile.{ResetPassword, SignOut}
import com.waz.zclient.core.controllers.tracking.events.session.LoggedOutEvent
import com.waz.zclient.core.controllers.tracking.events.settings.EditedUsernameEvent
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.pages.main.profile.preferences.ColorPreference
import com.waz.zclient.pages.main.profile.preferences.dialogs._
import com.waz.zclient.preferences.PreferencesActivity.ShowUsernameEdit
import com.waz.zclient.preferences.dialogs.{AccentColorPickerFragment, ChangeHandleFragment, PicturePreference}
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.ui.utils.TextViewUtils.getBoldText
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.StringUtils
import com.waz.zclient.utils.ViewUtils.showAlertDialog
import net.xpece.android.support.preference.EditTextPreference
import net.xpece.android.support.preference.EditTextPreference.OnEditTextCreatedListener

class AccountPreferences extends BasePreferenceFragment
  with AddEmailAndPasswordPreferenceDialogFragment.Container
  with ChangeEmailPreferenceDialogFragment.Container
  with AddPhoneNumberPreferenceDialogFragment.Container {

  import Threading.Implicits.Background

  implicit lazy val context = getContext

  lazy val zms      = inject[Signal[ZMessaging]]
  lazy val tracking = inject[GlobalTrackingController]

  lazy val selfUser = zms.flatMap(_.users.selfUser)
  lazy val selfAcc  = zms.map(_.account)

  lazy val emailPref = returning(findPref[Preference](R.string.pref_account_email_key)) { p =>
    p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
      def onPreferenceClick(preference: Preference): Boolean = {
        Option(preference.getTitle).map(_.toString).filter(_.nonEmpty) match {
          case Some(email) if email != getString(R.string.pref_account_add_email_title) => changeEmail(email)
          case _ => addEmailAndPassword()
        }
        true
      }
    })

    (for {
      email    <- selfUser.map(_.email.map(_.str).filter(_.nonEmpty))
      verified <- selfAcc.map(_.account.verified)
    } yield (email, verified)).on(Threading.Ui) {
      case (Some(e), isVerified) =>
        if (isVerified) getChildFragmentManager.popBackStack(VerifyEmailPreferenceFragment.TAG, POP_BACK_STACK_INCLUSIVE)
        p.setTitle(e)
        p.setSummary(R.string.pref_account_email_title)
      case _ =>
        p.setTitle(R.string.pref_account_add_email_title)
        p.setSummary("")
    }
  }

  lazy val phonePref = returning(findPref[Preference](R.string.pref_account_phone_key)) { p =>
    p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
      def onPreferenceClick(preference: Preference): Boolean = {
        Option(preference.getTitle).map(_.toString).filter(_.nonEmpty) match {
          case Some(ph) if ph != getString(R.string.pref_account_add_phone_title) => changePhoneNumber(ph)
          case _ => addPhoneNumber()
        }
        true
      }
    })

    selfUser.map(_.phone.map(_.str).filter(_.nonEmpty)).on(Threading.Ui) {
      case Some(ph) =>
        p.setTitle(ph)
        p.setSummary(R.string.pref_account_phone_title)
      case _ =>
        p.setTitle(R.string.pref_account_add_phone_title)
        p.setSummary("")
    }
  }

  lazy val prefs = Seq(
    //Name
    returning(findPref[EditTextPreference](R.string.pref_account_name_key)) { p =>
      p.setOnEditTextCreatedListener(new OnEditTextCreatedListener() {
        def onEditTextCreated(edit: EditText) = {
          //Having it directly on the xml doesn't seem to work for EditTextPreference
          edit.setSingleLine(true)
          edit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            def onFocusChange(view: View, b: Boolean) = edit.setSelection(edit.getText.length)
          })
        }
      })
      p.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
        def onPreferenceChange(preference: Preference, newValue: Any): Boolean = {
          val newName = newValue.asInstanceOf[String].trim
          if (TextUtils.getTrimmedLength(newName) < getInt(R.integer.account_preferences__min_name_length)) {
            showAlertDialog(
              getActivity, null,
              getString(R.string.pref_account_edit_name_empty_warning),
              getString(R.string.pref_account_edit_name_empty_verify),
              new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, which: Int) = dialog.dismiss()
              }, false)
            false
          } else {
            for {
              z <- zms.head
              _ <- z.users.updateSelf(name = Some(newName))
            } yield {}
            false
          }
        }
      })
      selfUser.map(_.name).on(Threading.Ui) { name =>
        p.setTitle(name)
        p.setText(name)
        p.setSummary(getString(R.string.pref_account_name_title))
      }
    },

    //Username
    returning(findPref[Preference](R.string.pref_account_username_key)) { p =>
      p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
        def onPreferenceClick(preference: Preference): Boolean = {
          selfUser.map(_.handle).head.map {
            case Some(h) => changeHandle(h.string, cancellable = true)
            case None =>
          } (Threading.Ui)
          true
        }
      })
      selfUser.map(_.handle).on(Threading.Ui) {
        case Some(h) =>
          p.setTitle(StringUtils.formatHandle(h.string))
          p.setSummary(getString(R.string.pref_account_username_title))
        case None =>
          p.setTitle(getString(R.string.pref_account_username_empty_title))
          p.setSummary("")
      }
    },

    //Reset password
    returning(findPref[Preference](R.string.pref_account_password_key)) { p =>
      p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
        def onPreferenceClick(preference: Preference): Boolean = {
          tracking.tagEvent(new ResetPassword(ResetPassword.Location.FROM_PROFILE))
          false
        }
      })
    },

    //Sign out
    returning(findPref[Preference](R.string.pref_account_sign_out_key)) { p =>
      p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
        def onPreferenceClick(preference: Preference): Boolean = {
          signOut()
          true
        }
      })
    },

    //Delete account
    returning(findPref[Preference](R.string.pref_account_delete_key)) { p =>
      p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
        def onPreferenceClick(preference: Preference): Boolean = {
          deleteAccount()
          true
        }
      })
    },

    //Picture
    returning(findPref[PicturePreference](R.string.pref_account_picture_key)) { p =>
      p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
        def onPreferenceClick(preference: Preference): Boolean = {
          getControllerFactory.getCameraController.openCamera(CameraContext.SETTINGS)
          true
        }
      })
    },

    //Accent color
    returning(findPref[ColorPreference](R.string.pref_account_color_key)) { p =>
      p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
        def onPreferenceClick(preference: Preference): Boolean = {
          getChildFragmentManager
            .beginTransaction
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .add(new AccentColorPickerFragment, AccentColorPickerFragment.fragmentTag)
            .addToBackStack(AccentColorPickerFragment.fragmentTag)
            .commit
          true
        }
      })
      selfUser.map(_.accent).map(AccentColor(_).getColor()).on(Threading.Ui)(p.setAccentColor)
    },

    phonePref,
    emailPref
  )

  override def onCreatePreferences2(savedInstanceState: Bundle, rootKey: String) = {
    super.onCreatePreferences2(savedInstanceState, rootKey)
    addPreferencesFromResource(R.xml.preferences_account)
    prefs // trigger lazy init
    if (getArguments.getBoolean(ShowUsernameEdit)) changeHandle(getControllerFactory.getUsernameController.getGeneratedUsername, cancellable = false)
  }

  override def onDestroyView() = {
    prefs.foreach(_.setOnPreferenceClickListener(null))
    super.onDestroyView()
  }

  override def onVerifyEmail(email: String) = {
    getChildFragmentManager.popBackStack(AddEmailAndPasswordPreferenceDialogFragment.TAG, POP_BACK_STACK_INCLUSIVE)
    getChildFragmentManager.popBackStack(ChangeEmailPreferenceDialogFragment.TAG, POP_BACK_STACK_INCLUSIVE)
    verifyEmail(email)
  }

  override def onEmailCleared() = {
    getChildFragmentManager.popBackStack(ChangeEmailPreferenceDialogFragment.TAG, POP_BACK_STACK_INCLUSIVE)
  }

  override def onVerifyPhone(phoneNumber: String) = {
    getChildFragmentManager.popBackStack(AddPhoneNumberPreferenceDialogFragment.TAG, POP_BACK_STACK_INCLUSIVE)
    verifyPhoneNumber(phoneNumber)
  }

  override def onPhoneNumberCleared() = {
    getChildFragmentManager.popBackStack(AddPhoneNumberPreferenceDialogFragment.TAG, POP_BACK_STACK_INCLUSIVE)
  }

  private def signOut() = {
    showAlertDialog(getActivity, null,
      getString(R.string.pref_account_sign_out_warning_message),
      getString(R.string.pref_account_sign_out_warning_verify),
      getString(R.string.pref_account_sign_out_warning_cancel),
      new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int) = {
          for {
          // TODO: Remove old SignOut event https://wearezeta.atlassian.net/browse/AN-4232
            _ <- tracking.tagEvent(new SignOut)
            _ <- tracking.tagEvent(new LoggedOutEvent)
            _ = getControllerFactory.getUsernameController.tearDown()
            _ <- selfAcc.head.flatMap(_.logout())
          } yield {}
        }
      }, null)
  }

  private def deleteAccount() = {
    val email = emailPref.getTitle.asInstanceOf[String]
    val phone = phonePref.getTitle.asInstanceOf[String]

    val message = if (getString(R.string.pref_account_add_email_title) == email)
      getString(R.string.pref_account_delete_warning_message_sms, phone)
    else
      getString(R.string.pref_account_delete_warning_message_email, email)

    showAlertDialog(getActivity,
      getString(R.string.pref_account_delete_warning_title),
      getBoldText(getActivity, message),
      getString(R.string.pref_account_delete_warning_verify),
      getString(R.string.pref_account_delete_warning_cancel),
      new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int) = {
          zms.head.map(_.users.deleteAccount())
        }
      }, null)
  }

  private def verifyPhoneNumber(phoneNumber: String) = {
    import VerifyPhoneNumberPreferenceFragment._
    showPrefDialog(newInstance(phoneNumber), TAG)
  }

  private def verifyEmail(email: String) = {
    import VerifyEmailPreferenceFragment._
    showPrefDialog(newInstance(email), TAG)
  }

  private def addPhoneNumber() = {
    import AddPhoneNumberPreferenceDialogFragment._
    showPrefDialog(newInstance, TAG)
  }

  def changePhoneNumber(phoneNumber: String) = {
    import AddPhoneNumberPreferenceDialogFragment._
    showPrefDialog(newInstance(phoneNumber), TAG)
  }

  def changeEmail(email: String) = {
    import ChangeEmailPreferenceDialogFragment._
    showPrefDialog(newInstance(email), TAG)
  }

  private def addEmailAndPassword() = {
    import AddEmailAndPasswordPreferenceDialogFragment._
    showPrefDialog(newInstance, TAG)
  }

  private def changeHandle(currentHandle: String, cancellable: Boolean) = {
    tracking.tagEvent(new EditedUsernameEvent)
    import ChangeHandleFragment._
    showPrefDialog(newInstance(currentHandle, cancellable), FragmentTag)
  }

  private def showPrefDialog(f: Fragment, tag: String) = {
    getChildFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(f, tag)
      .addToBackStack(tag)
      .commit
  }
}

object AccountPreferences {
  def newInstance(rootKey: String, extras: Bundle) = {
    returning(new AccountPreferences) { f =>
      f.setArguments(returning(if (extras == null) new Bundle else new Bundle(extras)) { b =>
        b.putString(ARG_PREFERENCE_ROOT, rootKey)
      })
    }
  }
}
