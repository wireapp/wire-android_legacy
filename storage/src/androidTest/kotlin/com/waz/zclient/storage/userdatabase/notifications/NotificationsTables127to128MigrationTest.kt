package com.waz.zclient.storage.userdatabase.notifications

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@ExperimentalCoroutinesApi
class NotificationsTables127to128MigrationTest : UserDatabaseMigrationTest(127, 128) {

    @Test
    fun givenCloudNotificationsStatInsertedIntoCloudNotificationsStatsTableVersion127_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val stage = "testStage"
        val firstBucket = 1
        val secondBucket = 2
        val thirdBucket = 3

        CloudNotificationsStatsTableTestHelper.insertCloudNotificationStat(stage, firstBucket,
            secondBucket, thirdBucket, openHelper = testOpenHelper)

        validateMigration(USER_DATABASE_MIGRATION_127_TO_128)
    }

    @Test
    fun givenCloudNotificationInsertedIntoCloudNotificationsTableVersion127_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val id = "testId"
        val stage = "testStage"
        val stageStartTime = 333344994

        CloudNotificationsTableTestHelper.insertCloudNotification(id, stage, stageStartTime,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_127_TO_128)
    }

    @Test
    fun givenNotificationDataInsertedIntoNotificationDataTableVersion127_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val id = "testId"
        val data = "testData"

        NotificationDataTableTestHelper.insertNotificationData(
            id = id,
            data = data,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_127_TO_128)
    }

    @Test
    fun givenPushNotificationEventInsertedIntoPushNotificationEventsDataTableVersion127_whenMigratedToVersion128_thenAssertDataIsStillIntact() {


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

        validateMigration(USER_DATABASE_MIGRATION_127_TO_128)

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

    private suspend fun allPushNotificationEvents() =
        getDatabase().pushNotificationEventDao().allPushNotificationEvents()
}
