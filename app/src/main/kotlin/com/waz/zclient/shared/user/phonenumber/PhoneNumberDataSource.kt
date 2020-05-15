package com.waz.zclient.shared.user.phonenumber

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.shared.user.datasources.local.UsersLocalDataSource
import com.waz.zclient.shared.user.datasources.remote.UsersRemoteDataSource
import kotlinx.coroutines.runBlocking

class PhoneNumberDataSource(
    private val usersRemoteDataSource: UsersRemoteDataSource,
    private val usersLocalDataSource: UsersLocalDataSource
) : PhoneNumberRepository {

    override suspend fun changePhone(phone: String) = changePhoneRemotely(phone)
        .onSuccess { runBlocking { changePhoneLocally(phone) } }

    private suspend fun changePhoneRemotely(phone: String) = usersRemoteDataSource.changePhone(phone)

    private suspend fun changePhoneLocally(phone: String) = usersLocalDataSource.changePhone(phone)

    override suspend fun deletePhone(): Either<Failure, Any> = deletePhoneRemotely()
        .onSuccess { runBlocking { deletePhoneLocally() } }

    private suspend fun deletePhoneLocally() = usersLocalDataSource.deletePhone()

    private suspend fun deletePhoneRemotely() = usersRemoteDataSource.deletePhone()
}
