package com.waz.zclient.devices.data.source

import com.waz.zclient.devices.data.source.remote.model.ClientApi
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.devices.domain.model.ClientLocation
import com.waz.zclient.storage.db.clients.model.ClientDao

class ClientMapper {

    fun toClient(clientDao: ClientDao) = with(clientDao) {
        Client(
            time = time,
            label = label,
            _class = _class,
            type = type,
            id = id,
            model = model,
            verification = verification,
            macKey = macKey,
            encKey = encKey,
            location = ClientLocation(long = lon, lat = lat, name = locationName))
    }

    fun toClient(clientApi: ClientApi) = with(clientApi) {
        Client(
            cookie = cookie,
            time = time,
            label = label,
            _class = _class,
            type = type,
            id = id,
            model = model,
            location = ClientLocation(lat = location.lat, long = location.long))
    }

    fun toClientDao(client: Client) = with(client) {
        ClientDao(
            id = id,
            time = time,
            label = label,
            type = type,
            _class = _class,
            model = model,
            lat = location.lat,
            lon = location.long,
            locationName = location.name,
            encKey = encKey,
            macKey = macKey,
            verification = verification
        )
    }

    fun toListOfClientDao(list: List<Client>): List<ClientDao> = list.map {
        toClientDao(it)
    }

    @JvmName("clientDaoToClients")
    fun toListOfClients(list: List<ClientDao>): List<Client> = list.map {
        toClient(it)
    }

    @JvmName("clientApiToClients")
    fun toListOfClients(list: List<ClientApi>): List<Client> = list.map {
        toClient(it)
    }
}
