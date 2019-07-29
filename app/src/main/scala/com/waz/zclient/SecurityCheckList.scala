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
package com.waz.zclient

import java.io.File

import android.app.AlertDialog
import android.content.Context
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading.Implicits.Ui
import com.waz.zclient.log.LogUI._

import scala.concurrent.Future
import scala.util.Try


class SecurityCheckList(list: List[(SecurityCheckList.Check, List[SecurityCheckList.Action])]) {
  import SecurityCheckList._

  def run(implicit context: Context): Future[Boolean] = runChecks(list)

  private def runChecks(checks: List[(Check, List[Action])])(implicit context: Context): Future[Boolean] = checks match {
    case (check, actions) :: tail =>
      check(context).flatMap {
        case false => runChecks(tail)
        case true  => runActions(actions).map(_ => false)
      }
    case Nil => Future.successful(true)
  }

  private def runActions(actions: List[Action])(implicit context: Context): Future[Unit] = actions match {
    case a :: tail => a(context).flatMap(_ => runActions(tail))
    case Nil       => Future.successful(())
  }
}

object SecurityCheckList extends DerivedLogTag {
  type Check  = Context => Future[Boolean]
  type Action = Context => Future[Unit]

  def apply(args: (Check, List[Action])*): SecurityCheckList = new SecurityCheckList(args.toList)

  def fromBuildConfig(): SecurityCheckList = {
    var checks: Seq[(Check, List[Action])] = List.empty

    if (BuildConfig.BLOCK_ON_JAILBREAK_OR_ROOT) {
      checks = checks :+ (checkIfRooted -> List(displayBlockingDialog))
    }

    new SecurityCheckList(checks.toList)
  }

  def getSystemProperty(key: String): Option[String] =
    Try(
      Class.forName("android.os.SystemProperties")
           .getMethod("get", classOf[String])
           .invoke(null, key)
           .asInstanceOf[String]
    ).toOption

  def runCommand(command: String): Boolean =
    Try {
      verbose(l"runCommand($command)")
      Runtime.getRuntime.exec(command)
    }.isSuccess

  def showBlockingDialog(context: Context, title: String, message: String): Unit = {
    verbose(l"show blocking dialog: $title")
    new AlertDialog.Builder(context)
      .setTitle(title)
      .setMessage(message)
      .setCancelable(false)
      .create().show()
  }

  val checkIfRooted: Check = { _ =>
    Future {
      lazy val releaseTagsExist = getSystemProperty("ro.build.tags").contains("release-keys")
      lazy val otacertsExist = new File("/etc/security/otacerts.zip").exists()
      lazy val canRunSu = runCommand("su")
      val isDeviceRooted = !releaseTagsExist || !otacertsExist || canRunSu
      verbose(l"checkIfRooted, isDeviceRooted: $isDeviceRooted")
      isDeviceRooted
    }
  }

  val displayBlockingDialog: Action = { context =>
    showBlockingDialog(context, "Device is rooted", "Device is rooted")
    Future.successful(())
  }
}
