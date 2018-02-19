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
package com.waz.zclient.appentry.fragments

import android.content.DialogInterface
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.KITKAT
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.transition._
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, LinearLayout}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.zclient._
import com.waz.zclient.appentry.controllers.SignInController
import com.waz.zclient.appentry.controllers.SignInController._
import com.waz.zclient.appentry.fragments.SignInFragment._
import com.waz.zclient.appentry.{AppEntryActivity, EntryError}
import com.waz.zclient.newreg.fragments.TabPages
import com.waz.zclient.newreg.fragments.country.Country
import com.waz.zclient.newreg.views.PhoneConfirmationButton
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.profile.views.GuidedEditText
import com.waz.zclient.tracking.SignUpScreenEvent
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceEditText, TypefaceTextView}
import com.waz.zclient.ui.utils.{KeyboardUtils, TextViewUtils}
import com.waz.zclient.ui.views.tab.TabIndicatorLayout
import com.waz.zclient.ui.views.tab.TabIndicatorLayout.Callback
import com.waz.zclient.utils._

class SignInFragment extends BaseFragment[Container] with FragmentHelper with View.OnClickListener {

  lazy val signInController = inject[SignInController]

  lazy val container = getView.findViewById(R.id.sign_in_container).asInstanceOf[FrameLayout]
  lazy val scenes = Array(
    R.layout.sign_in_email_scene,
    R.layout.sign_in_phone_scene,
    R.layout.sign_up_email_scene,
    R.layout.sign_up_phone_scene
  )

  lazy val phoneButton = findById[TypefaceTextView](getView, R.id.ttv__new_reg__sign_in__go_to__phone)
  lazy val emailButton = findById[TypefaceTextView](getView, R.id.ttv__new_reg__sign_in__go_to__email)
  lazy val tabSelector = findById[TabIndicatorLayout](getView, R.id.til__app_entry)
  lazy val closeButton = findById[GlyphTextView](getView, R.id.close_button)

  def nameField = Option(findById[GuidedEditText](getView, R.id.get__sign_in__name))

  def emailField = Option(findById[GuidedEditText](getView, R.id.get__sign_in__email))
  def passwordField = Option(findById[GuidedEditText](getView, R.id.get__sign_in__password))

  def phoneField = Option(findById[TypefaceEditText](getView, R.id.et__reg__phone))
  def countryNameText = Option(findById[TypefaceTextView](R.id.ttv_new_reg__signup__phone__country_name))
  def countryCodeText = Option(findById[TypefaceTextView](R.id.tv__country_code))
  def countryButton = Option(findById[LinearLayout](R.id.ll__signup__country_code__button))

  def confirmationButton = Option(findById[PhoneConfirmationButton](R.id.pcb__signin__email))

  def termsOfService = Option(findById[TypefaceTextView](R.id.terms_of_service_text))

  def forgotPasswordButton = Option(findById[View](getView, R.id.ttv_signin_forgot_password))

  def setupViews(): Unit = {

    emailField.foreach { field =>
      field.setValidator(signInController.emailValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__email)
      field.setText(signInController.email.currentValue.getOrElse(""))
      field.getEditText.addTextListener(signInController.email ! _)
    }

    passwordField.foreach { field =>
      field.setValidator(signInController.passwordValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__password)
      field.setText(signInController.password.currentValue.getOrElse(""))
      field.getEditText.addTextListener(signInController.password ! _)
    }

    nameField.foreach { field =>
      field.setValidator(signInController.nameValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__name)
      field.setText(signInController.name.currentValue.getOrElse(""))
      field.getEditText.addTextListener(signInController.name ! _)
    }

    phoneField.foreach { field =>
      field.setText(signInController.phone.currentValue.getOrElse(""))
      field.addTextListener(signInController.phone ! _)
    }

    termsOfService.foreach { text =>
      TextViewUtils.linkifyText(text, ContextCompat.getColor(getContext, R.color.white), true, new Runnable {
        override def run() = getContainer.onOpenUrl(getString(R.string.url_terms_of_service_personal))
      })
    }
    countryButton.foreach(_.setOnClickListener(this))
    countryCodeText.foreach(_.setOnClickListener(this))
    confirmationButton.foreach(_.setOnClickListener(this))
    confirmationButton.foreach(_.setAccentColor(Color.WHITE))
    setConfirmationButtonActive(signInController.isValid.currentValue.getOrElse(false))
    forgotPasswordButton.foreach(_.setOnClickListener(this))
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.sign_in_fragment, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {

    val transition = if (SDK_INT >= KITKAT) Option(new AutoTransition2()) else None

    def switchScene(sceneIndex: Int) = transition.fold[Unit]({
      container.removeAllViews()
      LayoutInflater.from(getContext).inflate(scenes(sceneIndex), container)
    })( TransitionManager.go(Scene.getSceneForLayout(container, scenes(sceneIndex), getContext), _) )

    phoneButton.setOnClickListener(this)
    emailButton.setOnClickListener(this)
    closeButton.setOnClickListener(this)
    tabSelector.setLabels(Array[Int](R.string.new_reg__phone_signup__create_account, R.string.i_have_an_account))
    tabSelector.setTextColor(ContextCompat.getColorStateList(getContext, R.color.wire__text_color_dark_selector))
    tabSelector.setSelected(TabPages.SIGN_IN)

    tabSelector.setCallback(new Callback {
      override def onItemSelected(pos: Int) = {
        pos match  {
          case TabPages.CREATE_ACCOUNT =>
            tabSelector.setSelected(TabPages.CREATE_ACCOUNT)
            signInController.uiSignInState.mutate(_ => SignInMethod(Register, Phone))
          case TabPages.SIGN_IN =>
            tabSelector.setSelected(TabPages.SIGN_IN)
            signInController.uiSignInState.mutate {
              case SignInMethod(Register, _) => SignInMethod(Login, Email)
              case other => other
            }
          case _ =>
        }
      }
    })

    signInController.uiSignInState.head.map {
      case SignInMethod(Login, _) => tabSelector.setSelected(TabPages.SIGN_IN)
      case SignInMethod(Register, _) => tabSelector.setSelected(TabPages.CREATE_ACCOUNT)
    } (Threading.Ui)

    //TODO: remove when login by email available on phones
    if (LayoutSpec.isPhone(getActivity)) {
      signInController.uiSignInState.onUi {
        case SignInMethod(Register, Email) =>
          signInController.uiSignInState ! SignInMethod(Register, Phone)
        case SignInMethod(Register, _) =>
          phoneButton.setVisible(false)
          emailButton.setVisible(false)
        case _ =>
          phoneButton.setVisible(true)
          emailButton.setVisible(true)
      }
    }

    signInController.uiSignInState.onUi { state =>
      state match {
        case SignInMethod(Login, Email) =>
          switchScene(0)
          setupViews()
          setEmailButtonSelected()
          emailField.foreach(_.getEditText.requestFocus())
        case SignInMethod(Login, Phone) =>
          switchScene(1)
          setupViews()
          setPhoneButtonSelected()
          phoneField.foreach(_.requestFocus())
        case SignInMethod(Register, Email) =>
          switchScene(2)
          setupViews()
          setEmailButtonSelected()
          nameField.foreach(_.getEditText.requestFocus())
        case SignInMethod(Register, Phone) =>
          switchScene(3)
          setupViews()
          setPhoneButtonSelected()
          phoneField.foreach(_.requestFocus())
      }
      signInController.phoneCountry.currentValue.foreach{ onCountryHasChanged }
    }

    signInController.uiSignInState.map(s => SignInMethod(s.signType, Phone)).onUi { method =>
      ZMessaging.globalModule.map(_.trackingService.track(SignUpScreenEvent(method)))(Threading.Ui)
    }

    signInController.isValid.onUi { setConfirmationButtonActive }
    signInController.phoneCountry.onUi { onCountryHasChanged }
    signInController.isAddingAccount.onUi { closeButton.setVisible }
  }

  private def setConfirmationButtonActive(active: Boolean): Unit = {
    if(active)
      confirmationButton.foreach(_.setState(PhoneConfirmationButton.State.CONFIRM))
    else
      confirmationButton.foreach(_.setState(PhoneConfirmationButton.State.NONE))
  }

  private def setPhoneButtonSelected() = {
    phoneButton.setBackground(ContextCompat.getDrawable(getContext, R.drawable.selector__reg__signin))
    phoneButton.setTextColor(ContextCompat.getColor(getContext, R.color.white))
    emailButton.setBackground(null)
    emailButton.setTextColor(ContextCompat.getColor(getContext, R.color.white_40))
  }

  private def setEmailButtonSelected() = {
    emailButton.setBackground(ContextCompat.getDrawable(getContext, R.drawable.selector__reg__signin))
    emailButton.setTextColor(ContextCompat.getColor(getContext, R.color.white))
    phoneButton.setBackground(null)
    phoneButton.setTextColor(ContextCompat.getColor(getContext, R.color.white_40))
  }

  override def onClick(v: View) = {
    v.getId match {
      case R.id.ttv__new_reg__sign_in__go_to__email =>
        signInController.uiSignInState.mutate {
          case SignInMethod(x, Phone) => SignInMethod(x, Email)
          case other => other
        }

      case R.id.ttv__new_reg__sign_in__go_to__phone =>
        signInController.uiSignInState.mutate {
          case SignInMethod(x, Email) => SignInMethod(x, Phone)
          case other => other
        }

      case R.id.ll__signup__country_code__button | R.id.tv__country_code =>
        getActivity.asInstanceOf[AppEntryActivity].openCountryBox()

      case R.id.pcb__signin__email =>
        implicit val ec = Threading.Ui
        KeyboardUtils.closeKeyboardIfShown(getActivity)
        getActivity.asInstanceOf[AppEntryActivity].enableProgress(true)
        signInController.attemptSignIn().map {
          case Left(error) =>
            getActivity.asInstanceOf[AppEntryActivity].enableProgress(false)
            showError(error)
          case _ =>
        }
      case R.id.ttv_signin_forgot_password =>
        getContainer.onOpenUrl(getString(R.string.url_password_reset))
      case R.id.close_button =>
        getContainer.abortAddAccount()
      case _ =>
    }
  }

  def onCountryHasChanged(country: Country): Unit = {
    countryCodeText.foreach(_.setText(String.format("+%s", country.getCountryCode)) )
    countryNameText.foreach(_.setText(country.getName))
  }

  def showError(entryError: EntryError, okCallback: => Unit = {}): Unit =
    ViewUtils.showAlertDialog(getActivity,
      entryError.headerResource,
      entryError.bodyResource,
      R.string.reg__phone_alert__button,
      new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          dialog.dismiss()
          okCallback
        }
      },
      false)
}

object SignInFragment {
  val Tag = logTagFor[SignInFragment]
  trait Container {
    def abortAddAccount(): Unit
    def onOpenUrl(url: String): Unit
  }
}

class AutoTransition2 extends TransitionSet {
  setOrdering(TransitionSet.ORDERING_TOGETHER)
  addTransition(new Fade(Fade.OUT)).addTransition(new ChangeBounds).addTransition(new Fade(Fade.IN))
}
