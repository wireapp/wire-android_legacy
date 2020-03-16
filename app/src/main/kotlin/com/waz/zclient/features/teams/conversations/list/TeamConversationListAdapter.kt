package com.waz.zclient.features.teams.conversations.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R
import com.waz.zclient.core.ui.list.RecyclerViewItemClickListener
import kotlinx.android.synthetic.main.item_view_team_conversation.view.*

class TeamConversationListAdapter : RecyclerView.Adapter<TeamConversationViewHolder>() {

    private var teamConversationList: MutableList<TeamConversationItem> = mutableListOf()

    private var itemClickListener: RecyclerViewItemClickListener<TeamConversationItem>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamConversationViewHolder =
        TeamConversationViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_view_team_conversation, parent, false))

    override fun getItemCount() = teamConversationList.size

    override fun onBindViewHolder(holder: TeamConversationViewHolder, position: Int) {
        val teamConversation = teamConversationList[position]
        holder.bind(teamConversation, itemClickListener)
    }

    fun updateTeams(teamConversationList: List<TeamConversationItem>) {
        this.teamConversationList.clear()
        this.teamConversationList.addAll(teamConversationList)
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(itemClickListener: RecyclerViewItemClickListener<TeamConversationItem>) {
        this.itemClickListener = itemClickListener
    }
}

class TeamConversationViewHolder(parent: View) : RecyclerView.ViewHolder(parent) {

    fun bind(
        teamConversation: TeamConversationItem,
        itemClickListener: RecyclerViewItemClickListener<TeamConversationItem>?
    ) {
        with(itemView) {
            teamConversationListItemTitleTextView.text = teamConversation.title
            setOnClickListener {
                itemClickListener?.onItemClicked(teamConversation)
            }
        }
    }

}
