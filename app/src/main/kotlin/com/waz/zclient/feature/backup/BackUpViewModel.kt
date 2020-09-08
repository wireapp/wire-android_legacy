package com.waz.zclient.feature.backup

import androidx.lifecycle.ViewModel
import com.waz.zclient.core.usecase.DefaultUseCaseExecutor
import com.waz.zclient.core.usecase.UseCaseExecutor
import com.waz.zclient.feature.backup.usecase.CreateBackUpUseCase
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.usecase.CreateBackUpUseCaseParams
import com.waz.zclient.feature.backup.usecase.RestoreBackUpUseCase
import com.waz.zclient.feature.backup.usecase.RestoreBackUpUseCaseParams
import java.io.File

class BackUpViewModel(
    private val createBackUpUseCase: CreateBackUpUseCase,
    private val restoreBackUpUseCase: RestoreBackUpUseCase
) : ViewModel(), UseCaseExecutor by DefaultUseCaseExecutor() {

    fun createBackup(
        userId: String,
        clientId: String,
        handle: String,
        password: String,
        onFinish: (Either<Failure, File>) -> Unit
    ): Unit =
        createBackUpUseCase(viewModelScope, CreateBackUpUseCaseParams(userId, clientId, handle, password)) {
            onFinish(it)
        }

    fun restoreBackup(backupFile: File, userId: String, password: String, onFinish: (Either<Failure, Unit>) -> Unit): Unit =
        restoreBackUpUseCase(viewModelScope, RestoreBackUpUseCaseParams(backupFile, userId, password)) {
            onFinish(it)
        }
}
