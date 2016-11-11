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
package com.waz.zclient.pages.main.conversation.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.api.AssetStatus;
import com.waz.api.Message;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.utils.ViewUtils;

public class MessageBottomSheetDialog extends BottomSheetDialog {

    private final Message message;
    private final MessageViewController messageViewController;
    private final Callback callback;

    public enum MessageAction {
        FORWARD(R.id.message_bottom_menu_item_forward, R.string.glyph__forward, R.string.message_bottom_menu_action_forward),
        COPY(R.id.message_bottom_menu_item_copy, R.string.glyph__copy, R.string.message_bottom_menu_action_copy),
        DELETE_LOCAL(R.id.message_bottom_menu_item_delete_local, R.string.glyph__delete_me, R.string.message_bottom_menu_action_delete_local),
        DELETE_GLOBAL(R.id.message_bottom_menu_item_delete_global, R.string.glyph__delete_everywhere, R.string.message_bottom_menu_action_delete_global),
        LIKE(R.id.message_bottom_menu_item_like, R.string.glyph__like, R.string.message_bottom_menu_action_like),
        UNLIKE(R.id.message_bottom_menu_item_unlike, R.string.glyph__liked, R.string.message_bottom_menu_action_unlike),
        SAVE(R.id.message_bottom_menu_item_save, R.string.glyph__download, R.string.message_bottom_menu_action_save),
        OPEN_FILE(R.id.message_bottom_menu_item_open_file, R.string.glyph__view, R.string.message_bottom_menu_action_open),
        EDIT(R.id.message_bottom_menu_item_edit, R.string.glyph__edit, R.string.message_bottom_menu_action_edit);

        public int resId;
        public int glyphResId;
        public int stringId;

        MessageAction(int resId, int glyphResId, int stringId) {
            this.resId = resId;
            this.glyphResId = glyphResId;
            this.stringId = stringId;
        }
    }

    public MessageBottomSheetDialog(@NonNull Context context, @NonNull Message message, MessageViewController messageViewController, boolean isMemberOfConversation, Callback callback) {
        super(context);
        this.message = message;
        this.messageViewController = messageViewController;
        this.callback = callback;
        init(isMemberOfConversation);
    }

    public MessageBottomSheetDialog(@NonNull Context context, int theme, @NonNull Message message, MessageViewController messageViewController, boolean isMemberOfConversation, Callback callback) {
        super(context, theme);
        this.message = message;
        this.messageViewController = messageViewController;
        this.callback = callback;
        init(isMemberOfConversation);
    }

    @SuppressLint("InflateParams")
    private void init(boolean isMemberOfConversation) {
        LinearLayout view = (LinearLayout) getLayoutInflater().inflate(R.layout.message__bottom__menu, null);
        if (isMemberOfConversation && isLikeAllowed()) {
            if (message.isLikedByThisUser()) {
                addAction(view, MessageAction.UNLIKE);
            } else {
                addAction(view, MessageAction.LIKE);
            }
        }
        if (isCopyAllowed()) {
            addAction(view, MessageAction.COPY);
        }
        if (isEditAllowed(isMemberOfConversation)) {
            addAction(view, MessageAction.EDIT);
        }
        if (isSaveAllowed()) {
            addAction(view, MessageAction.SAVE);
        }
        if (isOpenFileAllowed()) {
            addAction(view, MessageAction.OPEN_FILE);
        }
        if (isForwardAllowed()) {
            addAction(view, MessageAction.FORWARD);
        }
        addAction(view, MessageAction.DELETE_LOCAL);
        if (isDeleteForEveryoneAllowed(isMemberOfConversation)) {
            addAction(view, MessageAction.DELETE_GLOBAL);
        }
        setContentView(view);
    }

    private void addAction(ViewGroup root, final MessageAction action) {
        LinearLayout row = (LinearLayout) getLayoutInflater().inflate(R.layout.message__bottom__menu__row, root, false);
        row.setId(action.resId);

        TextView icon = (TextView) row.getChildAt(0);
        icon.setText(action.glyphResId);

        TextView label = (TextView) row.getChildAt(1);
        label.setText(action.stringId);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onAction(action, message, messageViewController);
                dismiss();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.toPx(getContext(), 48));
        root.addView(row, params);
    }

    private boolean isLikeAllowed() {
        if (message.isEphemeral()) {
            return false;
        }
        switch (message.getMessageType()) {
            case ANY_ASSET:
            case ASSET:
            case AUDIO_ASSET:
            case LOCATION:
            case TEXT:
            case TEXT_EMOJI_ONLY:
            case RICH_MEDIA:
            case VIDEO_ASSET:
                return true;
            default:
                return false;
        }
    }

    private boolean isSaveAllowed() {
        if (message.isEphemeral()) {
            return false;
        }
        switch (message.getMessageType()) {
            // Only image supported ATM, need to handle Audio/File/Video
            case ASSET:
                return true;
            case AUDIO_ASSET:
            case VIDEO_ASSET:
                if (message.getAsset().getStatus() == AssetStatus.UPLOAD_DONE ||
                    message.getAsset().getStatus() == AssetStatus.DOWNLOAD_DONE) {
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private boolean isOpenFileAllowed() {
        if (message.isEphemeral()) {
            return false;
        }
        switch (message.getMessageType()) {
            case ANY_ASSET:
                if (message.getAsset().getStatus() == AssetStatus.UPLOAD_DONE ||
                    message.getAsset().getStatus() == AssetStatus.DOWNLOAD_DONE) {
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private boolean isCopyAllowed() {
        if (message.isEphemeral()) {
            return false;
        }
        switch (message.getMessageType()) {
            case TEXT:
            case TEXT_EMOJI_ONLY:
            case RICH_MEDIA:
                return true;
            default:
                return false;
        }
    }

    private boolean isForwardAllowed() {
        if (message.isEphemeral()) {
            return false;
        }
        switch (message.getMessageType()) {
            case TEXT:
            case TEXT_EMOJI_ONLY:
            case RICH_MEDIA:
                return true;
            case ANY_ASSET:
            case AUDIO_ASSET:
            case VIDEO_ASSET:
                if (message.getAsset().getStatus() == AssetStatus.UPLOAD_DONE ||
                    message.getAsset().getStatus() == AssetStatus.DOWNLOAD_DONE) {
                    return true;
                }
                return false;
            case ASSET:
                // TODO: Once https://wearezeta.atlassian.net/browse/CM-976 is resolved, we should handle image asset like any other asset
                return true;
            default:
                return false;
        }
    }

    private boolean isEditAllowed(boolean isMemberOfConversation) {
        if (!isMemberOfConversation ||
            message.isEphemeral() ||
            !message.getUser().isMe()) {
            return false;
        }
        switch (message.getMessageType()) {
            case TEXT_EMOJI_ONLY:
            case TEXT:
            case RICH_MEDIA:
                return true;
            default:
                return false;
        }
    }

    private boolean isDeleteForEveryoneAllowed(boolean isMemberOfConversation) {
        if (!isMemberOfConversation ||
            !message.getUser().isMe()) {
            return false;
        }
        switch (message.getMessageType()) {
            case TEXT:
            case ANY_ASSET:
            case ASSET:
            case AUDIO_ASSET:
            case VIDEO_ASSET:
            case KNOCK:
            case LOCATION:
            case RICH_MEDIA:
            case TEXT_EMOJI_ONLY:
                return true;
            default:
                return false;
        }
    }

    public interface Callback {
        void onAction(MessageAction action, Message message, MessageViewController messageViewController);
    }

}
