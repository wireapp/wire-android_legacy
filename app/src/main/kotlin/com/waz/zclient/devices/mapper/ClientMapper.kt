package com.waz.zclient.devices.mapper

import com.waz.zclient.storage.clients.model.ClientEntity
import com.waz.zclient.storage.clients.model.ClientLocationEntity
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.devices.domain.model.ClientLocation

fun ClientEntity.toClient() = Client(
    cookie = cookie,
    time = time,
    label = label,
    _class = _class,
    type = type,
    id = id,
    model = model,
    location = location.toClient()
)

fun ClientLocationEntity.toClient() = ClientLocation(
    long = long,
    lat = lat
)

fun Array<ClientEntity>.toListOfClients() = map {
    it.toClient()
}
