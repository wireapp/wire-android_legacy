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
package com.waz.zclient.ui.colorpicker;

import android.content.Context;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.utils.ViewUtils;
import java.util.List;

public class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.ViewHolder> {

    public static final String SPACE = " ";
    private List<String> emojis;
    private OnEmojiClickListener onEmojiClickListener;
    private EmojiSize emojiSize;

    private final int smallSize;
    private final int mediumSize;
    private final int largeSize;
    private final int categorySpacing;
    private final int textColor;

    public EmojiAdapter(Context context) {
        super();

        emojiSize = EmojiSize.SMALL;
        smallSize = context.getResources().getDimensionPixelSize(R.dimen.sketch__emoji__keyboard__item_size__small);
        mediumSize = context.getResources().getDimensionPixelSize(R.dimen.sketch__emoji__keyboard__item_size__medium);
        largeSize = context.getResources().getDimensionPixelSize(R.dimen.sketch__emoji__keyboard__item_size__large);
        categorySpacing = context.getResources().getDimensionPixelSize(R.dimen.sketch__emoji__keyboard__category_spacing);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            textColor = context.getResources().getColor(R.color.text__primary_dark);
        } else {
            textColor = context.getResources().getColor(R.color.text__primary_dark, context.getTheme());
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        FrameLayout frameLayout = new FrameLayout(parent.getContext());
        TextView textView = new TextView(parent.getContext());
        textView.setId(R.id.emoji_keyboard_item);
        textView.setTextColor(textColor);
        textView.setGravity(Gravity.CENTER);
        frameLayout.addView(textView);
        return new ViewHolder(frameLayout);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        String emoji = emojis.get(position);
        holder.bind(emoji);
    }

    @Override
    public int getItemCount() {
        return emojis == null ? 0 : emojis.size();
    }

    public void setOnEmojiClickListener(OnEmojiClickListener onEmojiClickListener) {
        this.onEmojiClickListener = onEmojiClickListener;
    }

    public void setEmojis(List<String> emojis, EmojiSize emojiSize) {
        this.emojis = emojis;
        this.emojiSize = emojiSize;
        notifyDataSetChanged();
    }

    public void setEmojiSize(EmojiSize emojiSize) {
        this.emojiSize = emojiSize;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView textView;

        public ViewHolder(final View itemView) {
            super(itemView);
            textView = ViewUtils.getView(itemView, R.id.emoji_keyboard_item);
            textView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onEmojiClickListener != null) {
                        onEmojiClickListener.onEmojiClick(textView.getText().toString(), emojiSize);
                    }
                }
            });
        }

        public void bind(String string) {
            textView.setText(string);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, itemView.getResources().getDimension(emojiSize.getIconSizeResId()));
            int size;
            if (SPACE.equals(string)) {
                size = categorySpacing;
            } else {
                switch (emojiSize) {
                    case LARGE:
                        size = largeSize;
                        break;
                    case MEDIUM:
                        size = mediumSize;
                        break;
                    case SMALL:
                    default:
                        size = smallSize;
                }
            }
            ViewGroup.LayoutParams params = textView.getLayoutParams();
            params.width = size;
            params.height = size;
            textView.setLayoutParams(params);
            textView.invalidate();
        }
    }

    public interface OnEmojiClickListener {
        void onEmojiClick(String emoji, EmojiSize emojiSize);
    }

}
