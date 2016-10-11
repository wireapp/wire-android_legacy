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
package com.waz.zclient.pages.main.conversation;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.VideoView;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;
import com.waz.zclient.utils.ViewUtils;


public class VideoPlayerFragment extends BaseFragment<VideoPlayerFragment.Container> implements OnBackPressedListener {

    public static final String TAG = VideoPlayerFragment.class.getName();

    private static final String ARG_VIDEO_URI = "ARG_VIDEO_URI";

    private VideoView videoView;
    private View closeButton;
    private View headerContainer;
    private MediaController mediaController;

    private View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                fadeHeader(!mediaController.isShowing());
            }
            return true;

        }
    };

    public static VideoPlayerFragment newInstance(Uri uri) {
        VideoPlayerFragment fragment = new VideoPlayerFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_VIDEO_URI, uri);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video_player, container, false);

        headerContainer = ViewUtils.getView(view, R.id.rl__video_player__header);

        closeButton = ViewUtils.getView(view, R.id.gtv__video_player__close);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
                    return;
                }
                getControllerFactory().getSingleImageController().hideVideo();
            }
        });

        videoView = ViewUtils.getView(view, R.id.vv__video_view);
        videoView.setVideoURI((Uri) getArguments().getParcelable(ARG_VIDEO_URI));

        mediaController = new MediaController(getContext());
        mediaController.setMediaPlayer(videoView);

        videoView.setMediaController(mediaController);
        videoView.requestFocus();
        videoView.start();
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {

            }
        });

        videoView.setOnTouchListener(onTouchListener);
        view.setOnTouchListener(onTouchListener);
        mediaController.show(0);

        return view;
    }

    private void fadeHeader(final boolean fadeIn) {
        headerContainer.animate()
                       .alpha(fadeIn ? 1f : 0f)
                       .setInterpolator(new Quart.EaseOut())
                       .setDuration(getResources().getInteger(R.integer.single_image_message__overlay__fade_duration))
                       .withStartAction(new Runnable() {
                           @Override
                           public void run() {
                               if (fadeIn) {
                                   headerContainer.setVisibility(View.VISIBLE);
                                   mediaController.show(0);
                               }
                           }
                       })
                       .withEndAction(new Runnable() {
                           @Override
                           public void run() {
                               if (!fadeIn) {
                                   headerContainer.setVisibility(View.GONE);
                                   mediaController.hide();
                               }
                           }
                       })
                       .start();
    }

    @Override
    public boolean onBackPressed() {
        if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
            return false;
        }
        getControllerFactory().getSingleImageController().hideVideo();
        return true;
    }

    public interface Container {

    }
}
