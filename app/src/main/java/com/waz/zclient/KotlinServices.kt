package com.waz.zclient

import android.content.Context
import com.waz.zclient.audio.AudioService
import com.waz.zclient.audio.AudioServiceImpl

object KotlinServices {

    fun init(context: Context) {
        audioService = AudioServiceImpl(context)
    }

    lateinit var audioService: AudioService
}