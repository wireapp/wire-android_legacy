package com.waz.zclient.shared.backup.datasources

import java.lang.Exception
import java.lang.StringBuilder

interface BackupDataJSONConverter {

    companion object {
        fun toIntArray(bytes: ByteArray?): IntArray? = bytes?.map { it.toInt() }?.toIntArray()
        fun toByteArray(ints: IntArray?): ByteArray? = ints?.map { it.toByte() }?.toByteArray()
    }
}

class BackupDataKotlinxConverter: BackupDataJSONConverter {

}
