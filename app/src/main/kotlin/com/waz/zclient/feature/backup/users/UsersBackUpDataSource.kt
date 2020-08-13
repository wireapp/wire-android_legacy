package com.waz.zclient.feature.backup.users

import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.users.model.UsersEntity
import java.io.File

class UsersBackUpDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<UsersEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<UsersBackUpModel, File>,
    override val mapper: BackUpDataMapper<UsersBackUpModel, UsersEntity>
) : BackUpDataSource<UsersBackUpModel, UsersEntity>()
