package com.waz.zclient.utilities.resources

import android.content.res.Resources

class ResourceManagerImpl(val resources: Resources) : ResourceManager {

    override fun getStringArray(id: Int): Array<String> {
        return resources.getStringArray(id)
    }

    override fun getString(id: Int): String {
        return resources.getString(id)
    }
}
