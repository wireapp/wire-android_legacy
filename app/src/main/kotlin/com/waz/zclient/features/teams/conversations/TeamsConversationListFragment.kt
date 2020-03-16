package com.waz.zclient.features.teams.conversations

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.core.ui.list.RecyclerViewItemClickListener
import com.waz.zclient.features.teams.conversations.list.TeamConversationItem
import com.waz.zclient.features.teams.conversations.list.TeamConversationListAdapter
import com.waz.zclient.features.teams.di.TEAMS_SCOPE_ID
import kotlinx.android.synthetic.main.fragment_teams_conversations.*

class TeamsConversationListFragment : Fragment(R.layout.fragment_teams_conversations) {

    private val teamsConversationViewModel by viewModel<TeamsConversationsViewModel>(TEAMS_SCOPE_ID)

    private val teamsConversationListAdapter by lazy {
        TeamConversationListAdapter()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initConversationList()
        observeTeamConversations()
        observeJoinConversation()
        teamsConversationViewModel.loadData()

        teamsConversationViewModel.errorLiveData.observe(viewLifecycleOwner) {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    private fun observeJoinConversation() {
        teamsConversationViewModel.joinConversationLiveData.observe(viewLifecycleOwner) {
            Toast.makeText(context, "You've now joined the Revolution. Well done!", Toast.LENGTH_LONG).show()
        }
    }

    private fun initConversationList() {
        teamConversationsFragmentConversationListRecyclerView.adapter = teamsConversationListAdapter
        teamsConversationListAdapter.setOnItemClickListener(object : RecyclerViewItemClickListener<TeamConversationItem> {
            override fun onItemClicked(item: TeamConversationItem) {
                teamsConversationViewModel.onConversationItemClicked(item.title)
            }
        })
    }

    private fun observeTeamConversations() {
        teamsConversationViewModel.teamConversationsLiveData.observe(viewLifecycleOwner) {
            teamsConversationListAdapter.updateTeams(it)
        }
    }

    companion object {
        fun newInstance() = TeamsConversationListFragment()
    }

}
