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
package com.waz.zclient.preferences.dialogs

import android.Manifest.permission.READ_PHONE_STATE
import android.annotation.TargetApi
import android.app.Dialog
import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_POSITIVE
import android.graphics.PorterDuff
import android.graphics.drawable.{Drawable, DrawableContainer, InsetDrawable}
import android.os.Build.VERSION_CODES._
import android.os.{Build, Bundle}
import android.support.annotation.NonNull
import android.support.v4.app.DialogFragment
import android.support.v4.graphics.drawable.DrawableWrapper
import android.support.v4.view.animation.{FastOutLinearInInterpolator, LinearOutSlowInInterpolator}
import android.support.v4.view.{ViewCompat, ViewPropertyAnimatorListenerAdapter}
import android.support.v7.app.AlertDialog
import android.support.v7.widget.AppCompatDrawableManager.getPorterDuffColorFilter
import android.support.v7.widget.DrawableUtils.canSafelyMutateDrawable
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, WindowManager}
import android.widget.{EditText, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.model.PhoneNumber
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.appentry.DialogErrorMessage.PhoneError
import com.waz.zclient.controllers.deviceuser.IDeviceUserController
import com.waz.zclient.newreg.fragments.country.{Country, CountryController}
import com.waz.zclient.ui.utils.{DrawableUtils, MathUtils}
import com.waz.zclient.utils.{DeprecationUtils, RichView, ViewUtils}

import scala.util.Try

class ChangePhoneDialog extends DialogFragment with FragmentHelper with CountryController.Observer {
  import ChangePhoneDialog._
  import Threading.Implicits.Ui

  private lazy val root = LayoutInflater.from(getContext).inflate(R.layout.preference_dialog_add_phone, null)

  val onPhoneChanged = EventStream[Option[PhoneNumber]]()

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val users = zms.map(_.users)
  lazy val deviceUserController = inject[IDeviceUserController]

  private lazy val countryController = new CountryController(getActivity)
  private lazy val currentPhone      = Option(getArguments.getString(CurrentPhoneArg))
  private lazy val hasEmail          = Option(getArguments.getBoolean(HasEmailArg)).getOrElse(false)
  private lazy val number            = currentPhone.map(countryController.getPhoneNumberWithoutCountryCode)
  private lazy val countryCode =
    for {
      p <- currentPhone
      n <- number
    } yield
      p.substring(0, p.length - n.length).replace("+", "")

  private lazy val containerView = findById[View](root, R.id.ll__preferences__container)
  private lazy val errorView     = returning(findById[TextView](root, R.id.tv__preferences__error))(_.setVisible(false))

  lazy val countryEditText = returning(findById[EditText](root, R.id.acet__preferences__country)) { v =>
    countryCode.foreach { cc =>
      v.setText(s"+$cc")
      v.requestFocus()
    }
  }

  lazy val phoneEditText = returning(findById[EditText](root, R.id.acet__preferences__phone)) { v =>
    if (countryCode.isEmpty) v.requestFocus()
    v.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean =
        if (actionId == EditorInfo.IME_ACTION_DONE) {
          handleInput()
          true
        }
        else false
    })
    number.foreach { n =>
      v.setText(n)
      v.setSelection(n.length)
    }
  }

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    //lazy init
    containerView
    errorView
    countryEditText
    phoneEditText

    val alertDialogBuilder = new AlertDialog.Builder(getActivity)
      .setTitle(if (currentPhone.isDefined) R.string.pref__account_action__dialog__edit_phone__title else R.string.pref__account_action__dialog__add_phone__title)
      .setView(root)
      .setPositiveButton(android.R.string.ok, null)
      .setNegativeButton(android.R.string.cancel, null)
    if (currentPhone.isDefined && hasEmail) alertDialogBuilder.setNeutralButton(R.string.pref_account_delete, null)
    val alertDialog: AlertDialog = alertDialogBuilder.create
    alertDialog.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    alertDialog
  }

  override def onStart() = {
    super.onStart()
    if (currentPhone.isDefined && inject[PermissionsService].checkPermission(READ_PHONE_STATE)) setSimPhoneNumber()
    Try(getDialog.asInstanceOf[AlertDialog]).toOption.foreach { dialog =>
      dialog.getButton(BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
        def onClick(v: View) = handleInput()
      })
      Option(dialog.getButton(DialogInterface.BUTTON_NEUTRAL)).foreach {
        _.setOnClickListener(new View.OnClickListener() {
          def onClick(v: View) = clearPhoneNumber()
        })
      }
    }
    countryController.addObserver(this)
  }

  override def onStop() = {
    countryController.removeObserver(this)
    super.onStop()
  }

  private def setSimPhoneNumber() = {
    for {
      iso <- Option(deviceUserController.getPhoneCountryISO)
      cc <- Option(countryController.getCodeForAbbreviation(iso))
      ph <- Option(deviceUserController.getPhoneNumber(cc))
    } {
      phoneEditText.setText(ph)
      phoneEditText.setSelection(ph.length)
      countryEditText.setText(String.format("+%s", cc.replace("+", "")))
    }
  }

  private def clearPhoneNumber() = {
    ViewUtils.showAlertDialog(
      getActivity,
      getString(R.string.pref__account_action__dialog__delete_phone_or_email__confirm__title),
      getString(R.string.pref__account_action__dialog__delete_phone_or_email__confirm__message, currentPhone.getOrElse("")),
      getString(android.R.string.ok), getString(android.R.string.cancel),
      new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int) =
          users.head.flatMap(_.clearPhone()).map {
            case Right(_) =>
              onPhoneChanged ! None
              dismiss()
            case Left(_) =>
              showError(getString(R.string.pref__account_action__dialog__delete_phone__error))
          }
      }, null)
  }

  private def handleInput(): Unit = {

    val newCountryCode = Option(countryEditText.getText.toString.trim).filter { cc =>
      cc.nonEmpty && cc.matches("\\+([0-9])+")
    }
    val rawNumber = Option(phoneEditText.getText.toString.trim).filter(_.nonEmpty)

    (newCountryCode, rawNumber) match {
      case (None, _) => showError(getString(R.string.pref__account_action__dialog__add_phone__error__country))
      case (_, None) => showError(getString(R.string.pref__account_action__dialog__add_phone__error__number))
      case (Some(cc), Some(rn)) =>
        val n = PhoneNumber(s"$cc$rn".toLowerCase)
        ViewUtils.showAlertDialog(
          getActivity,
          getString(R.string.pref__account_action__dialog__add_phone__confirm__title),
          getString(R.string.pref__account_action__dialog__add_phone__confirm__message, n.str),
          getString(android.R.string.ok),
          getString(android.R.string.cancel),
          new DialogInterface.OnClickListener() {
            def onClick(dialog: DialogInterface, which: Int) =
              users.head.flatMap(_.updatePhone(n)).map { //TODO what if the number is already set?
                case Right(_) =>
                  onPhoneChanged ! Some(n)
                  dismiss()
                case Left(err) =>
                  showError(getString(PhoneError(err).headerResource))
              }
          },
          null
        )
    }
  }

  // from TextInputLayout
  private def showError(error: String) = if (!TextUtils.equals(errorView.getText, error)) {
    val animate = ViewCompat.isLaidOut(containerView)
    ViewCompat.animate(errorView).cancel()

    if (!TextUtils.isEmpty(error)) {
      errorView.setText(error)
      errorView.setVisible(true)
      if (animate) {
        if (MathUtils.floatEqual(DeprecationUtils.getAlpha(errorView), 1f)) DeprecationUtils.setAlpha(errorView, 0f)
        ViewCompat.animate(errorView).alpha(1f).setDuration(AnimationDuration).setInterpolator(LINEAR_OUT_SLOW_IN_INTERPOLATOR).setListener(new ViewPropertyAnimatorListenerAdapter() {
          override def onAnimationStart(view: View) = view.setVisible(true)
        }).start()
      }
    }
    else if (errorView.isVisible)
      if (animate)
        ViewCompat.animate(errorView)
          .alpha(0f)
          .setDuration(AnimationDuration)
          .setInterpolator(FAST_OUT_LINEAR_IN_INTERPOLATOR)
          .setListener(new ViewPropertyAnimatorListenerAdapter() {
            override def onAnimationEnd(view: View) = {
              errorView.setText(error)
              view.setVisible(false)
              updateEditTextBackground(countryEditText)
              updateEditTextBackground(phoneEditText)
            }
          }).start()
      else errorView.setVisible(false)
    updateEditTextBackground(countryEditText)
    updateEditTextBackground(phoneEditText)
  }

  // from TextInputLayout
  private def updateEditTextBackground(editText: EditText) = {
    ensureBackgroundDrawableStateWorkaround(editText)
    Option(editText.getBackground).map { bg =>
      if (canSafelyMutateDrawable(bg)) bg.mutate else bg
    }.foreach { bg =>
      if (errorView.isVisible) {
        // Set a color filter of the error color
        bg.setColorFilter(getPorterDuffColorFilter(errorView.getCurrentTextColor, PorterDuff.Mode.SRC_IN))
      }
      else {
        // Else reset the color filter and refresh the drawable state so that the
        // normal tint is used
        clearColorFilter(bg)
        editText.refreshDrawableState()
      }
    }
  }

  // from TextInputLayout
  @TargetApi(KITKAT)
  private def clearColorFilter(@NonNull drawable: Drawable): Unit = {
    drawable.clearColorFilter()
    if (Build.VERSION.SDK_INT == LOLLIPOP || Build.VERSION.SDK_INT == LOLLIPOP_MR1) {
      // API 21 + 22 have an issue where clearing a color filter on a DrawableContainer
      // will not propagate to all of its children. To workaround this we unwrap the drawable
      // to find any DrawableContainers, and then unwrap those to clear the filter on its
      // children manually

      drawable match {
        case _: InsetDrawable     => clearColorFilter(drawable.asInstanceOf[InsetDrawable].getDrawable)
        case _: DrawableWrapper   => clearColorFilter(drawable.asInstanceOf[DrawableWrapper].getWrappedDrawable)
        case _: DrawableContainer =>
          Option(drawable.asInstanceOf[DrawableContainer].getConstantState.asInstanceOf[DrawableContainer.DrawableContainerState]).foreach { st =>
            (0 until st.getChildCount).foreach { i =>
              clearColorFilter(st.getChild(i));
            }
          }
        case _ => //
      }
    }
  }

  // from TextInputLayout
  private def ensureBackgroundDrawableStateWorkaround(editText: EditText) = {
    // The workaround is only required on API 21-22
    if (Seq(LOLLIPOP, LOLLIPOP_MR1).contains(Build.VERSION.SDK_INT)) {
      Option(editText.getBackground).foreach { bg =>
        // There is an issue in the platform which affects container Drawables
        // where the first drawable retrieved from resources will propogate any changes
        // (like color filter) to all instances from the cache. We'll try to workaround it...
        val newBg = bg.getConstantState.newDrawable
        var hasReconstructedEditTextBackground = false

        bg match {
          case _: DrawableContainer =>
            // If we have a Drawable container, we can try and set it's constant state via
            // reflection from the new Drawable
            hasReconstructedEditTextBackground = DrawableUtils.setContainerConstantState(bg.asInstanceOf[DrawableContainer], newBg.getConstantState)
          case _ => //
        }

        if (!hasReconstructedEditTextBackground) {
          // If we reach here then we just need to set a brand new instance of the Drawable
          // as the background. This has the unfortunate side-effect of wiping out any
          // user set padding, but I'd hope that use of custom padding on an EditText
          // is limited.
          editText.setBackground(newBg)
        }
      }
    }
  }

  override def onCountryHasChanged(country: Country) =
    if(countryEditText.getText.toString.trim.isEmpty) countryEditText.setText(s"+${country.getCountryCode}")
}

object ChangePhoneDialog {

  private val AnimationDuration = 200L
  private val FAST_OUT_LINEAR_IN_INTERPOLATOR = new FastOutLinearInInterpolator
  private val LINEAR_OUT_SLOW_IN_INTERPOLATOR = new LinearOutSlowInInterpolator

  val FragmentTag     = ChangePhoneDialog.getClass.getSimpleName
  val CurrentPhoneArg = "ARG_CURRENT_PHONE"
  val HasEmailArg     = "ARG_HAS_EMAIL"

  def apply(currentPhone: Option[String], hasEmail: Boolean): ChangePhoneDialog =
    returning(new ChangePhoneDialog()) {
      _.setArguments(returning(new Bundle()) { b =>
        currentPhone.foreach(b.putString(CurrentPhoneArg, _))
        b.putBoolean(HasEmailArg, hasEmail)
      })
    }
}
