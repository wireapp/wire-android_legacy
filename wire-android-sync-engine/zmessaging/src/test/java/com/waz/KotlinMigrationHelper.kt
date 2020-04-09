package com.waz

import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.property.KeyValuesEntity
import kotlinx.coroutines.runBlocking

object KotlinMigrationHelper {

    @JvmStatic
    fun assertKeyValue(roomDB: UserDatabase, keyValuesEntity: KeyValuesEntity) {
        runBlocking {
            with(roomDB.keyValuesDao().allKeyValues()[0]) {
                assert(this.key == keyValuesEntity.key)
                assert(this.value == keyValuesEntity.value)
            }
        }
    }
}
