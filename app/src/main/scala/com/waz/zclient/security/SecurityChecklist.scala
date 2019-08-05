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
package com.waz.zclient.security

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading.Implicits.Background
import com.waz.zclient.security.SecurityChecklist._
import com.waz.zclient.log.LogUI._

import scala.concurrent.Future

class SecurityChecklist(list: List[(Check, List[Action])]) extends DerivedLogTag {

  def run(): Future[Boolean] = {
    if (list.isEmpty) {
      Future.successful(true)
    } else {
      info(l"Running security checks")
      runChecks(list)
    }
  }

  private def runChecks(checks: List[(Check, List[Action])]): Future[Boolean] = checks match {
    case Nil =>
      Future.successful(true)
    case (check, actions) :: tail =>
      check.isSatisfied.flatMap {
        case true  => runChecks(tail)
        case false => runActions(actions).map(_ => false)
      }
  }

  private def runActions(actions: List[Action]): Future[Unit] = actions match {
    case Nil            => Future.successful(())
    case action :: tail => action.execute().flatMap(_ => runActions(tail))
  }
}

object SecurityChecklist {

  def apply(args: (Check, List[Action])*): SecurityChecklist = new SecurityChecklist(args.toList)

  trait Check {
    def isSatisfied: Future[Boolean]
  }

  trait Action {
    def execute(): Future[Unit]
  }
}
