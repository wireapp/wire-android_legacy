package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single

class UserRemoteDataSourceImpl : UserRemoteDataSource {

    private val network = Network()
    //Hardcoded just for testing
    private val apiToken = "dvK4kE1gKeRMYPnmJ3SXSZuKrR1-hp-8StYRigDnt0LuWc7l-Qr73ESzqm1bQe8QM4XLTt50YGunjahC6RfYAA==.v=1.k=1.d=1574777278.t=a.l=.u=aa4e0112-bc8c-493e-8677-9fde2edf3567.c=16912824850823569981"

    override fun getProfile(): Single<UserEntity> = network.getUserApi().getProfile("Bearer " + apiToken)
    override fun updateName(name: String): Completable = network.getUserApi().updateName("Bearer " + apiToken, name)
    override fun updateHandle(handle: String): Completable = network.getUserApi().updateHandle("Bearer " + apiToken, handle)
    override fun updateEmail(email: String): Completable = network.getUserApi().updateEmail("Bearer " + apiToken, email)
    override fun updatePhone(phone: String): Completable = network.getUserApi().updatePhone("Bearer " + apiToken, phone)

}
