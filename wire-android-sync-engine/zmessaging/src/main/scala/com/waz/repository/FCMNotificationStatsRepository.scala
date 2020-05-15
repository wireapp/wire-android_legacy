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
package com.waz.repository

import com.waz.content.Database
import com.waz.db.Col.{int, text}
import com.waz.db.Dao
import com.waz.model.FCMNotification
import com.waz.repository.FCMNotificationsRepository.FCMNotificationsDao
import com.waz.threading.Threading
import com.waz.utils.wrappers.DBCursor
import com.waz.utils.Identifiable

import scala.concurrent.{ExecutionContext, Future}

/**
  * @param stage the stage for which these buckets apply
  * @param bucket1 notifications which were processed in 0-10s
  * @param bucket2 notifications which were processed in 10s-30m
  * @param bucket3 notifications which were processed in 30m+
  */
case class FCMNotificationStats(stage:   String,
                                bucket1: Int,
                                bucket2: Int,
                                bucket3: Int) extends Identifiable[String] {
  override def id: String = stage
}

trait FCMNotificationStatsRepository {
  def writeTimestampAndStats(update: FCMNotificationStats, stageTimestamp: FCMNotification): Future[Unit]
  def listAllStats(): Future[Vector[FCMNotificationStats]]
}

class FCMNotificationStatsRepositoryImpl(fcmTimestamps: FCMNotificationsRepository)
                                        (implicit val db: Database, ec: ExecutionContext)
  extends FCMNotificationStatsRepository {

  import com.waz.repository.FCMNotificationStatsRepository.FCMNotificationStatsDao._

  override def writeTimestampAndStats(update: FCMNotificationStats, stageTimestamp: FCMNotification): Future[Unit] =
    db.withTransaction { implicit db =>
      FCMNotificationsDao.insertOrIgnore(stageTimestamp)
      insertOrReplace(getById(update.stage) match {
        case Some(stageRow) =>
          stageRow.copy(bucket1 = stageRow.bucket1 + update.bucket1,
            bucket2 = stageRow.bucket2 + update.bucket2,
            bucket3 = stageRow.bucket3 + update.bucket3)
        case _ => update
      })
      if (update.stage == FCMNotification.FinishedPipeline)
        FCMNotificationsDao.deleteEvery(FCMNotification.everyStage.map((stageTimestamp.id, _)))
    }.andThen { case _ =>
      //this op is not done as part of the transaction because it's not essential, it's just an
      //extra check to ensure the FCM timestamps table doesn't grow overly long due to bugs
      if (update.stage == FCMNotification.FinishedPipeline)
        fcmTimestamps.trimExcessRows()
    }

  override def listAllStats(): Future[Vector[FCMNotificationStats]] = db.read(list(_))
}

object FCMNotificationStatsRepository {

  implicit object FCMNotificationStatsDao extends Dao[FCMNotificationStats, String] {
    val Stage = text('stage, "PRIMARY KEY")(_.stage)
    val Bucket1 = int('bucket1)(_.bucket1)
    val Bucket2 = int('bucket2)(_.bucket2)
    val Bucket3 = int('bucket3)(_.bucket3)

    override val idCol = Stage
    override val table = Table("FCMNotificationStats", Stage, Bucket1, Bucket2, Bucket3)

    override def apply(implicit cursor: DBCursor): FCMNotificationStats =
      FCMNotificationStats(Stage, Bucket1, Bucket2, Bucket3)
  }

}

