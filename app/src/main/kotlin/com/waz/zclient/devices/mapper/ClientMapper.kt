package com.waz.zclient.devices.mapper

import com.waz.zclient.core.resources.Resource
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

fun Resource<Array<ClientEntity>>.toDomainList()= Resource(
    status = status,
    data = data?.map {
        it.toDomain()
    },
    message = message
)

fun Resource<ClientEntity>.toDomainObject() = Resource(
    status = status,
    data = data?.toDomain(),
    message = message
)
