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
package com.waz.zclient.deeplinks

import android.content.Context
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.AccountManager.ClientRegistrationState.Registered
import com.waz.service.{AccountManager, AccountsService, SSOService}
import com.waz.utils.events.Signal
import com.waz.zclient.utils.ContextUtils.showErrorDialog
import com.waz.zclient.{BuildConfig, Injectable, Injector, R}

import scala.concurrent.{ExecutionContext, Future}

class DeepLinkService(implicit injector: Injector) extends Injectable with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Ui

  private lazy val accountsService        = inject[AccountsService]
  private lazy val account                = inject[Signal[Option[AccountManager]]]

  def validateSSOLogin(token: String)(implicit context: Context, ex: ExecutionContext): Future[Option[String]] = {
    (if (!inject[SSOService].isTokenValid(token.trim)) {
      showErrorDialog(R.string.sso_signin_wrong_code_title, R.string.sso_signin_wrong_code_message).map(_ => None)
    } else {
      accountsService.accountsWithManagers.head.flatMap {
        case accounts if accounts.size < BuildConfig.MAX_ACCOUNTS =>
          Future.successful(Some(token))
        case _ =>
          showErrorDialog(R.string.sso_signin_max_accounts_title, R.string.sso_signin_max_accounts_message).map(_ => None)
      }
    }).flatMap {
      case Some(ssoToken) =>
        account.head.flatMap {
          case Some(am) =>
            am.getOrRegisterClient().flatMap {
              case Right(Registered(_)) => Future.successful(Some(ssoToken))
              case _                    => Future.successful(None)
            }
          case _ => Future.successful(Some(ssoToken))
        }
      case _ => Future.successful(None)
    }
  }
}
