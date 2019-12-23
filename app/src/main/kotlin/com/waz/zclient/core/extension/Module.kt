package com.waz.zclient.core.extension

import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module


fun Module.load() {
    loadKoinModules(this)
}

fun Module.unload() {
    unloadKoinModules(this)
}
