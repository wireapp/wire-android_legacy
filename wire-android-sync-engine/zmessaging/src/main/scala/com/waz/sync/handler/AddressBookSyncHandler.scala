/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.sync.handler

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.AddressBook
import com.waz.service._
import com.waz.service.tracking.TrackingService
import com.waz.sync.SyncResult
import com.waz.sync.client.AddressBookClient
import com.waz.threading.Threading

import scala.concurrent.Future

class AddressBookSyncHandler(contacts: ContactsServiceImpl,
                             client: AddressBookClient,
                             tracking: TrackingService) extends DerivedLogTag {

  import Threading.Implicits.Background
  def postAddressBook(ab: AddressBook): Future[SyncResult] = {
    verbose(l"postAddressBook()")
    if (ab == AddressBook.Empty) Future.successful(SyncResult.Success)
    else {
      // TODO: post incremental changes only - once backend supports that
      for {
        postRes <- client.postAddressBook(ab).future
        result  <- postRes match {
          case Left(error) =>
            Future.successful(SyncResult(error))
          case Right(users) =>
            contacts.onAddressBookUploaded(ab, users)
              .map(_ => SyncResult.Success)
              .recover {
                case e: Throwable =>
                  SyncResult(e)
              }
        }
      } yield result
    }
  }
}
