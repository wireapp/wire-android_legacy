package com.waz.zclient.storage.userdatabase.notifications

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@ExperimentalCoroutinesApi
class NotificationsTables126to128MigrationTest : UserDatabaseMigrationTest(126, 128) {

    @Test
    fun givenCloudNotificationsStatInsertedIntoCloudNotificationsStatsTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val stage = "testStage"
        val firstBucket = 1
        val secondBucket = 2
        val thirdBucket = 3

        CloudNotificationsStatsTableTestHelper.insertCloudNotificationStat(stage, firstBucket,
            secondBucket, thirdBucket, openHelper = testOpenHelper)

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allCloudNotificationStats()[0]) {
                assertEquals(this.stage, stage)
                assertEquals(this.firstBucket, firstBucket)
                assertEquals(this.secondBucket, secondBucket)
                assertEquals(this.thirdBucket, thirdBucket)
            }
        }
    }

    @Test
    fun givenCloudNotificationInsertedIntoCloudNotificationsTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val id = "testId"
        val stage = "testStage"
        val stageStartTime = 333344994

        CloudNotificationsTableTestHelper.insertCloudNotification(id, stage, stageStartTime,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allCloudNotifications()[0]) {
                assertEquals(this.id, id)
                assertEquals(this.stage, stage)
                assertEquals(this.stageStartTime, stageStartTime)
            }
        }
    }

    @Test
    fun givenNotificationDataInsertedIntoNotificationDataTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val id = "testId"
        val data = "testData"

        NotificationDataTableTestHelper.insertNotificationData(
            id = id,
            data = data,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allNotificationsData()[0]) {
                assertEquals(this.id, id)
                assertEquals(this.data, data)
            }
        }
    }

    @Test
    fun givenPushNotificationEventInsertedIntoPushNotificationEventsDataTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {


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

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allPushNotificationEvents()[0]) {
                assertEquals(this.eventIndex, eventIndex)
                assertEquals(this.pushId, pushId)
                assertEquals(this.isDecrypted, isDecrypted)
                assertEquals(this.eventJson, eventJson)
                this.plain?.let { assertTrue(it.contentEquals(plain)) }
                assertEquals(this.isTransient, isTransient)
            }
        }
    }

    private suspend fun allCloudNotifications() =
        getDatabase().cloudNotificationsDao().allCloudNotifications()

    private suspend fun allCloudNotificationStats() =
        getDatabase().cloudNotificationStatsDao().allCloudNotificationStats()

    private suspend fun allNotificationsData() =
        getDatabase().notificationDataDao().allNotificationsData()

    private suspend fun allPushNotificationEvents() =
        getDatabase().pushNotificationEventDao().allPushNotificationEvents()
}
