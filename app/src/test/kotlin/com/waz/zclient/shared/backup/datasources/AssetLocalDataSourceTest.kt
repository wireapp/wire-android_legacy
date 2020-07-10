package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.AssetsJSONEntity
import com.waz.zclient.storage.db.assets.AssetsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.amshove.kluent.shouldEqual
import org.junit.Test
import java.lang.Exception
import java.lang.StringBuilder

import com.waz.zclient.shared.backup.datasources.BackupDataJSONConverter.Companion.toByteArray
import com.waz.zclient.shared.backup.datasources.BackupDataJSONConverter.Companion.toIntArray

@ExperimentalCoroutinesApi
class AssetLocalDataSourceTest : UnitTest() {
    private fun serialize(bytes: ByteArray): String {
        val sb = StringBuilder()
        sb.append('[');
        sb.append(toIntArray(bytes)?.joinToString())
        sb.append(']')

        return sb.toString()
    }

    private fun serializeNullable(bytes: ByteArray?): String = if (bytes == null) "[]" else serialize(bytes)

    private fun deserializeByteArray(str: String): ByteArray? =
            try {
                val sub = str.subSequence(IntRange(str.indexOfFirst { it == '[' } + 1, str.indexOfLast { it == ']' } - 1))
                toByteArray(sub.split(',').map { it.trim().toInt() }.toIntArray())
            } catch (e: Exception){
                null
            }

    private val assetsEntity = AssetsEntity(
            id = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
            token = "5UFmZ-Bmy1NP5Ninrc21XQ==",
            name = "",
            encryption = "AES_CBS__TzF3CPcCs6lCuLRISq64MAByIAm/TELGUj9XXdTHKF0",
            mime = "image/png",
            sha = ByteArray(256) { it.toByte() },
            size = 1796931,
            source = null,
            preview = null,
            details = "{\"ImageDetails\":{\"dimensions\":{\"width\":1080,\"height\":1080}}}",
            conversationId = null
    )

    private val json = Json(JsonConfiguration.Stable.copy(isLenient = true, ignoreUnknownKeys = true))

    @Test
    fun `serialize and deserialize a byte array`(): Unit {
        val byteArray = assetsEntity.sha!!
        val str: String = serialize(byteArray)
        val result: ByteArray = deserializeByteArray(str)!!

        result.size shouldEqual byteArray.size
        for (i in IntRange(0, result.size - 1)) {
            result[i] shouldEqual byteArray[i]
        }
    }

    @Test
    fun `convert an asset entity to a json entity and back`() = run {
        val assetsJSONEntity = AssetsJSONEntity.from(assetsEntity)
        val result: AssetsEntity = assetsJSONEntity.toEntity()

        result.id shouldEqual assetsEntity.id
    }

    @Test
    fun `convert a json string to an asset entity`(): Unit = run {
        val jsonStr = """{
            "id": "${assetsEntity.id}",
            "token": "${assetsEntity.token}",
            "name": "${assetsEntity.name}",
            "encryption": "${assetsEntity.encryption}",
            "mime": "${assetsEntity.mime}",
            "sha": ${serializeNullable(assetsEntity.sha)},
            "size": ${assetsEntity.size},
            "details": "${assetsEntity.details.replace("\"", "\\\"")}"
        }""".trimIndent()

        println(jsonStr)

        val result = json.parse(AssetsJSONEntity.serializer(), jsonStr).toEntity()

        result.id shouldEqual assetsEntity.id
    }

    @Test
    fun `convert an asset entity to json string and back`(): Unit = run {
        val jsonStr = json.stringify(AssetsJSONEntity.serializer(), AssetsJSONEntity.from(assetsEntity))
        println(jsonStr)

        val result = json.parse(AssetsJSONEntity.serializer(), jsonStr).toEntity()

        result.id shouldEqual assetsEntity.id
    }
}