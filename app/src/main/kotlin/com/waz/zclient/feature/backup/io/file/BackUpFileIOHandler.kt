package com.waz.zclient.feature.backup.io.file

import com.waz.zclient.feature.backup.BackUpIOHandler
import java.io.File

class BackUpFileIOHandler<T>(
    private val fileName: String,
    private val jsonConverter: JsonConverter<T>
) : BackUpIOHandler<T> {

    override fun write(iterator: Iterator<T>) {
        val file = File(fileName).also {
            it.delete()
            it.createNewFile()
        }
        while (iterator.hasNext()) {
            val jsonStr = jsonConverter.toJson(iterator.next())
            file.appendText("$jsonStr\n")
        }
    }

    //TODO close bufferedReader in case of exception, error, etc.
    @Suppress("IteratorNotThrowingNoSuchElementException") //lineIterator already throws it
    override fun readIterator(): Iterator<T> {
        val reader = File(fileName).bufferedReader()
        val lineIterator = reader.lineSequence().iterator()

        return object : Iterator<T> {
            var closeReaderAfterNext = false

            override fun hasNext(): Boolean = lineIterator.hasNext().also { hasNext ->
                if (!hasNext) closeReaderAfterNext = true
            }

            override fun next(): T = lineIterator.next().let {
                if (closeReaderAfterNext) reader.close()
                jsonConverter.fromJson(it)
            }
        }
    }
}

class JsonConverter<T> {
    fun fromJson(jsonString: String): T {
        TODO("add kotlinx.serialization")
    }

    fun toJson(model: T): String {
        TODO("add kotlinx.serialization")
    }
}
