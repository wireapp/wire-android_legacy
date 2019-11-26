package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Single

class UserRemoteDataSourceImpl constructor(

) : UserRemoteDataSource {

    private val network =  Network()
    //Hardcoded just for testing
    private val apiToken = "4j27dTgQe-D-wcAuPRtFdzjt_y0rvQweT-oBWF5firdnTnssee4_T9kEKizHXS_fMJ3ty-PtpogmmtEzEMFHDA==.v=1.k=1.d=1574700958.t=a.l=.u=a93240b0-ba89-441e-b8ee-ff4403808f93.c=13798313379856385499"


    override fun getUserProfile(): Single<UserEntity> = network.getUserApi().getUserProfile("Bearer "+apiToken)

}
