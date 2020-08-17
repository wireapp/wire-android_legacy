package com.waz.zclient

import android.content.Context
import com.waz.model.UserId
import com.waz.zclient.audio.AudioService
import com.waz.zclient.audio.AudioServiceImpl
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess

import com.waz.zclient.feature.backup.BackUpViewModel

import org.koin.java.KoinJavaComponent.inject
import java.io.File

object KotlinServices {

    lateinit var audioService: AudioService

    private val backUpViewModel: BackUpViewModel by lazy { inject(BackUpViewModel::class.java).value }

    fun init(context: Context) {
        audioService = AudioServiceImpl(context)
    }

    fun createBackup(userId: UserId, handle: String, password: String, onSuccess: (File) -> Unit, onFailure: (String) -> Unit): Unit =
        backUpViewModel.createBackup(userId, handle, password) { result ->
            result.onSuccess(onSuccess).onFailure { onFailure(it.toString()) }
        }
}
