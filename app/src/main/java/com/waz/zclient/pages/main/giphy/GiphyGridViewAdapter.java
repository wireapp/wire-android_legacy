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
package com.waz.zclient.pages.main.giphy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.GiphyResults;
import com.waz.api.ImageAsset;
import com.waz.api.LoadHandle;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.conversation.views.AspectRatioImageView;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.utils.ViewUtils;

public class GiphyGridViewAdapter extends RecyclerView.Adapter<GiphyGridViewAdapter.ViewHolder> {

    private ScrollGifCallback scrollGifCallback;
    private Context context;
    private GiphyResults giphyResults;


    public GiphyGridViewAdapter(Context context) {
        this.context = context;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View rootView = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_giphy_image, parent, false);

        return new ViewHolder(context, rootView, scrollGifCallback);
    }

    public void setScrollGifCallback(ScrollGifCallback scrollGifCallback) {
        this.scrollGifCallback = scrollGifCallback;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final ImageAsset imageAsset = giphyResults.get(position);
        holder.setImageAsset(imageAsset, position);
    }

    @Override
    public int getItemCount() {
        return giphyResults != null ? giphyResults.size() : 0;
    }

    public void setGiphyResults(GiphyResults giphyResults) {
        this.giphyResults = giphyResults;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements ImageAsset.BitmapCallback {

        private final Context context;
        private int animationDuration;
        private AspectRatioImageView gifPreview;
        private LoadHandle bitmapLoadHandle;
        private ImageAsset imageAsset;


        public ViewHolder(Context context, View itemView, final ScrollGifCallback scrollGifCallback) {
            super(itemView);
            animationDuration = context.getResources().getInteger(R.integer.framework_animation_duration_short);
            this.context = context;
            this.gifPreview = ViewUtils.getView(itemView, R.id.iv__row_giphy_image);
            this.gifPreview.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (scrollGifCallback != null) {
                        scrollGifCallback.setSelectedGifFromGridView(imageAsset);
                    }
                }
            });
        }

        public void setImageAsset(ImageAsset imageAsset, int position) {
            if (this.imageAsset == imageAsset) {
                return;
            }
            if (gifPreview != null) {
                gifPreview.setImageBitmap(null);
            }
            this.imageAsset = imageAsset;
            float imageAssetHeight = imageAsset.getHeight();
            float aspectRatio = (float) imageAsset.getWidth() / imageAssetHeight;
            if (MathUtils.floatEqual(imageAssetHeight, 0)) {
                aspectRatio = 1f;
            }
            int[] colorArray = context.getResources().getIntArray(R.array.accents_color);
            gifPreview.setImageDrawable(new ColorDrawable(colorArray[position % (colorArray.length - 1)]));
            gifPreview.setAspectRatio(aspectRatio);
            if (bitmapLoadHandle != null) {
                bitmapLoadHandle.cancel();
            }
            int width = gifPreview.getMeasuredWidth();
            if (width == 0) {
                width = imageAsset.getWidth();
            }
            bitmapLoadHandle = imageAsset.getSingleBitmap(width, this);
        }

        @Override
        public void onBitmapLoaded(final Bitmap bitmap, boolean b) {
            final Drawable[] images = new Drawable[2];
            images[0] = gifPreview.getDrawable();
            images[1] = new BitmapDrawable(context.getResources(), bitmap);
            TransitionDrawable crossfader = new TransitionDrawable(images);
            gifPreview.setImageDrawable(crossfader);
            crossfader.startTransition(animationDuration);
            gifPreview.setImageBitmap(bitmap);
        }

        @Override
        public void onBitmapLoadingFailed() {}
    }

    public interface ScrollGifCallback {
        void setSelectedGifFromGridView(ImageAsset gifAsset);
    }

}
