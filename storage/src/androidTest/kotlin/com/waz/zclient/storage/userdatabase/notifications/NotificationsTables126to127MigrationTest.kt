package com.waz.zclient.storage.userdatabase.notifications

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class NotificationsTables126to127MigrationTest : UserDatabaseMigrationTest(126,
    127, USER_DATABASE_MIGRATION_126_TO_127) {

    @Test
    fun givenCloudNotificationsStatInsertedIntoCloudNotificationsStatsTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val stage = "testStage"
        val firstBucket = 1
        val secondBucket = 2
        val thirdBucket = 3

        CloudNotificationsStatsTableTestHelper.insertCloudNotificationStat(stage, firstBucket,
            secondBucket, thirdBucket, openHelper = testOpenHelper)

        validateMigration()

        runBlocking {
            with(allCloudNotificationStats()[0]) {
                assert(this.stage == stage)
                assert(this.firstBucket == firstBucket)
                assert(this.secondBucket == secondBucket)
                assert(this.thirdBucket == thirdBucket)
            }
        }
    }

    @Test
    fun givenCloudNotificationInsertedIntoCloudNotificationsTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val id = "testId"
        val stage = "testStage"
        val stageStartTime = 333344994

        CloudNotificationsTableTestHelper.insertCloudNotification(id, stage, stageStartTime,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            with(allCloudNotifications()[0]) {
                assert(this.id == id)
                assert(this.stage == stage)
                assert(this.stageStartTime == stageStartTime)
            }
        }
    }

    @Test
    fun givenNotificationDataInsertedIntoNotificationDataTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val id = "testId"
        val data = "testData"

        NotificationDataTableTestHelper.insertNotificationData(
            id = id,
            data = data,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            with(allNotificationsData()[0]) {
                assert(this.id == id)
                assert(this.data == data)
            }
        }
    }

    @Test
    fun givenPushNotificationEventInsertedIntoPushNotificationEventsDataTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {


        val eventIndex = 1
        val pushId = "testId"
        val isDecrypted = true
        val eventJson = "testEvent"
        val plain = ByteArray(5)
        val isTransient = false

        PushNotificationEventsTableTestHelper.insertPushNotificationEvent(
            eventIndex = eventIndex,
            pushId = pushId,
            isDecrypted = isDecrypted,
            eventJson = eventJson,
            plain = plain,
            isTransient = isTransient,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            with(allPushNotificationEvents()[0]) {
                assert(this.eventIndex == eventIndex)
                assert(this.pushId == pushId)
                assert(this.isDecrypted == isDecrypted)
                assert(this.eventJson == eventJson)
                assert(this.plain!!.contentEquals(plain)  )
                assert(this.isTransient == isTransient)
            }
        }
    }

    private suspend fun allCloudNotifications() =
        getUserDatabase().cloudNotificationsDao().allCloudNotifications()

    private suspend fun allCloudNotificationStats() =
        getUserDatabase().cloudNotificationStatsDao().allCloudNotificationStats()

    private suspend fun allNotificationsData() =
        getUserDatabase().notificationDataDao().allNotificationsData()

    private suspend fun allPushNotificationEvents() =
        getUserDatabase().pushNotificationEventDao().allPushNotificationEvents()
}
