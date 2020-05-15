package com.waz.db.migration.userdatabase

import com.waz.api.ErrorType
import com.waz.api.impl.ErrorResponse
import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.ErrorData
import com.waz.model.ErrorData.ErrorDataDao
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.errors.ErrorsEntity

class ErrorsMigrationTest extends UserDatabaseMigrationTest {
  feature("Erros table migration") {
    scenario("Erros migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val errorResponse = ErrorResponse(0, "error msg", "error label")
      val errorData = ErrorData(ErrorType.CANNOT_SEND_VIDEO, errorResponse)
      ErrorDataDao.insertOrReplace(Seq(errorData))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertErrorsEntity(_, new ErrorsEntity(
          errorData.id.str, "CANNOT_SEND_VIDEO", "", "",
          null, 0,
          "error msg", "error label", errorData.time.getTime.toInt
        ))
      })
    }
  }
}

