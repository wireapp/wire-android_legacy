package com.waz.zclient.devices.mapper

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

fun Array<ClientEntity>.toDomainList() = map {
    it.toDomain()
}
