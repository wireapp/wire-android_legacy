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

import android.R
import android.app.Dialog
import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_POSITIVE
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, WindowManager}
import android.widget.{EditText, TextView}
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.{AppCompatDrawableManager, DrawableUtils => AxDrawableUtils}
import androidx.core.view.{ViewCompat, ViewPropertyAnimatorListenerAdapter}
import androidx.fragment.app.DialogFragment
import androidx.interpolator.view.animation.{FastOutLinearInInterpolator, LinearOutSlowInInterpolator}
import com.waz.model.PhoneNumber
import com.waz.service.UserService
import com.waz.threading.Threading
import com.waz.utils.{MathUtils, returning}
import com.waz.zclient._
import com.waz.zclient.appentry.DialogErrorMessage.PhoneError
import com.waz.zclient.newreg.fragments.country.{Country, CountryController}
import com.waz.zclient.utils.{DeprecationUtils, RichView, ViewUtils}
import com.wire.signals.{EventStream, Signal}

import scala.util.Try

class ChangePhoneDialog extends DialogFragment with FragmentHelper with CountryController.Observer {
  import ChangePhoneDialog._
  import Threading.Implicits.Ui

  private lazy val root = LayoutInflater.from(getContext).inflate(R.layout.preference_dialog_add_phone, null)

  val onPhoneChanged = EventStream[Option[PhoneNumber]]()

  private lazy val users = inject[Signal[UserService]]

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

  private lazy val countryEditText = returning(findById[EditText](root, R.id.acet__preferences__country)) { v =>
    countryCode.foreach { cc =>
      v.setText(s"+$cc")
      v.requestFocus()
    }
  }

  private lazy val phoneEditText = returning(findById[EditText](root, R.id.acet__preferences__phone)) { v =>
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

  override def onStart(): Unit = {
    super.onStart()
    Try(getDialog.asInstanceOf[AlertDialog]).toOption.foreach { dialog =>
      dialog.getButton(BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
        def onClick(v: View): Unit = handleInput()
      })
      Option(dialog.getButton(DialogInterface.BUTTON_NEUTRAL)).foreach {
        _.setOnClickListener(new View.OnClickListener() {
          def onClick(v: View): Unit = clearPhoneNumber()
        })
      }
    }
    countryController.addObserver(this)
  }

  override def onStop(): Unit = {
    countryController.removeObserver(this)
    super.onStop()
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
            def onClick(dialog: DialogInterface, which: Int): Unit =
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
  private def showError(error: String): Unit = if (!TextUtils.equals(errorView.getText, error)) {
    val animate = ViewCompat.isLaidOut(containerView)
    ViewCompat.animate(errorView).cancel()

    if (!TextUtils.isEmpty(error)) {
      errorView.setText(error)
      errorView.setVisible(true)
      if (animate) {
        if (MathUtils.floatEqual(DeprecationUtils.getAlpha(errorView), 1f)) DeprecationUtils.setAlpha(errorView, 0f)
        ViewCompat.animate(errorView).alpha(1f).setDuration(AnimationDuration).setInterpolator(LINEAR_OUT_SLOW_IN_INTERPOLATOR).setListener(new ViewPropertyAnimatorListenerAdapter() {
          override def onAnimationStart(view: View): Unit = view.setVisible(true)
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
            override def onAnimationEnd(view: View): Unit = {
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
  private def updateEditTextBackground(editText: EditText): Unit = {
    Option(editText.getBackground).map { bg =>
      if (AxDrawableUtils.canSafelyMutateDrawable(bg)) bg.mutate else bg
    }.foreach { bg =>
      if (errorView.isVisible) {
        // Set a color filter of the error color
        bg.setColorFilter(AppCompatDrawableManager.getPorterDuffColorFilter(errorView.getCurrentTextColor, PorterDuff.Mode.SRC_IN))
      }
      else {
        // Else  refresh the drawable state so that the normal tint is used
        editText.refreshDrawableState()
      }
    }
  }

  override def onCountryHasChanged(country: Country): Unit =
    if(countryEditText.getText.toString.trim.isEmpty) countryEditText.setText(s"+${country.getCountryCode}")
}

object ChangePhoneDialog {

  private val AnimationDuration = 200L
  private val FAST_OUT_LINEAR_IN_INTERPOLATOR = new FastOutLinearInInterpolator
  private val LINEAR_OUT_SLOW_IN_INTERPOLATOR = new LinearOutSlowInInterpolator

  val FragmentTag: String = ChangePhoneDialog.getClass.getSimpleName
  val CurrentPhoneArg: String = "ARG_CURRENT_PHONE"
  val HasEmailArg: String = "ARG_HAS_EMAIL"

  def apply(currentPhone: Option[String], hasEmail: Boolean): ChangePhoneDialog =
    returning(new ChangePhoneDialog()) {
      _.setArguments(returning(new Bundle()) { b =>
        currentPhone.foreach(b.putString(CurrentPhoneArg, _))
        b.putBoolean(HasEmailArg, hasEmail)
      })
    }
}
