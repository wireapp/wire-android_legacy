package com.waz.zclient.shared.user.profile

import com.waz.zclient.core.usecase.ObservableUseCase
import com.waz.zclient.shared.assets.usecase.PublicAsset
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

@ExperimentalCoroutinesApi
class GetUserProfilePictureUseCase(
    private val usersRepository: UsersRepository,
    private val profilePictureMapper: ProfilePictureMapper
) : ObservableUseCase<ProfilePictureAsset, Unit>() {

    override suspend fun run(params: Unit): Flow<ProfilePictureAsset> =
        usersRepository.profileDetails().mapNotNull { it.picture }.map { profilePictureMapper.map(it) }
}

class ProfilePictureMapper {
    fun map(assetId: String) = ProfilePictureAsset(assetId)
}

data class ProfilePictureAsset(private val id: String) : PublicAsset(id)
