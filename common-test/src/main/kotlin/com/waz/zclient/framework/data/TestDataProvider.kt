package com.waz.zclient.framework.data

abstract class TestDataProvider<T> {

    abstract fun data(): T

    fun listOfData(numberOfItems: Int = DEFAULT_NUMBER_OF_ITEMS): List<T> {
        val list = mutableListOf<T>()
        repeat(numberOfItems) {
            list.add(data())
        }
        return list
    }

    companion object {
        private const val DEFAULT_NUMBER_OF_ITEMS = 3
    }
}
