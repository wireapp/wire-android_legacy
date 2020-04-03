package com.waz.zclient.core.backend.items

class StagingBackendItem : BackendItem() {

    override fun environment() = Backend.STAGING.environment

    override fun baseUrl() = BASE_URL

    companion object {
        private const val BASE_URL = "https://staging-nginz-https.zinfra.io"
    }
}
