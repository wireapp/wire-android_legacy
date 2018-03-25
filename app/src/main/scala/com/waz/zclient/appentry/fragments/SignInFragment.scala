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

import android.content.{Context, DialogInterface}
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
import com.waz.api.impl.ErrorResponse
import com.waz.service.AccountManager.ClientRegistrationState.{LimitReached, Registered}
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.appentry.fragments.SignInFragment._
import com.waz.zclient.appentry.{AppEntryActivity, EntryError}
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.newreg.fragments.TabPages
import com.waz.zclient.newreg.fragments.country.{Country, CountryController}
import com.waz.zclient.newreg.views.PhoneConfirmationButton
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.profile.validator.{EmailValidator, NameValidator, PasswordValidator}
import com.waz.zclient.pages.main.profile.views.GuidedEditText
import com.waz.zclient.tracking.{GlobalTrackingController, SignUpScreenEvent}
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceEditText, TypefaceTextView}
import com.waz.zclient.ui.utils.{KeyboardUtils, TextViewUtils}
import com.waz.zclient.ui.views.tab.TabIndicatorLayout
import com.waz.zclient.ui.views.tab.TabIndicatorLayout.Callback
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._

class SignInFragment extends BaseFragment[Container]
  with FragmentHelper
  with View.OnClickListener
  with CountryController.Observer {

  implicit def context: Context = getActivity

  lazy val accountsService   = inject[AccountsService]
  lazy val browserController = inject[BrowserController]
  lazy val tracking          = inject[GlobalTrackingController]

  lazy val isAddingAccount = accountsService.zmsInstances.map(_.nonEmpty)

  lazy val uiSignInState = {
    val sign = getArguments.getString(SignTypeArg, "Register") match {
      case "Login" => Login
      case _       => Register
    }
    val input = getArguments.getString(InputTypeArg, "Phone") match {
      case "Email" => Email
      case _       => Phone
    }
    Signal(SignInMethod(sign, input))
  }

  val email    = Signal("")
  val password = Signal("")
  val name     = Signal("")
  val phone    = Signal("")

  lazy val countryController = new CountryController(context)
  countryController.addObserver(this)
  lazy val phoneCountry = Signal[Country]()

  lazy val nameValidator = new NameValidator()
  lazy val emailValidator = EmailValidator.newInstance()
  lazy val passwordValidator = PasswordValidator.instance(context)
  lazy val legacyPasswordValidator = PasswordValidator.instanceLegacy(context)

  lazy val isValid: Signal[Boolean] = uiSignInState.flatMap {
    case SignInMethod(Login, Email) =>
      for {
        email <- email
        password <- password
      } yield emailValidator.validate(email) && legacyPasswordValidator.validate(password)
    case SignInMethod(Register, Email) =>
      for {
        name <- name
        email <- email
        password <- password
      } yield nameValidator.validate(name) && emailValidator.validate(email) && passwordValidator.validate(password)
    case SignInMethod(_, Phone) =>
      phone.map(_.nonEmpty)
    case _ => Signal.empty[Boolean]
  }

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
      field.setValidator(emailValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__email)
      field.setText(email.currentValue.getOrElse(""))
      field.getEditText.addTextListener(email ! _)
    }

    passwordField.foreach { field =>
      field.setValidator(passwordValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__password)
      field.setText(password.currentValue.getOrElse(""))
      field.getEditText.addTextListener(password ! _)
    }

    nameField.foreach { field =>
      field.setValidator(nameValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__name)
      field.setText(name.currentValue.getOrElse(""))
      field.getEditText.addTextListener(name ! _)
    }

    phoneField.foreach { field =>
      field.setText(phone.currentValue.getOrElse(""))
      field.addTextListener(phone ! _)
    }

    termsOfService.foreach { text =>
      TextViewUtils.linkifyText(text, getColor(R.color.white), true, new Runnable {
        override def run() = browserController.openUrl(getString(R.string.url_terms_of_service_personal))
      })
    }
    countryButton.foreach(_.setOnClickListener(this))
    countryCodeText.foreach(_.setOnClickListener(this))
    confirmationButton.foreach(_.setOnClickListener(this))
    confirmationButton.foreach(_.setAccentColor(Color.WHITE))
    setConfirmationButtonActive(isValid.currentValue.getOrElse(false))
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
            uiSignInState.mutate(_ => SignInMethod(Register, Phone))
          case TabPages.SIGN_IN =>
            tabSelector.setSelected(TabPages.SIGN_IN)
            uiSignInState.mutate {
              case SignInMethod(Register, _) => SignInMethod(Login, Email)
              case other => other
            }
          case _ =>
        }
      }
    })

    uiSignInState.head.map {
      case SignInMethod(Login, _) => tabSelector.setSelected(TabPages.SIGN_IN)
      case SignInMethod(Register, _) => tabSelector.setSelected(TabPages.CREATE_ACCOUNT)
    } (Threading.Ui)

    //TODO: remove when login by email available on phones
    //TODO put back after testing...
//    if (LayoutSpec.isPhone(getActivity)) {
//      signInController.uiSignInState.onUi {
//        case SignInMethod(Register, Email) =>
//          signInController.uiSignInState ! SignInMethod(Register, Phone)
//        case SignInMethod(Register, _) =>
//          phoneButton.setVisible(false)
//          emailButton.setVisible(false)
//        case _ =>
//          phoneButton.setVisible(true)
//          emailButton.setVisible(true)
//      }
//    }

    uiSignInState.onUi { state =>
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
      phoneCountry.currentValue.foreach(onCountryHasChanged)
    }

    uiSignInState.map(s => SignInMethod(s.signType, Phone)).onUi { method =>
      ZMessaging.globalModule.map(_.trackingService.track(SignUpScreenEvent(method)))(Threading.Ui)
    }

    isValid.onUi(setConfirmationButtonActive)
    phoneCountry.onUi(onCountryHasChanged)
    isAddingAccount.onUi(closeButton.setVisible)
  }

  private def setConfirmationButtonActive(active: Boolean): Unit = {
    import PhoneConfirmationButton.State._
    confirmationButton.foreach(_.setState(if (active) CONFIRM else NONE))
  }

  private def setPhoneButtonSelected() = {
    phoneButton.setBackground(getDrawable(R.drawable.selector__reg__signin))
    phoneButton.setTextColor(getColor(R.color.white))
    emailButton.setBackground(null)
    emailButton.setTextColor(getColor(R.color.white_40))
  }

  private def setEmailButtonSelected() = {
    emailButton.setBackground(getDrawable(R.drawable.selector__reg__signin))
    emailButton.setTextColor(getColor(R.color.white))
    phoneButton.setBackground(null)
    phoneButton.setTextColor(getColor(R.color.white_40))
  }

  override def onClick(v: View) = {
    v.getId match {
      case R.id.ttv__new_reg__sign_in__go_to__email =>
        uiSignInState.mutate {
          case SignInMethod(x, Phone) => SignInMethod(x, Email)
          case other => other
        }

      case R.id.ttv__new_reg__sign_in__go_to__phone =>
        uiSignInState.mutate {
          case SignInMethod(x, Email) => SignInMethod(x, Phone)
          case other => other
        }

      case R.id.ll__signup__country_code__button | R.id.tv__country_code =>
        activity.openCountryBox()

      case R.id.pcb__signin__email => //TODO rename!
        implicit val ec = Threading.Ui
        KeyboardUtils.closeKeyboardIfShown(getActivity)
        activity.enableProgress(true)

        def onResponse[A](resp: Either[ErrorResponse, A], m: SignInMethod) = {
          returning(resp match {
            case Left(error) =>
              activity.enableProgress(false)
              Left(EntryError(error.code, error.label, m))
            case Right(ret) => Right(ret)
          })(tracking.onEnteredCredentials(_, m))
        }

        uiSignInState.head.flatMap {
          case m@SignInMethod(Login, Email) =>
            for {
              email    <- email.head
              password <- password.head
              Right(_) <- accountsService.loginEmail(email, password).map(onResponse(_, m))
              Some(am) <- accountsService.getActiveAccountManager
              Right(client) <- am.registerClient().map(onResponse(_, m))
            } yield activity.onEnterApplication(openSettings = false)
          case _ => throw new NotImplementedError("Only login with email works right now") //TODO
        }

      case R.id.ttv_signin_forgot_password =>
        browserController.openUrl(getString(R.string.url_password_reset))
      case R.id.close_button =>
        getContainer.abortAddAccount()
      case _ =>
    }
  }

  override def onCountryHasChanged(country: Country): Unit = {
    phoneCountry ! country
    countryCodeText.foreach(_.setText(String.format("+%s", country.getCountryCode)) )
    countryNameText.foreach(_.setText(country.getName))
  }

  def clearCredentials() =
    Set(email, password, name, phone).foreach(_ ! "")

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

  def activity = getActivity.asInstanceOf[AppEntryActivity]
}

object SignInFragment {

  val SignTypeArg = "SIGN_IN_TYPE"
  val InputTypeArg = "INPUT_TYPE"

  def newInstance() = new SignInFragment

  def newInstance(signInMethod: SignInMethod): SignInFragment = {
    returning(new SignInFragment()) {
      _.setArguments(returning(new Bundle) { b =>
          b.putString(SignTypeArg, signInMethod.signType.toString)
          b.putString(InputTypeArg, signInMethod.inputType.toString)
      })
    }
  }

  val Tag = logTagFor[SignInFragment]
  trait Container {
    def abortAddAccount(): Unit
  }

  sealed trait SignType
  object Login extends SignType
  object Register extends SignType

  sealed trait InputType
  object Email extends InputType
  object Phone extends InputType

  case class SignInMethod(signType: SignType, inputType: InputType)
}

class AutoTransition2 extends TransitionSet {
  setOrdering(TransitionSet.ORDERING_TOGETHER)
  addTransition(new Fade(Fade.OUT)).addTransition(new ChangeBounds).addTransition(new Fade(Fade.IN))
}
