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
package com.waz.zclient.pages.extendedcursor.image;

import android.content.Context;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.waz.utils.wrappers.URI;
import com.waz.zclient.R;
import com.waz.zclient.ui.animation.interpolators.penner.Quint;
import com.waz.zclient.utils.ViewUtils;

public class CursorImagesLayout extends FrameLayout implements View.OnClickListener, CursorImagesAdapter.AdapterCallback {

    private static final int IMAGE_ROWS = 3;

    private RecyclerView recyclerView;
    private CursorImagesAdapter cursorImagesAdapter;

    private View buttonNavToCamera;
    private View buttonOpenGallery;

    private Callback callback;

    public CursorImagesLayout(Context context) {
        this(context, null);
    }

    public CursorImagesLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CursorImagesLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        recyclerView = ViewUtils.getView(this, R.id.rv__cursor_images);
        recyclerView.setHasFixedSize(true);

        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        GridLayoutManager layout = new GridLayoutManager(getContext(), IMAGE_ROWS, GridLayoutManager.HORIZONTAL, false);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return (cursorImagesAdapter.getItemViewType(position) == CursorImagesAdapter.VIEW_TYPE_CAMERA) ? IMAGE_ROWS : 1;
            }
        });
        recyclerView.setLayoutManager(layout);
        int dividerSpacing = getContext().getResources().getDimensionPixelSize(R.dimen.extended_container__camera__gallery_grid__divider__spacing);
        recyclerView.addItemDecoration(new CursorImagesItemDecoration(dividerSpacing));

        buttonNavToCamera = ViewUtils.getView(this, R.id.gtv__cursor_image__nav_camera_back);
        buttonOpenGallery = ViewUtils.getView(this, R.id.gtv__cursor_image__nav_open_gallery);

        buttonNavToCamera.setVisibility(View.GONE);
        buttonNavToCamera.setOnClickListener(this);
        buttonOpenGallery.setOnClickListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        cursorImagesAdapter = new CursorImagesAdapter(getContext(), this);
        cursorImagesAdapter.setCallback(callback);

        recyclerView.setAdapter(cursorImagesAdapter);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (cursorImagesAdapter != null) {
            cursorImagesAdapter.close();
            cursorImagesAdapter = null;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.gtv__cursor_image__nav_camera_back:
                recyclerView.smoothScrollToPosition(0);
                break;
            case R.id.gtv__cursor_image__nav_open_gallery:
                if (callback != null) {
                    callback.openGallery();
                }
                break;
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;

        if (cursorImagesAdapter != null) {
            cursorImagesAdapter.setCallback(callback);
        }
    }

    @Override
    public void onCameraPreviewDetached() {
        buttonNavToCamera.setVisibility(View.VISIBLE);
        buttonNavToCamera.setAlpha(0);
        buttonNavToCamera
            .animate()
            .setInterpolator(new Quint.EaseOut())
            .alpha(1);
    }

    @Override
    public void onCameraPreviewAttached() {
        buttonNavToCamera
            .animate()
            .alpha(0)
            .setDuration(getResources().getInteger(R.integer.animation_duration_short))
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    buttonNavToCamera.setAlpha(1);
                    buttonNavToCamera.setVisibility(View.GONE);
                }
            });
    }

    public void onClose() {
        if (cursorImagesAdapter != null) {
            cursorImagesAdapter.close();
        }
    }

    public interface Callback {
        void openCamera();

        void openVideo();

        void onGalleryPictureSelected(URI uri);

        void openGallery();

        void onPictureTaken(byte[] imageData);
    }
}
