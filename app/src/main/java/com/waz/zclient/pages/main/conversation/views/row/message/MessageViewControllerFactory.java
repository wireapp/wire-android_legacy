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
package com.waz.zclient.pages.main.conversation.views.row.message;

import android.content.Context;
import android.widget.Adapter;
import com.waz.api.Message;
import com.waz.api.Message.Type;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.views.AudioMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.ConnectRequestMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.ConversationNameChangedMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.ErrorMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.FileMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.ImageMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.LocationMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.MissedCallViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.OtrSystemMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.ParticipantsChangedMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.PingMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.SoundCloudMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.SpotifyMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.TextMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.TwitterMessageViewControllerFactory;
import com.waz.zclient.pages.main.conversation.views.row.message.views.VideoMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.YouTubeMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.LinkPreviewViewController;
import com.waz.zclient.utils.MessageUtils;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class MessageViewControllerFactory {

    private static final String TAG = MessageViewControllerFactory.class.getName();
    private static final EnumSet<Message.Type> ACCEPTED_TYPES = EnumSet.of(Type.TEXT,
                                                                           Type.TEXT_EMOJI_ONLY,
                                                                           Type.ASSET,
                                                                           Type.ANY_ASSET,
                                                                           Type.VIDEO_ASSET,
                                                                           Type.AUDIO_ASSET,
                                                                           Type.LOCATION,
                                                                           Type.KNOCK,
                                                                           Type.MEMBER_JOIN,
                                                                           Type.MEMBER_LEAVE,
                                                                           Type.CONNECT_REQUEST,
                                                                           Type.RENAME,
                                                                           Type.MISSED_CALL,
                                                                           Type.INCOMING_CALL,
                                                                           Type.OTR_ERROR,
                                                                           Type.OTR_IDENTITY_CHANGED,
                                                                           Type.OTR_DEVICE_ADDED,
                                                                           Type.OTR_VERIFIED,
                                                                           Type.OTR_UNVERIFIED,
                                                                           Type.HISTORY_LOST,
                                                                           Type.STARTED_USING_DEVICE,
                                                                           Type.RICH_MEDIA);
    private static final EnumSet<Message.Part.Type> ACCEPTED_PART_TYPES = EnumSet.of(Message.Part.Type.TEXT,
                                                                                     Message.Part.Type.TEXT_EMOJI_ONLY,
                                                                                     Message.Part.Type.WEB_LINK,
                                                                                     Message.Part.Type.ASSET,
                                                                                     Message.Part.Type.YOUTUBE,
                                                                                     Message.Part.Type.SOUNDCLOUD,
                                                                                     Message.Part.Type.TWITTER,
                                                                                     Message.Part.Type.SPOTIFY);
    private static final Map<Object, Integer> MESSAGE_TYPE_TO_POSITION = new HashMap<>(ACCEPTED_TYPES.size());

    static {
        Type[] acceptedTypesArray = new Type[ACCEPTED_TYPES.size()];
        ACCEPTED_TYPES.toArray(acceptedTypesArray);
        for (int i = 0; i < acceptedTypesArray.length; i++) {
            MESSAGE_TYPE_TO_POSITION.put(acceptedTypesArray[i], i);
        }

        Message.Part.Type[] acceptedPartTypesArray = new Message.Part.Type[ACCEPTED_PART_TYPES.size()];
        ACCEPTED_PART_TYPES.toArray(acceptedPartTypesArray);
        for (int i = 0; i < acceptedPartTypesArray.length; i++) {
            MESSAGE_TYPE_TO_POSITION.put(acceptedPartTypesArray[i], i + acceptedTypesArray.length);
        }
    }

    public static MessageViewController create(Context context,
                                               Message message,
                                               MessageViewsContainer messageViewsContainer) {
        switch (message.getMessageType()) {
            case TEXT:
            case TEXT_EMOJI_ONLY:
                return new TextMessageViewController(context, messageViewsContainer);
            case ASSET:
                return new ImageMessageViewController(context, messageViewsContainer);
            case ANY_ASSET:
                return new FileMessageViewController(context, messageViewsContainer);
            case VIDEO_ASSET:
                return new VideoMessageViewController(context, messageViewsContainer);
            case AUDIO_ASSET:
                return new AudioMessageViewController(context, messageViewsContainer);
            case LOCATION:
                if (messageViewsContainer != null && !messageViewsContainer.isTornDown() &&
                    !messageViewsContainer.getControllerFactory().getUserPreferencesController().isContentPreviewEnabled()) {
                    return new TextMessageViewController(context, messageViewsContainer);
                }
                return new LocationMessageViewController(context, messageViewsContainer);
            case KNOCK:
                return new PingMessageViewController(context, messageViewsContainer);
            case MEMBER_JOIN:
            case MEMBER_LEAVE:
                return new ParticipantsChangedMessageViewController(context, messageViewsContainer);
            case CONNECT_REQUEST:
                return new ConnectRequestMessageViewController(context, messageViewsContainer);
            case RENAME:
                return new ConversationNameChangedMessageViewController(context, messageViewsContainer);
            case MISSED_CALL:
                return new MissedCallViewController(context, messageViewsContainer);
            case OTR_ERROR:
            case OTR_IDENTITY_CHANGED:
            case STARTED_USING_DEVICE:
            case HISTORY_LOST:
            case OTR_DEVICE_ADDED:
            case OTR_VERIFIED:
            case OTR_UNVERIFIED:
                return new OtrSystemMessageViewController(context, messageViewsContainer);
            case RICH_MEDIA:
                if (message.getParts().length == 0) {
                    return new TextMessageViewController(context, messageViewsContainer);
                }
                final Message.Part richMediaPart = MessageUtils.getFirstRichMediaPart(message);
                if (richMediaPart == null) {
                    return new LinkPreviewViewController(context, messageViewsContainer);
                }
                if (messageViewsContainer != null && !messageViewsContainer.isTornDown() &&
                    !messageViewsContainer.getControllerFactory().getUserPreferencesController().isContentPreviewEnabled()) {
                    return new TextMessageViewController(context, messageViewsContainer);
                }
                switch (richMediaPart.getPartType()) {
                    case TEXT:
                    case TEXT_EMOJI_ONLY:
                        return new TextMessageViewController(context, messageViewsContainer);
                    case WEB_LINK:
                        return new LinkPreviewViewController(context, messageViewsContainer);
                    case ASSET:
                        return new ImageMessageViewController(context, messageViewsContainer);
                    case YOUTUBE:
                        return new YouTubeMessageViewController(context, messageViewsContainer);
                    case SOUNDCLOUD:
                        return new SoundCloudMessageViewController(context, messageViewsContainer);
                    case TWITTER:
                        return new TwitterMessageViewControllerFactory(context, messageViewsContainer);
                    case SPOTIFY:
                        return new SpotifyMessageViewController(context, messageViewsContainer);
                    default:
                        return new LinkPreviewViewController(context, messageViewsContainer);
                }
            case INCOMING_CALL:
            default:
                return new ErrorMessageViewController(context, messageViewsContainer);
        }
    }

    public static int getControllerTypeCount() {
        return ACCEPTED_TYPES.size() + ACCEPTED_PART_TYPES.size();
    }

    public static int getKeyPosition(Object messageType) {
        if (ACCEPTED_TYPES.contains(messageType) || ACCEPTED_PART_TYPES.contains(messageType)) {
            return MESSAGE_TYPE_TO_POSITION.get(messageType);
        }
        return Adapter.IGNORE_ITEM_VIEW_TYPE;
    }
}
