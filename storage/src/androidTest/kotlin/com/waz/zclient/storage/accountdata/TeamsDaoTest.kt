package com.waz.zclient.storage.accountdata

import androidx.room.Room
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.GlobalDatabase
import com.waz.zclient.storage.db.teams.TeamsDao
import com.waz.zclient.storage.db.teams.TeamsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class TeamsDaoTest : IntegrationTest() {

    private lateinit var teamsDao: TeamsDao

    private lateinit var globalDatabase: GlobalDatabase

    @Before
    fun setup() {
        globalDatabase = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            GlobalDatabase::class.java
        ).build()
        teamsDao = globalDatabase.teamsDao()
    }

    @After
    fun tearDown() {
        globalDatabase.close()
    }

    @Test
    fun givenAListOfTeams_whenAllTeamsIsCalled_ThenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        val teams = getListOfTeams()
        teams.map {
            teamsDao.insertTeam(it)
        }

        val roomActiveAccounts = teamsDao.allTeams()
        assertEquals(roomActiveAccounts[0].teamId, TEST_TEAM_FIRST_ID)
        assertEquals(roomActiveAccounts[1].teamId, TEST_TEAM_SECOND_ID)
        assertEquals(roomActiveAccounts.size, 2)
        roomActiveAccounts.map {
            assertEquals(it.teamName, TEST_TEAM_NAME)
            assertEquals(it.creatorId, TEST_TEAM_CREATOR)
            assertEquals(it.iconId, TEST_TEAM_ICON)
        }
        Unit
    }

    private fun createTeamEntity(teamId: String) =
        TeamsEntity(
            teamId = teamId,
            teamName = TEST_TEAM_NAME,
            creatorId = TEST_TEAM_CREATOR,
            iconId = TEST_TEAM_ICON
        )

    private fun getListOfTeams() = listOf(
        createTeamEntity(TEST_TEAM_FIRST_ID),
        createTeamEntity(TEST_TEAM_SECOND_ID)
    )

    companion object {
        private const val TEST_TEAM_FIRST_ID = "1"
        private const val TEST_TEAM_SECOND_ID = "2"
        private const val TEST_TEAM_NAME = "testTeam"
        private const val TEST_TEAM_CREATOR = "123"
        private const val TEST_TEAM_ICON = "teamIcon.png"
    }
}
