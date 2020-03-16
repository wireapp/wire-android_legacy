package com.waz.zclient.features.teams.conversations

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.R
import com.waz.zclient.core.extension.createScope
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.features.teams.di.TEAMS_SCOPE
import com.waz.zclient.features.teams.di.TEAMS_SCOPE_ID
import kotlinx.android.synthetic.main.activity_team_conversations.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

class TeamsConversationListActivity : AppCompatActivity(R.layout.activity_team_conversations) {

    private val scope = createScope(
        scopeId = TEAMS_SCOPE_ID,
        scopeName = TEAMS_SCOPE
    )

    @ExperimentalCoroutinesApi
    @InternalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(activityTeamConversationsListToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        replaceFragment(R.id.activityTeamConversationsListLayoutContainer, TeamsConversationListFragment.newInstance(), false)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        @JvmStatic
        fun newIntent(context: Context) = Intent(context, TeamsConversationListActivity::class.java)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.close()
    }

}
