package com.waz.zclient.devices.mapper

import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.devices.data.model.ClientEntity
import com.waz.zclient.devices.data.model.ClientLocationEntity
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.devices.domain.model.ClientLocation

fun ClientEntity.toDomain() = Client(
    cookie = cookie,
    time = time,
    label = label,
    _class = _class,
    type = type,
    id = id,
    model = model,
    location = location.toDomain()
)

fun ClientLocationEntity.toDomain() = ClientLocation(
    long = long,
    lat = lat
)

fun RequestResult<Array<ClientEntity>>.toDomainList() = RequestResult(
    status = status,
    data = data?.map {
        it.toDomain()
    },
    message = message
)

fun RequestResult<ClientEntity>.toDomainObject() = RequestResult(
    status = status,
    data = data?.toDomain(),
    message = message
)
