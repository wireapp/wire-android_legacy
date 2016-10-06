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
package com.waz.zclient.pages.main.conversation.views.row.footer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.TextView;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.zclient.R;
import com.waz.zclient.controllers.tracking.events.conversation.ReactedToMessageEvent;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.footer.views.FooterLikeDetailsLayout;
import com.waz.zclient.pages.main.conversation.views.row.message.ConversationItemViewController;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.utils.ZTimeFormatter;
import org.threeten.bp.DateTimeUtils;
import org.threeten.bp.Instant;

public class FooterViewController implements ConversationItemViewController,
                                             FooterLikeDetailsLayout.OnClickListener,
                                             FooterActionCallback {

    private static final int LIKE_HINT_VISIBILITY_MIL_SEC = 3000;
    private static final int TIMESTAMP_VISIBILITY_MIL_SEC = 5000;
    private final Context context;
    private final MessageViewsContainer container;
    private final View view;
    private final TextView likeButton;
    private final TextView likeButtonAnimation;
    private final TextView messageStatusTextView;
    private final FooterLikeDetailsLayout likeDetails;

    private final Handler mainHandler;

    private final int likeButtonColorLiked;
    private final int likeButtonColorUnliked;
    private Message message;

    private boolean isMyLastMessage;
    private final float height;

    private final ModelObserver<Message> messageModelObserver = new ModelObserver<Message>() {
        @Override
        public void updated(Message message) {

            mainHandler.removeCallbacksAndMessages(null);
            if (message.isLiked()) {
                showLikeDetails();
            } else {
                showMessageStatus();
            }

            if (shouldBeExpanded() && (view.getVisibility() == View.GONE || view.getMeasuredHeight() == 0)) {
                if (container.getExpandedView() != null && container.getExpandedView() != FooterViewController.this) {
                    container.getExpandedView().close();
                }
                container.setExpandedView(FooterViewController.this);
                container.setExpandedMessageId(message.getId());
                expand();
                if (message.isLastMessageFromSelf()) {
                    likeButton.setVisibility(View.GONE);
                }
            } else if (isMyLastMessage && !message.isLastMessageFromSelf()) {
                collapse();
            }

            isMyLastMessage = message.isLastMessageFromSelf();

            updateLikeButton();
            updateMessageStatusLabel();

            boolean showHint = !container.isTornDown() && container.getControllerFactory().getUserPreferencesController().hasPerformedAction(
                IUserPreferencesController.LIKED_MESSAGE);
            likeDetails.setUsers(message.isLiked() ? message.getLikes() : null, showHint);
        }
    };

    private Runnable showLikeDetailsRunnable = new Runnable() {
        @Override
        public void run() {
            showLikeDetails();
        }
    };

    private Runnable showMessageStatusRunnable = new Runnable() {
        @Override
        public void run() {
            showMessageStatus();
        }
    };

    public FooterViewController(Context context, MessageViewsContainer container) {
        this.context = context;
        this.container = container;
        mainHandler = new Handler(Looper.getMainLooper());
        view = View.inflate(context, R.layout.row_conversation_footer, null);
        likeButton = ViewUtils.getView(view, R.id.gtv__footer__like__button);
        likeButtonAnimation = ViewUtils.getView(view, R.id.gtv__footer__like__button_animation);
        likeButtonAnimation.setVisibility(View.GONE);
        messageStatusTextView = ViewUtils.getView(view, R.id.tv__footer__message_status);
        likeDetails = ViewUtils.getView(view, R.id.fldl_like_details);

        likeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLike();
            }
        });
        likeDetails.setOnClickListener(this);

        likeButtonColorLiked = ContextCompat.getColor(context, R.color.accent_red);
        likeButtonColorUnliked = ContextCompat.getColor(context, R.color.text__secondary_light);
        height = context.getResources().getDimension(R.dimen.content__footer__height);
    }

    public void setMessage(Message message) {
        this.message = message;
        isMyLastMessage = message.isLastMessageFromSelf();
        if (shouldBeExpanded() || message.getId().equals(container.getExpandedMessageId())) {
            view.setVisibility(View.VISIBLE);
            if (message.isLiked()) {
                likeDetails.setVisibility(View.VISIBLE);
                messageStatusTextView.setVisibility(View.GONE);
                messageStatusTextView.setTranslationY(height);

            } else {
                likeDetails.setVisibility(View.GONE);
                likeDetails.setTranslationY(-height);
                messageStatusTextView.setVisibility(View.VISIBLE);
            }
        } else {
            view.setVisibility(View.GONE);
            likeDetails.setVisibility(View.GONE);
            likeDetails.setTranslationY(-height);
            messageStatusTextView.setVisibility(View.VISIBLE);
        }
        messageModelObserver.setAndUpdate(message);
    }

    @Override
    public View getView() {
        return view;
    }

    @Override
    public void recycle() {
        mainHandler.removeCallbacksAndMessages(null);
        view.setVisibility(View.GONE);
        likeButton.setVisibility(View.VISIBLE);
        likeButton.setTag(null);
        messageModelObserver.clear();
        likeDetails.setUsers(null, false);
        message = null;
        isMyLastMessage = false;
    }

    private void toggleLike() {
        if (message.isLikedByThisUser()) {
            message.unlike();
            container.getControllerFactory().getTrackingController().tagEvent(ReactedToMessageEvent.unlike(message.getConversation(),
                                                                                                           message,
                                                                                                           ReactedToMessageEvent.Method.BUTTON));
        } else {
            message.like();
            container.getControllerFactory().getTrackingController().tagEvent(ReactedToMessageEvent.like(message.getConversation(),
                                                                                                         message,
                                                                                                         ReactedToMessageEvent.Method.BUTTON));
            if (!container.getControllerFactory().getUserPreferencesController().hasPerformedAction(IUserPreferencesController.LIKED_MESSAGE)) {
                container.getControllerFactory().getUserPreferencesController().setPerformedAction(IUserPreferencesController.LIKED_MESSAGE);
            }
        }
    }

    private boolean shouldBeExpanded() {
        return message.isLiked() ||
               message.isLastMessageFromOther() ||
               (message.isLastMessageFromSelf() && message.getConversation().getType() == IConversation.Type.ONE_TO_ONE) ||
               message.getMessageStatus() == Message.Status.FAILED ||
               message.getMessageStatus() == Message.Status.FAILED_READ;
    }

    private void updateLikeButton() {
        boolean likedByThisUser = message.isLikedByThisUser();
        boolean showLikeAnimation = likeButton.getTag() != null &&
                                    (boolean) likeButton.getTag() != likedByThisUser;
        likeButton.setText(context.getText(likedByThisUser ? R.string.glyph__liked : R.string.glyph__like));
        likeButton.setTextColor(likedByThisUser ? likeButtonColorLiked : likeButtonColorUnliked);
        likeButton.setTag(likedByThisUser);
        if (message.isLiked() && likeButton.getVisibility() == View.GONE) {
            likeButton.setVisibility(View.VISIBLE);
        }
        if (showLikeAnimation) {
            showLikeAnimation(likedByThisUser);
        }
    }

    private void updateMessageStatusLabel() {
        Instant messageTime = message.isEdited() || message.isDeleted() ?
                              message.getEditTime() :
                              message.getTime();
        String timestamp = ZTimeFormatter.getSingleMessageTime(getView().getContext(), DateTimeUtils.toDate(messageTime));
        String status;
        if (message.getUser().isMe()) {
            switch (message.getMessageStatus()) {
                case PENDING:
                    status = context.getString(R.string.message_footer__status__sending);
                    break;
                case SENT:
                    status = context.getString(R.string.message_footer__status__sent, timestamp);
                    break;
                case DELETED:
                    status = context.getString(R.string.message_footer__status__deleted, timestamp);
                    break;
                case DELIVERED:
                    if (message.getConversation().getType() == IConversation.Type.GROUP) {
                        status = context.getString(R.string.message_footer__status__sent, timestamp);
                    } else {
                        status = context.getString(R.string.message_footer__status__delivered, timestamp);
                    }
                    break;
                case FAILED:
                case FAILED_READ:
                    status = context.getString(R.string.message_footer__status__failed);
                    break;
                default:
                    status = timestamp;
                    break;
            }
        } else {
            status = timestamp;
        }
        messageStatusTextView.setText(status);
        TextViewUtils.linkifyText(messageStatusTextView,
                                  ContextCompat.getColor(context, R.color.accent_red),
                                  false,
                                  new Runnable() {
                                      @Override
                                      public void run() {
                                          message.retry();
                                      }
                                  });
    }

    private void showLikeAnimation(boolean like) {
        final Interpolator interpolator = new AccelerateDecelerateInterpolator();
        float startScale;
        float endScale;
        float startAlpha;
        float endAlpha;
        if (like) {
            startScale = 2f;
            endScale = 1f;
            startAlpha = 0f;
            endAlpha = 1f;
        } else {
            startScale = 1f;
            endScale = 2f;
            startAlpha = 1f;
            endAlpha = 0f;
        }
        likeButtonAnimation.setScaleX(startScale);
        likeButtonAnimation.setScaleY(startScale);
        likeButtonAnimation.setAlpha(startAlpha);
        likeButtonAnimation.setVisibility(View.VISIBLE);
        likeButtonAnimation.animate()
                  .scaleX(endScale)
                  .scaleY(endScale)
                  .alpha(endAlpha)
                  .setDuration(250)
                  .setInterpolator(interpolator).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (likeButtonAnimation != null) {
                    likeButtonAnimation.setVisibility(View.GONE);
                }
            }
        });
        container.getControllerFactory().getVibratorController().vibrate(new long[]{10, 20, 10, 20, 10, 30, 10, 20, 10, 20, 10, 30});
    }

    private void expand() {
        if (container.getExpandedView() != null && container.getExpandedView() != this) {
            container.getExpandedView().close();
        }
        container.setExpandedMessageId(message.getId());
        container.setExpandedView(this);
        view.setVisibility(View.VISIBLE);
        if (shouldShowLikeButton()) {
            likeButton.setVisibility(View.VISIBLE);
        }

        View parent = (View) view.getParent();
        final int widthSpec;
        if (parent == null) {
            // needed for tests
            widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        } else {
             widthSpec = View.MeasureSpec.makeMeasureSpec(parent.getMeasuredWidth()
                                                                   - parent.getPaddingLeft()
                                                                   - parent.getPaddingRight(),
                                                                   View.MeasureSpec.AT_MOST);
        }
        final int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(widthSpec, heightSpec);
        ValueAnimator animator = createHeightAnimator(view, 0, view.getMeasuredHeight());

        if (!message.isLiked() &&
            !message.getUser().isMe() &&
            !container.getControllerFactory().getUserPreferencesController().hasPerformedAction(IUserPreferencesController.LIKED_MESSAGE)) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mainHandler.removeCallbacksAndMessages(null);
                    mainHandler.postDelayed(showMessageStatusRunnable, LIKE_HINT_VISIBILITY_MIL_SEC);
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    showLikeDetails();
                }
            });
        }
        animator.start();
    }

    private void collapse() {
        if (shouldBeExpanded()) {
            return;
        }
        if (message.getId().equals(container.getExpandedMessageId())) {
            container.setExpandedMessageId(null);
        }
        int origHeight = view.getHeight();

        ValueAnimator animator = createHeightAnimator(view, origHeight, 0);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animator) {
                view.setVisibility(View.GONE);
            }
        });
        animator.start();
    }

    private void showLikeDetails() {
        int height = view.getHeight();
        getViewTextViewAnimator(messageStatusTextView, false, height).start();
        getViewTextViewAnimator(likeDetails, true, 0).start();
    }

    private void showMessageStatus() {
        int height = view.getHeight();
        getViewTextViewAnimator(messageStatusTextView, true, 0).start();
        getViewTextViewAnimator(likeDetails, false, -height).start();
    }

    private ObjectAnimator getViewTextViewAnimator(final View view, boolean animateIn, float to) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, to);
        animator.setDuration(context.getResources().getInteger(com.waz.zclient.ui.R.integer.wire__animation__duration__short));
        if (animateIn) {
            animator.setInterpolator(new Expo.EaseOut());
        } else {
            animator.setInterpolator(new Expo.EaseIn());
        }
        if (animateIn) {
            animator.setStartDelay(context.getResources().getInteger(com.waz.zclient.ui.R.integer.animation_delay_short));
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    view.setVisibility(View.VISIBLE);
                }
            });
        } else {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    view.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(View.GONE);
                }
            });
        }
        return animator;
    }

    private void showTimestampForABit() {
        mainHandler.removeCallbacksAndMessages(null);
        if (likeDetails.getVisibility() == View.VISIBLE) {
            showMessageStatus();
            mainHandler.postDelayed(showLikeDetailsRunnable, TIMESTAMP_VISIBILITY_MIL_SEC);
        } else {
            showLikeDetails();
        }
    }

    public ValueAnimator createHeightAnimator(final View view, final int start, final int end) {
        ValueAnimator animator = ValueAnimator.ofInt(start, end);
        animator.setDuration(250);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator valueAnimator) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                if (layoutParams == null) {
                    // needed for tests
                    return;
                }
                layoutParams.height = (Integer) valueAnimator.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }
        });
        return animator;
    }

    @Override
    public boolean toggleVisibility() {
        if (message == null) {
            return false;
        }
        if (view.getVisibility() == View.GONE || view.getMeasuredHeight() == 0) {
            expand();
            return true;
        } else if (likeButton.getVisibility() == View.GONE && shouldShowLikeButton()) {
            likeButton.setVisibility(View.VISIBLE);
            return true;
        } else if (message.isLiked()) {
            showTimestampForABit();
            return true;
        } else {
            collapse();
            return false;
        }
    }

    @Override
    public void close() {
        if (message != null) {
            collapse();
        } else {
            view.setVisibility(View.GONE);
        }
        if (container.getExpandedView() == this) {
            container.setExpandedView(null);
        }
    }

    @Override
    public void onClickedLikersAvatars() {
        if (container != null) {
            container.getControllerFactory().getConversationScreenController().showLikesList(message);
        }
    }

    private boolean shouldShowLikeButton() {
        return !(message.getMessageStatus() == Message.Status.FAILED ||
                 message.getMessageStatus() == Message.Status.FAILED_READ ||
                 message.getMessageStatus() == Message.Status.PENDING);
    }
}
