package com.waz.zclient

import android.content.Context
import com.waz.zclient.audio.AudioService
import com.waz.zclient.audio.AudioServiceImpl
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess

import com.waz.zclient.feature.backup.BackUpViewModel
import org.koin.core.KoinComponent
import org.koin.core.get
import java.io.File

object KotlinServices : KoinComponent {

    lateinit var audioService: AudioService

    private val backUpViewModel: BackUpViewModel by lazy { get<BackUpViewModel>() }

    fun init(context: Context) {
        audioService = AudioServiceImpl(context)
    }

    fun createBackup(
        userId: String,
        clientId: String,
        handle: String,
        password: String,
        onSuccess: (File) -> Unit,
        onFailure: (String) -> Unit
    ): Unit =
        backUpViewModel.createBackup(userId, clientId, handle, password) { result ->
            result.onSuccess(onSuccess).onFailure { onFailure(it.toString()) }
        }

    fun restoreBackup(backupFile: File, userId: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit): Unit =
        backUpViewModel.restoreBackup(backupFile, userId, password) { result ->
            result.onSuccess { onSuccess() }.onFailure { onFailure(it.toString()) }
        }
}
