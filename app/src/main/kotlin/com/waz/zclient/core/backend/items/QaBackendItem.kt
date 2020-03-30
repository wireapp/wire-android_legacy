package com.waz.zclient.core.backend.items

class QaBackendItem : BackendItem() {

    override fun environment() = Backend.QA.environment

    override fun baseUrl() = BASE_URL

    companion object {
        private const val BASE_URL = "https://nginz-https.qa-demo.wire.link"
    }
}
