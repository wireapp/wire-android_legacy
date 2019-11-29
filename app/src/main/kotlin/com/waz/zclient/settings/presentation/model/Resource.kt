package com.waz.zclient.settings.presentation.model

data class Resource<T>(
    val status: ResourceStatus,
    val data: T?,
    val message: String?
)
