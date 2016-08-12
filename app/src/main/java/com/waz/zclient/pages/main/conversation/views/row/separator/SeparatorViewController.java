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
package com.waz.zclient.pages.main.conversation.views.row.separator;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.waz.api.Message;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.ConversationItemViewController;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.theme.ThemeUtils;
import com.waz.zclient.ui.utils.BitmapUtils;
import com.waz.zclient.utils.DateConvertUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.utils.ZTimeFormatter;
import com.waz.zclient.views.chathead.ChatheadImageView;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;

public class SeparatorViewController implements ConversationItemViewController,
                                                UpdateListener {
    public static final String TAG = SeparatorViewController.class.getSimpleName();
    private MessageViewsContainer messageViewsContainer;
    private Resources resources;
    private Context context;

    private View view;
    private LinearLayout timestampLinearLayout;
    private TypefaceTextView timestampTextview;
    private View timestampDivider;
    private TypefaceTextView userNameTextView;
    private View messageRecalledGlyph;
    private View messageEditedGlyph;
    private ImageView unreadDot;
    private ChatheadImageView userChatheadImageView;
    private View userLayout;

    private boolean is24HourFormat;
    private User user;

    public SeparatorViewController(Context context, MessageViewsContainer messageViewsContainer) {
        this.messageViewsContainer = messageViewsContainer;
        this.context = context;
        this.resources = context.getResources();
        this.is24HourFormat = DateFormat.is24HourFormat(context);

        view = View.inflate(context, R.layout.row_conversation_separator, null);
        userNameTextView = ViewUtils.getView(view, R.id.ttv__row_conversation__separator__name);
        messageRecalledGlyph = ViewUtils.getView(view, R.id.gtv__message_recalled);
        messageEditedGlyph = ViewUtils.getView(view, R.id.gtv__message_edited);
        timestampLinearLayout = ViewUtils.getView(view, R.id.ll__row_conversation__separator_time);
        timestampDivider = ViewUtils.getView(view, R.id.v__row_conversation__separator__time__divider);
        timestampTextview = ViewUtils.getView(view, R.id.ttv__row_conversation__separator__time);
        unreadDot = ViewUtils.getView(view, R.id.iv__row_conversation__unread_dot);
        userChatheadImageView = ViewUtils.getView(view, R.id.civ__row_conversation__separator_chathead);
        userLayout = ViewUtils.getView(view, R.id.ll__row_conversation__separator_user);
    }

    public void setMessage(Message message, Separator separator) {
        setUnreadDot(separator);
        setUserName(message, separator);
        setTimestamp(separator);
        setPadding(message, separator);
        showRecalledGlyph(separator);

        if (message.getUser().isMe() &&
            message.isEdited()) {
            messageEditedGlyph.setVisibility(View.VISIBLE);
        } else {
            messageEditedGlyph.setVisibility(View.GONE);
        }
    }

    private void setPadding(Message message, Separator separator) {
        if (separator.nextMessage == null) {
            return;
        }

        if (separator.previousMessage == null) {
            if (separator.nextMessage.getMessageType() == Message.Type.MEMBER_JOIN) {
                ViewUtils.setPaddingTop(view,
                                        resources.getDimensionPixelSize(R.dimen.content__separator__padding__before_started));
            }
            return;
        }

        resetPadding();

        boolean showSeparator = SeparatorRules.shouldHaveName(message, separator) ||
                                SeparatorRules.shouldHaveTimestamp(separator,
                                                                   resources.getInteger(R.integer.content__burst_time_interval)) ||
                                SeparatorRules.shouldHaveUnreadDot(separator,
                                                                   messageViewsContainer.getUnreadMessageCount());

        if (showSeparator) {
            setPaddingWithSeparator(message, separator);
        } else {
            setPaddingWithoutSeparator(separator);
        }
    }

    private void resetPadding() {
        ViewUtils.setPaddingTop(view, 0);
        ViewUtils.setMarginTop(timestampLinearLayout, 0);
        ViewUtils.setPaddingTop(userLayout, 0);
        ViewUtils.setPaddingBottom(view, 0);
        ViewUtils.setMarginBottom(timestampLinearLayout, 0);
        ViewUtils.setPaddingBottom(userLayout, 0);
    }

    private void setPaddingWithoutSeparator(Separator separator) {
        int padding = 0;

        Message.Type previousMessageType = separator.previousMessage.getMessageType();
        Message.Type nextMessageType = separator.nextMessage.getMessageType();

        boolean previousIsImage = previousMessageType == Message.Type.ASSET;
        boolean nextIsImage = nextMessageType == Message.Type.ASSET;

        boolean previousIsAsset = previousMessageType == Message.Type.TEXT_EMOJI_ONLY ||
                                  previousMessageType == Message.Type.AUDIO_ASSET ||
                                  previousMessageType == Message.Type.RICH_MEDIA ||
                                  previousMessageType == Message.Type.AUDIO_ASSET ||
                                  previousMessageType == Message.Type.LOCATION ||
                                  previousMessageType == Message.Type.VIDEO_ASSET;
        boolean nextIsAsset = nextMessageType == Message.Type.TEXT_EMOJI_ONLY ||
                              nextMessageType == Message.Type.RICH_MEDIA ||
                              nextMessageType == Message.Type.AUDIO_ASSET ||
                              nextMessageType == Message.Type.LOCATION ||
                              nextMessageType == Message.Type.VIDEO_ASSET;

        if (previousMessageType == Message.Type.TEXT &&
            nextMessageType == Message.Type.TEXT) {
            // We are between two messages without anything
            padding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_two_messages);
        }
        if (previousIsAsset || previousIsImage) {
            // No separator between image and something else
            padding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__after_image);
        }
        if (nextIsAsset || nextIsImage) {
            // No separator between image and something else
            padding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__before_image);
        }
        if (previousIsAsset && nextIsAsset) {
            // No separator between asset and asset
            padding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_asset_asset);
        }
        if (previousIsImage && nextIsImage) {
            // No separator between image and image
            padding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_image_image);
        }
        if (previousMessageType == Message.Type.TEXT && nextMessageType == Message.Type.AUDIO_ASSET) {
            padding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_text_audio);
        }
        if (previousMessageType == Message.Type.MEMBER_JOIN ||
            previousMessageType == Message.Type.MEMBER_LEAVE) {
            // Before anything and the member change
            padding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__after_member_change);
        }
        if (nextMessageType == Message.Type.MEMBER_JOIN ||
            nextMessageType == Message.Type.MEMBER_LEAVE) {
            // Before anything and the member change
            padding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__to_member_change);
        }
        if (previousMessageType == Message.Type.RENAME ||
            nextMessageType == Message.Type.RENAME) {
            // Before / after anything and the name change
            padding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__to_name_change);
        }
        if (nextMessageType == Message.Type.KNOCK) {
            padding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__to_knock);
        }
        if (previousMessageType == Message.Type.KNOCK) {
            padding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__after_knock);
        }
        if (nextMessageType == Message.Type.MISSED_CALL ||
            previousMessageType == Message.Type.MISSED_CALL) {
            // Before / after missed call
            padding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__to_missed_call);
        }

        ViewUtils.setPaddingBottom(view, padding);
    }

    private void setPaddingWithSeparator(Message message, Separator separator) {
        Message.Type nextMessageType = separator.nextMessage.getMessageType();
        Message.Type previousMessageType = separator.previousMessage.getMessageType();

        boolean nextIsAsset = nextMessageType == Message.Type.TEXT_EMOJI_ONLY ||
                              nextMessageType == Message.Type.AUDIO_ASSET ||
                              nextMessageType == Message.Type.ASSET ||
                              nextMessageType == Message.Type.RICH_MEDIA ||
                              nextMessageType == Message.Type.AUDIO_ASSET ||
                              nextMessageType == Message.Type.LOCATION ||
                              nextMessageType == Message.Type.VIDEO_ASSET;
        boolean previousIsAsset = previousMessageType == Message.Type.TEXT_EMOJI_ONLY ||
                                  previousMessageType == Message.Type.AUDIO_ASSET ||
                                  previousMessageType == Message.Type.ASSET ||
                                  previousMessageType == Message.Type.RICH_MEDIA ||
                                  previousMessageType == Message.Type.AUDIO_ASSET ||
                                  previousMessageType == Message.Type.LOCATION ||
                                  previousMessageType == Message.Type.VIDEO_ASSET;
        boolean nextIsFile = nextMessageType == Message.Type.ANY_ASSET;

        boolean hasName = SeparatorRules.shouldHaveName(message, separator);
        boolean hasTimestamp = SeparatorRules.shouldHaveTimestamp(separator,
                                                                  resources.getInteger(R.integer.content__burst_time_interval));
        boolean hasDayTimestamp = SeparatorRules.shouldHaveBigTimestamp(separator);
        boolean hasUnreadDot = SeparatorRules.shouldHaveUnreadDot(separator,
                                                                  messageViewsContainer.getUnreadMessageCount());

        if (hasTimestamp || hasUnreadDot) {
            int topPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_time_anything);
            int bottomPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_time_anything);
            if (previousMessageType == Message.Type.MEMBER_LEAVE ||
                previousMessageType == Message.Type.MEMBER_JOIN) {
                // Member change after time separator
                topPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__after_member_change);
            } else if (nextMessageType == Message.Type.MISSED_CALL) {
                bottomPadding = resources.getDimensionPixelSize(R.dimen.content__missed_call__space_between_image_and_text);
            } else if (nextMessageType == Message.Type.ASSET) {
                topPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_image_time);
            } else if (nextMessageType == Message.Type.TEXT) {
                topPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_time_text);
            }

            ViewUtils.setMarginTop(timestampLinearLayout, topPadding);
            ViewUtils.setMarginBottom(timestampLinearLayout, bottomPadding);
        }

        if (hasDayTimestamp) {
            int topPadding = 0;
            if (previousMessageType == Message.Type.TEXT) {
                topPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_text_time);
            } else if (previousIsAsset) {
                topPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_image_time);
            }
            ViewUtils.setMarginTop(timestampLinearLayout, topPadding);

        }

        if (hasName) {
            int topPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__before_name);
            int bottomPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_name_text);

            if (nextIsAsset) {
                // Image after name separator
                bottomPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_name_image);
            }
            if (nextIsFile) {
                // File after name separator
                bottomPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_time_anything);
            }

            if (previousMessageType == Message.Type.AUDIO_ASSET) {
                topPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_text_audio);
            }

            if (hasTimestamp || hasDayTimestamp) {
                topPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__after_name_and_time);
            } else if (previousMessageType == Message.Type.MEMBER_LEAVE ||
                       previousMessageType == Message.Type.MEMBER_JOIN) {
                // Member change before name separator
                topPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_after_member_change_name);
            } else if (previousIsAsset) {
                topPadding = resources.getDimensionPixelSize(R.dimen.content__separator__padding__between_image_name);
            }

            ViewUtils.setPaddingTop(userLayout, topPadding);
            ViewUtils.setPaddingBottom(userLayout, bottomPadding);
        }
    }

    private void setUnreadDot(Separator separator) {
        if (SeparatorRules.shouldHaveUnreadDot(separator, messageViewsContainer.getUnreadMessageCount())) {
            int radius = resources.getDimensionPixelSize(R.dimen.conversation__unread_dot__radius);
            int width = resources.getDimensionPixelSize(R.dimen.list_menu_unread_width);

            Drawable dotDrawable = new BitmapDrawable(resources,
                                                      BitmapUtils.getUnreadMarker(width,
                                                                                  radius,
                                                                                  messageViewsContainer.getControllerFactory().getAccentColorController().getColor()));
            unreadDot.setVisibility(View.VISIBLE);
            unreadDot.setImageDrawable(dotDrawable);
        } else {
            unreadDot.setVisibility(View.GONE);
        }
    }

    private void setTimestamp(Separator separator) {
        if (SeparatorRules.shouldHaveTimestamp(separator,
                                               resources.getInteger(R.integer.content__burst_time_interval)) ||
            SeparatorRules.shouldHaveUnreadDot(separator, messageViewsContainer.getUnreadMessageCount())) {
            timestampLinearLayout.setVisibility(View.VISIBLE);

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime timestamp = DateConvertUtils.asLocalDateTime(separator.nextMessage.getTime());
            final ZoneId timeZone = ZoneId.systemDefault();
            String textSmall = ZTimeFormatter.getSeparatorTime(context.getResources(),
                                                               now,
                                                               timestamp,
                                                               is24HourFormat,
                                                               timeZone,
                                                               true);

            timestampTextview.setVisibility(View.VISIBLE);
            timestampTextview.setTransformedText(textSmall);
            if (SeparatorRules.shouldHaveBigTimestamp(separator)) {
                if (ThemeUtils.isDarkTheme(context)) {
                    timestampLinearLayout.setBackgroundColor(context.getResources().getColor(R.color.white_8));
                } else {
                    timestampLinearLayout.setBackgroundColor(context.getResources().getColor(R.color.black_4));
                }
                timestampTextview.setTypeface(context.getString(R.string.wire__typeface__bold));
                timestampDivider.setVisibility(View.GONE);
            } else {
                timestampLinearLayout.setBackgroundColor(Color.TRANSPARENT);
                timestampDivider.setVisibility(View.VISIBLE);
                timestampTextview.setTypeface(context.getString(R.string.wire__typeface__light));
            }
        } else {
            timestampLinearLayout.setVisibility(View.GONE);
            timestampLinearLayout.setBackgroundColor(Color.TRANSPARENT);
            timestampDivider.setVisibility(View.GONE);
            timestampTextview.setVisibility(View.GONE);
        }
    }

    private void setUserName(Message message, Separator separator) {
        if (user != null) {
            user.removeUpdateListener(this);
        }

        boolean shouldHaveName = SeparatorRules.shouldHaveName(message, separator);

        if (shouldHaveName) {
            user = separator.nextMessage.getUser();
            showUserName();
            user.addUpdateListener(this);
        } else {
            userNameTextView.setVisibility(View.GONE);
            userChatheadImageView.setVisibility(View.GONE);
            userChatheadImageView.setOnClickListener(null);
            user = null;
        }
    }

    private void showRecalledGlyph(Separator separator) {
        messageRecalledGlyph.setVisibility(separator.nextMessage.getMessageType() == Message.Type.RECALLED ?
                                           View.VISIBLE : View.GONE);
    }

    private void showUserName() {
        userNameTextView.setVisibility(View.VISIBLE);
        userNameTextView.setText(user.getDisplayName());

        userChatheadImageView.setVisibility(View.VISIBLE);
        userChatheadImageView.setUser(user);
        if (!messageViewsContainer.isTornDown()) {
            userChatheadImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    messageViewsContainer.getControllerFactory()
                                         .getConversationScreenController()
                                         .setPopoverLaunchedMode(DialogLaunchMode.AVATAR);
                    if (messageViewsContainer.isPhone()) {
                        messageViewsContainer.getControllerFactory()
                                             .getConversationScreenController()
                                             .showUser(user);
                    } else {
                        messageViewsContainer.getControllerFactory()
                                             .getPickUserController()
                                             .showUserProfile(user, userChatheadImageView);
                    }
                }
            });
        }
    }

    public void setOnSeparatorClickListener(View.OnClickListener onSeparatorClickListener) {
        view.setOnClickListener(onSeparatorClickListener);
    }

    @Override
    public View getView() {
        return view;
    }

    @Override
    public void recycle() {
        if (user != null) {
            user.removeUpdateListener(this);
            user = null;
        }
        resetPadding();
    }

    @Override
    public void updated() {
        showUserName();
    }
}
