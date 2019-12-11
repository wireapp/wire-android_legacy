package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.network.api.client.ClientsService
import com.waz.zclient.features.clients.ClientEntity

/**
 * This demonstrates the usage of the Network API.
 * TODO: Remove this when used and implemented somewhere else.
 */
abstract class ClientsRepository(private val remoteDataSource: ClientsRemoteDataSource) {

    //Data mapping/transformation should happen at this level and repositories should
    //always deal with Entities and return Domain types
    fun allClients(): Either<Failure, List<ClientDomain>> =
        remoteDataSource.allClients().map { clientEntities -> clientEntities.map { ClientDomain.empty() } }

    //Data mapping/transformation should happen at this level and repositories should
    //always deal with Entities and return Domain types
    fun clientById(clientId: String?): Either<Failure, ClientDomain> =
        remoteDataSource.clientById(clientId).map { ClientDomain.empty() }
}

/**
 * Example Remote Data Source that perform network requests.
 * TODO: Remove this when used and implemented somewhere else.
 */
class ClientsRemoteDataSource(private val clientsService: ClientsService) {

    fun allClients(): Either<Failure, List<ClientEntity>> = clientsService.allClients()
    fun clientById(clientId: String?): Either<Failure, ClientEntity> = clientsService.clientById(clientId)
}

/**
 * This class should be named Client since it is a domain class but
 * for the time being it is for understanding that the type returned by
 * the repository belongs to the domain layer.
 */
data class ClientDomain(private val name: String) {
    companion object { fun empty() = ClientDomain(String.empty()) }
}
