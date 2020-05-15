/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.model.sync;

import java.util.HashMap;
import java.util.Map;

public enum SyncCommand {
    SyncUser("sync-user"),
    SyncSelf("sync-self"),
    DeleteAccount("delete-account"),
    SyncConversations("sync-convs"),
    SyncTeam("sync-team"),
    SyncTeamMember("sync-team-member"),
    SyncConnections("sync-connections"),
    SyncConversation("sync-conv"),
    SyncConvLink("sync-conv-link"),
    SyncSearchQuery("sync-search"),
    SyncSearchResults("sync-search-results"),
    SyncProperties("sync-properties"),
    ExactMatchHandle("exact-match"),
    PostConv("post-conv"),
    PostConvReceiptMode("post-conv-receipt-mode"),
    PostConvName("post-conv-name"),
    PostConvState("post-conv-state"),
    PostConvRole("update-conv_role"),
    PostLastRead("post-last-read"),
    PostCleared("post-cleared"),
    PostTypingState("post-typing-state"),
    PostConnectionStatus("post-conn-status"),
    PostSelfPicture("post-picture"),
    PostSelfName("post-name"),
    PostSelfAccentColor("post-accent-color"),
    PostAvailability("post-availability"),
    PostButtonAction("post-button-action"),
    PostMessage("post-message"),
    PostDeleted("post-deleted"),
    PostRecalled("post-recalled"),
    PostConvJoin("post-conv-join"),
    PostConvLeave("post-conv-leave"),
    PostConnection("post-connection"),
    DeletePushToken("delete-push-token"),
    SyncRichMedia("sync-rich-media"),
    RegisterPushToken("register-push-token"),
    PostSelf("post-self"),
    SyncSelfClients("sync-clients"),   // sync user clients, register current client and update prekeys when needed
    SyncSelfPermissions("sync-self-permissions"),
    SyncClients("sync-user-clients"),
    SyncClientLocation("sync-client-location"),
    SyncPreKeys("sync-prekeys"),
    PostAddBot("post-add-bot"),
    PostRemoveBot("post-remove-bot"),
    PostClientLabel("post-client-label"),
    PostLiking("post-liking"),
    PostSessionReset("post-session-reset"),
    PostAssetStatus("post-asset-status"),
    PostOpenGraphMeta("post-og-meta"),
    PostReceipt("post-receipt"),
    PostBoolProperty("post-bool-property"),
    PostIntProperty("post-int-property"),
    PostStringProperty("post-string-property"),
    PostFolders("post-folders-favorites"),
    SyncFolders("sync-folders"),
    DeleteGroupConv("delete-group-conv"),

    Unknown("unknown");

    public final String name;

    SyncCommand(String name) {
        this.name = name;
    }

    private static Map<String, SyncCommand> byName = new HashMap<>();
    static {
        for (SyncCommand value : SyncCommand.values()) {
            byName.put(value.name, value);
        }
    }

    public static SyncCommand fromName(String name) {
        SyncCommand result = byName.get(name);
        return (result == null) ? Unknown : result;
    }
}
