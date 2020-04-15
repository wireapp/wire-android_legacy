package com.waz.zclient.feature.settings.account.deleteaccount

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.accounts.AccountsRepository

class DeleteAccountUseCase(private val accountsRepository: AccountsRepository) : UseCase<Unit, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, Unit> =
        accountsRepository.deleteAccountPermanently()
}
