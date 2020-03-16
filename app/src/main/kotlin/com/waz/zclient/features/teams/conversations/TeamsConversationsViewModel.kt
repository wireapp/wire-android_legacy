package com.waz.zclient.features.teams.conversations

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.accounts.ActiveAccount
import com.waz.zclient.accounts.usecase.GetActiveAccountUseCase
import com.waz.zclient.conversations.usecase.JoinConversationParams
import com.waz.zclient.conversations.usecase.JoinConversationUseCase
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.logging.Logger
import com.waz.zclient.features.teams.conversations.list.TeamConversationItem
import com.waz.zclient.features.teams.conversations.mapper.TeamsConversationMapper
import com.waz.zclient.features.teams.usecase.GetAllTeamConversationsParams
import com.waz.zclient.features.teams.usecase.GetAllTeamConversationsUseCase

class TeamsConversationsViewModel(
    private val getAlTeamsConversationsUseCase: GetAllTeamConversationsUseCase,
    private val getActiveAccountUseCase: GetActiveAccountUseCase,
    private val joinConversationUseCase: JoinConversationUseCase,
    private val teamsConversationMapper: TeamsConversationMapper
) : ViewModel() {

    private val _teamsConversationsLiveData = MutableLiveData<List<TeamConversationItem>>()
    private val _errorLiveData = MutableLiveData<String>()
    private val _joinConversationLiveData = MutableLiveData<Unit>()

    val teamConversationsLiveData: LiveData<List<TeamConversationItem>> = _teamsConversationsLiveData
    val errorLiveData: LiveData<String> = _errorLiveData
    val joinConversationLiveData: LiveData<Unit> = _joinConversationLiveData

    fun loadData() {
        getActiveAccountUseCase(viewModelScope, Unit) {
            it.fold(::handleFailure, ::handleActiveAccountSuccess)
        }
    }

    fun onConversationItemClicked(convId: String) {
        joinConversationUseCase(viewModelScope, JoinConversationParams(convId)) {
            it.fold(::handleFailure, ::handleJoinConversationSuccess)
        }
    }

    private fun handleJoinConversationSuccess(unit: Unit) =
        _joinConversationLiveData.postValue(Unit)

    private fun handleActiveAccountSuccess(activeAccount: ActiveAccount) {
        getAlTeamsConversationsUseCase(viewModelScope, GetAllTeamConversationsParams(activeAccount.teamId)) {
            it.fold(::handleFailure, ::handleTeamsSuccess)
        }
    }

    private fun handleTeamsSuccess(teamConversations: List<TeamConversation>) {
        Logger.error("TeamViewModel", teamConversations.toString())
        _teamsConversationsLiveData.postValue(teamConversations.map {
            teamsConversationMapper.toTeamConversationItem(it)
        })
    }

    private fun handleFailure(failure: Failure) {
        _errorLiveData.postValue("Issue with API request. Failed with $failure")
    }
}
