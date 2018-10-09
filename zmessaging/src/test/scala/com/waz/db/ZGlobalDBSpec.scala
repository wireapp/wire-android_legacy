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
package com.waz.db

import com.waz.model.KeyValueData.KeyValueDataDao
import com.waz.model._
import com.waz.utils.DbLoader
import com.waz.utils.wrappers.DB
import org.robolectric.Robolectric
import org.scalatest._

class ZGlobalDBSpec extends FeatureSpec with Matchers with OptionValues with Inspectors with BeforeAndAfter with RobolectricTests with DbLoader {
  lazy val dbHelper = new ZGlobalDB(Robolectric.application)

  after {
    dbHelper.close()
    Robolectric.application.getDatabasePath(dbHelper.getDatabaseName).delete()
  }

  feature("Database migrations") {

    def createZmessagingDb(id: AccountId, userId: UserId) = {
      val zdb = new ZMessagingDB(Robolectric.application, id.str)
      implicit val db: DB  = zdb.getWritableDatabase
      KeyValueDataDao.insertOrIgnore(KeyValueData("self_user_id", userId.str))
      db.close()
      zdb.close()
    }

    lazy val userId1 = UserId()
    lazy val userId2 = UserId()

    scenario("Prepare zms databases") {
      createZmessagingDb(AccountId("8546c628-c9e8-45d6-82dd-7f6dcb56e171"), userId1)
      createZmessagingDb(AccountId("09621ddd-736f-4ec5-b4b5-d24cbb56b9f3"), userId2)
    }

    //TODO - create new ZGlobalDB tests

  }
}
