package com.waz.zclient.shared.clients.mapper

import com.waz.zclient.shared.clients.Client
import com.waz.zclient.shared.clients.ClientLocation
import com.waz.zclient.shared.clients.datasources.remote.ClientResponse
import com.waz.zclient.storage.db.clients.model.ClientEntity

class ClientMapper {

    fun toClient(clientEntity: ClientEntity) = with(clientEntity) {
        Client(
            time = time,
            label = label,
            clazz = clazz,
            type = type,
            id = id,
            model = model,
            verification = verification,
            macKey = macKey,
            encKey = encKey,
            location = ClientLocation(long = lon, lat = lat, name = locationName))
    }

    fun toClient(clientResponse: ClientResponse) = with(clientResponse) {
        Client(
            cookie = cookie,
            time = time,
            label = label,
            clazz = clazz,
            type = type,
            id = id,
            model = model,
            location = ClientLocation(lat = location.lat, long = location.long))
    }

    fun toClientDao(client: Client) = with(client) {
        ClientEntity(
            id = id,
            time = time,
            label = label,
            type = type,
            clazz = clazz,
            model = model,
            lat = location.lat,
            lon = location.long,
            locationName = location.name,
            encKey = encKey,
            macKey = macKey,
            verification = verification
        )
    }

    fun toListOfClientDao(list: List<Client>): List<ClientEntity> = list.map {
        toClientDao(it)
    }

    @JvmName("clientDaoToClients")
    fun toListOfClients(list: List<ClientEntity>): List<Client> = list.map {
        toClient(it)
    }

    @JvmName("clientApiToClients")
    fun toListOfClients(list: List<ClientResponse>): List<Client> = list.map {
        toClient(it)
    }
}
