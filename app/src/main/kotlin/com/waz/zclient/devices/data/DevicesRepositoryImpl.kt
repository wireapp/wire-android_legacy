package com.waz.zclient.devices.data

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import com.waz.zclient.core.data.resultLiveData
import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.devices.data.source.remote.DevicesRemoteDataSource
import com.waz.zclient.devices.data.source.remote.DevicesRemoteDataSourceImpl
import com.waz.zclient.devices.model.DeviceEntity

class DevicesRepositoryImpl(private val remoteDataSource: DevicesRemoteDataSource) : DevicesRepository {

    override fun getCurrentDeviceDetails(clientId: String): LiveData<RequestResult<DeviceEntity>> =
        resultLiveData(networkCall = { remoteDataSource.getCurrentDeviceData(clientId) })

    companion object {

        @Volatile
        @VisibleForTesting
        internal var instance: DevicesRepositoryImpl? = null

        fun getInstance(remoteDataSource: DevicesRemoteDataSource = DevicesRemoteDataSourceImpl()): DevicesRepository =
            instance ?: synchronized(this) {
                instance ?: DevicesRepositoryImpl(remoteDataSource).also {
                    instance = it
                }
            }
    }
}
