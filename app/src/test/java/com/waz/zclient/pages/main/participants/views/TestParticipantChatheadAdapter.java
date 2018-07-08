/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.pages.main.participants.views;


// TODO: Let's rewrite them for the new Scala ParticipantChatheadAdapter later on

public class TestParticipantChatheadAdapter {
/*
    @Test
    public void testAdapterIsInACorrectStateWithNoVerifiedAndNoUnverifiedUsers() {
        MockCoreList<User> mockUserList = new MockCoreList<>();

        int column = 5;
        int expectedSize = 1;

        ParticipantsChatheadAdapter adapter = new ParticipantsChatheadAdapter();
        adapter.setUsersList(mockUserList, column);

        // assertions
        Assert.assertEquals(expectedSize, adapter.getCount());
    }

    @Test
    public void testAdapterIsInACorrectStateWithVerifiedAndUserMatchColumnCount() {
        MockCoreList<User> mockUserList = new MockCoreList<>();

        int numOfUsers = 10;
        int column = 5;
        int expectedSize = 10;

        mockUserList.add(mockUsers(numOfUsers, false, numOfUsers));

        ParticipantsChatheadAdapter adapter = new ParticipantsChatheadAdapter();
        adapter.setUsersList(mockUserList, column);

        // assertions
        Assert.assertEquals(expectedSize, adapter.getCount());
    }

    @Test
    public void testAdapterIsInACorrectStateWithVerifiedAndUnverifiedUsers() {
        MockCoreList<User> mockUserList = new MockCoreList<>();

        int numOfUsers = 7;
        int column = 5;
        int expectedSize = (column - numOfUsers % column) + numOfUsers * 2 + column;

        mockUserList.add(mockUsers(numOfUsers, true, 0));
        mockUserList.add(mockUsers(numOfUsers, false, numOfUsers));

        ParticipantsChatheadAdapter adapter = new ParticipantsChatheadAdapter();
        adapter.setUsersList(mockUserList, column);

        // assertions
        Assert.assertEquals(expectedSize, adapter.getCount());

        int pos = 0;
        // unverified users
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("7", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("8", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("9", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("10", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("11", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("12", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("13", adapter.getItem(pos).getId());
        // empty unverified users empty
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        // separator
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_SEPARATOR_VERIFIED, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        // separator - empty
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_EMPTY, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_EMPTY, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_EMPTY, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_EMPTY, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        // verified users
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("0", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("1", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("2", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("3", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("4", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("5", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("6", adapter.getItem(pos).getId());
    }


    @Test
    public void testAdapterIsInACorrectStateWithNoVerifiedAndUnverifiedUsers() {
        MockCoreList<User> mockUserList = new MockCoreList<>();

        int numOfUsers = 7;
        int column = 5;
        int expectedSize = (column - numOfUsers % column) + numOfUsers;

        mockUserList.add(mockUsers(numOfUsers, false, numOfUsers));

        ParticipantsChatheadAdapter adapter = new ParticipantsChatheadAdapter();
        adapter.setUsersList(mockUserList, column);

        // assertions
        Assert.assertEquals(expectedSize, adapter.getCount());

        int pos = 0;
        // unverified users
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("7", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("8", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("9", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("10", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("11", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("12", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("13", adapter.getItem(pos).getId());
        // empty unverified users empty
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
    }

    @Test
    public void testAdapterIsInACorrectStateWithVerifiedAndNoUnverifiedUsers() {
        MockCoreList<User> mockUserList = new MockCoreList<>();

        int numOfUsers = 7;
        int column = 5;
        int expectedSize = numOfUsers + column;

        mockUserList.add(mockUsers(numOfUsers, true, 0));

        ParticipantsChatheadAdapter adapter = new ParticipantsChatheadAdapter();
        adapter.setUsersList(mockUserList, column);

        // assertions
        Assert.assertEquals(expectedSize, adapter.getCount());

        int pos = 0;
        // separator
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_SEPARATOR_VERIFIED, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        // separator - empty
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_EMPTY, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_EMPTY, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_EMPTY, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_EMPTY, adapter.getItemViewType(pos));
        Assert.assertNull(adapter.getItem(pos));
        // verified users
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("0", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("1", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("2", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("3", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("4", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("5", adapter.getItem(pos).getId());
        pos++;
        Assert.assertEquals(ParticipantsChatheadAdapter.VIEW_TYPE_CHATHEAD, adapter.getItemViewType(pos));
        Assert.assertEquals("6", adapter.getItem(pos).getId());
    }

    public List<User> mockUsers(int count, boolean verified, int start) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {

            User user = mock(User.class);
            when(user.getId()).thenReturn(String.format("%d", start + i));
            Verification verification = verified ? Verification.VERIFIED : Verification.UNVERIFIED;

            when(user.getVerified()).thenReturn(verification);
            users.add(user);
        }
        return users;
    }
    */
}
