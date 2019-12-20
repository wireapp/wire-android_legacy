package com.waz.zclient.core.extension

import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module


fun Module.load() {

    val loadModule by lazy { loadKoinModules(this) }
    loadModule
}

fun Module.unload() {
    unloadKoinModules(this)
}
