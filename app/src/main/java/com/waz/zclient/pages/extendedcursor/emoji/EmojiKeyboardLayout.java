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
package com.waz.zclient.pages.extendedcursor.emoji;


import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.waz.zclient.R;
import com.waz.zclient.ui.colorpicker.EmojiAdapter;
import com.waz.zclient.ui.colorpicker.EmojiSize;
import com.waz.zclient.ui.views.tab.TabIndicatorLayout;
import com.waz.zclient.utils.Emojis;
import com.waz.zclient.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class EmojiKeyboardLayout extends FrameLayout {

    private static final int SPAN_COUNT = 6;
    private Callback callback;
    private LinearLayout emojiContainerView;
    private EmojiAdapter emojiAdapter;
    private EmojiSize currentEmojiSize;

    private List<String> emojis;
    private List<Integer> spaces;

    public EmojiKeyboardLayout(Context context) {
        this(context, null);
    }

    public EmojiKeyboardLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmojiKeyboardLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        emojiContainerView = ViewUtils.getView(this, R.id.ll__emoji_keyboard__emoji_container);
        init();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setEmojis(List<String> recent, Set<String> unsupported) {
        populateEmojis(recent, unsupported);
        emojiAdapter.setEmojis(emojis, currentEmojiSize);
    }

    private void init() {
        currentEmojiSize = EmojiSize.SMALL;

        emojiAdapter = new EmojiAdapter(getContext());



        final GridLayoutManager layoutManager = new GridLayoutManager(getContext(),
                                                                      SPAN_COUNT,
                                                                      LinearLayoutManager.HORIZONTAL,
                                                                      false);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return spaces.contains(position) ? SPAN_COUNT : 1;
            }
        });

        final RecyclerView recyclerView = new RecyclerView(getContext());

        final TabIndicatorLayout til = new TabIndicatorLayout(getContext());
        int[] labels = new int[1];
        labels[0] = com.waz.zclient.ui.R.string.emoji_keyboard_tab_1;
        til.setLabels(labels);
        til.setSelected(currentEmojiSize.ordinal());
        til.setTextColor(ContextCompat.getColorStateList(getContext(), com.waz.zclient.ui.R.color.wire__text_color_dark_selector));
        til.setPrimaryColor(ContextCompat.getColor(getContext(), com.waz.zclient.ui.R.color.text__primary_dark));
        til.setLabelHeight(getContext().getResources().getDimensionPixelSize(com.waz.zclient.ui.R.dimen.sketch__emoji__keyboard__tab_label_size));
        til.setCallback(new TabIndicatorLayout.Callback() {
            @Override
            public void onItemSelected(int pos) {
                til.setSelected(pos);
                currentEmojiSize = EmojiSize.values()[pos];
                emojiAdapter.setEmojiSize(currentEmojiSize);
                setRecyclerViewPadding(recyclerView);
                layoutManager.setSpanCount(SPAN_COUNT);
            }
        });
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                   getContext().getResources().getDimensionPixelSize(com.waz.zclient.ui.R.dimen.sketch__emoji__keyboard__tab_size));
        emojiContainerView.addView(til, params);

        recyclerView.setAdapter(emojiAdapter);
        recyclerView.setLayoutManager(layoutManager);
        setRecyclerViewPadding(recyclerView);
        emojiContainerView.addView(recyclerView);

        emojiAdapter.setOnEmojiClickListener(new EmojiAdapter.OnEmojiClickListener() {
            @Override
            public void onEmojiClick(String emoji, EmojiSize emojiSize) {
                if (callback != null) {
                    callback.onEmojiSelected(emoji);
                }
            }
        });
    }

    private List<String> getFilteredList(String[] strings, Set<String> unsupported) {
        List<String> list = Arrays.asList(strings);
        if (unsupported == null || unsupported.size() == 0) {
            return list;
        }
        LinkedList<String> filteredList = new LinkedList<>(list);
        filteredList.removeAll(unsupported);
        return filteredList;
    }

    private void populateEmojis(List<String> recent, Set<String> unsupported) {
        spaces = new ArrayList<>();
        emojis = new ArrayList<>();
        for (String[] emojiArray : Emojis.getAllEmojisSortedByCategory()) {
            emojis.addAll(getFilteredList(emojiArray, unsupported));
            spaces.add(emojis.size());
            emojis.add(EmojiAdapter.SPACE);
        }
    }

    private void setRecyclerViewPadding(RecyclerView recyclerView) {
        int padding;
        switch (currentEmojiSize) {
            case SMALL:
                padding = getContext().getResources().getDimensionPixelOffset(com.waz.zclient.ui.R.dimen.sketch__emoji__keyboard__item_padding__small);
                break;
            case MEDIUM:
                padding = getContext().getResources().getDimensionPixelOffset(com.waz.zclient.ui.R.dimen.sketch__emoji__keyboard__item_padding__medium);
                break;
            case LARGE:
                padding = getContext().getResources().getDimensionPixelOffset(com.waz.zclient.ui.R.dimen.sketch__emoji__keyboard__item_padding__large);
                break;
            default:
                padding = getContext().getResources().getDimensionPixelOffset(com.waz.zclient.ui.R.dimen.sketch__emoji__keyboard__item_padding__small);
                break;
        }
        int sidePadding = getContext().getResources().getDimensionPixelSize(com.waz.zclient.ui.R.dimen.sketch__emoji__keyboard__side_padding);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(sidePadding, padding, sidePadding, padding);
    }

    public interface Callback {
        void onEmojiSelected(String emoji);
    }
}
