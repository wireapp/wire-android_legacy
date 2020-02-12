package com.waz.zclient.settings.account.deleteaccount

import com.waz.zclient.accounts.AccountsRepository
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class DeleteAccountUseCase(private val accountsRepository: AccountsRepository) : UseCase<Unit, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, Unit> =
        accountsRepository.deleteAccountPermanently()
}
