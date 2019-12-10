package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.network.api.client.ClientsService

/**
 * This demonstrates the usage of the Network API.
 * TODO: Remove this when used and implemented somewhere else.
 */
interface ClientsRepository {
    fun allClients(): Either<Failure, List<ClientDomain>>
    fun clientById(clientId: String?): Either<Failure, ClientDomain>
}

/**
 * Example Remote Data Source that perform network requests.
 * TODO: Remove this when used and implemented somewhere else.
 */
class ClientsRemoteDataSource(private val clientsService: ClientsService) : ClientsRepository {

    //Data mapping/transformation should happen at this level
    override fun allClients(): Either<Failure, List<ClientDomain>> =
        clientsService.allClients().map { clientEntities -> clientEntities.map { ClientDomain.empty() } }

    //Data mapping/transformation should happen at this level
    override fun clientById(clientId: String?): Either<Failure, ClientDomain> =
        clientsService.clientById(clientId).map { ClientDomain.empty() }
}

/**
 * This class should be named Client since it is a domain class but
 * for the time being it is for understanding that the type returned by
 * the repository belongs to the domain layer.
 */
data class ClientDomain(private val name: String) {
    companion object { fun empty() = ClientDomain(String.empty()) }
}
