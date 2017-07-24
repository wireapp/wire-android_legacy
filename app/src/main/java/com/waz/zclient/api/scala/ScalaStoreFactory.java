/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.api.scala;

import android.content.Context;

import com.waz.zclient.controllers.global.SelectionController;
import com.waz.zclient.core.api.scala.AppEntryStore;
import com.waz.zclient.core.api.scala.ScalaConnectStore;
import com.waz.zclient.core.api.scala.ScalaDraftStore;
import com.waz.zclient.core.api.scala.ScalaInAppNotificationStore;
import com.waz.zclient.core.api.scala.ScalaNetworkStore;
import com.waz.zclient.core.api.scala.ScalaParticipantsStore;
import com.waz.zclient.core.api.scala.ScalaPickUserStore;
import com.waz.zclient.core.api.scala.ScalaProfileStore;
import com.waz.zclient.core.api.scala.ScalaSingleParticipantStore;
import com.waz.zclient.core.stores.StoreFactory;
import com.waz.zclient.core.stores.api.IZMessagingApiStore;
import com.waz.zclient.core.stores.api.ZMessagingApiStore;
import com.waz.zclient.core.stores.appentry.IAppEntryStore;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.IConversationStore;
import com.waz.zclient.core.stores.draft.IDraftStore;
import com.waz.zclient.core.stores.inappnotification.IInAppNotificationStore;
import com.waz.zclient.core.stores.network.INetworkStore;
import com.waz.zclient.core.stores.participants.IParticipantsStore;
import com.waz.zclient.core.stores.pickuser.IPickUserStore;
import com.waz.zclient.core.stores.profile.IProfileStore;
import com.waz.zclient.core.stores.singleparticipants.ISingleParticipantStore;

public class ScalaStoreFactory extends StoreFactory {

    private Context context;
    private SelectionController selectionController;

    public ScalaStoreFactory(Context context, SelectionController selectionController) {
        this.context = context;
        this.selectionController = selectionController;
    }

    @Override
    protected IZMessagingApiStore createZMessagingApiStore() {
        return new ZMessagingApiStore(context);
    }

    @Override
    protected INetworkStore createNetworkStore() {
        return new ScalaNetworkStore(getZMessagingApiStore().getApi());
    }

    @Override
    protected IAppEntryStore createAppEntryStore() {
        return new AppEntryStore(context, getZMessagingApiStore().getApi());
    }

    @Override
    protected IConversationStore createConversationStore() {
        return new ScalaConversationStore(getZMessagingApiStore().getApi(), selectionController);
    }

    @Override
    protected IProfileStore createProfileStore() {
        return new ScalaProfileStore(getZMessagingApiStore().getApi());
    }

    @Override
    protected IPickUserStore createPickUserStore() {
        return new ScalaPickUserStore(getZMessagingApiStore().getApi());
    }

    @Override
    protected IParticipantsStore createParticipantsStore() {
        return new ScalaParticipantsStore();
    }

    @Override
    protected ISingleParticipantStore createSingleParticipantStore() {
        return new ScalaSingleParticipantStore();
    }

    @Override
    protected IInAppNotificationStore createInAppNotificationStore() {
        return new ScalaInAppNotificationStore(getZMessagingApiStore().getApi());
    }

    @Override
    public IConnectStore createConnectStore() {
        return new ScalaConnectStore(context, getZMessagingApiStore().getApi());
    }

    @Override
    protected IDraftStore createDraftStore() {
        return new ScalaDraftStore();
    }

    @Override
    public void tearDown() {
        super.tearDown();
        context = null;
    }
}
