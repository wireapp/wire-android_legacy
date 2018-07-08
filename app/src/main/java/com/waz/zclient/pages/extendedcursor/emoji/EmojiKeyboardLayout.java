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
package com.waz.zclient.pages.extendedcursor.emoji;


import android.app.Instrumentation;
import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import com.waz.threading.Threading;
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

public class EmojiKeyboardLayout extends LinearLayout {

    private static final int SPAN_COUNT = 4;
    private static final int CATEGORY_COUNT = 9;
    private static final int TAB_COUNT = 10;

    private Callback callback;
    private EmojiAdapter emojiAdapter;
    private RecyclerView recyclerView;
    private GridLayoutManager layoutManager;
    private TabIndicatorLayout tapIndicatorLayout;
    private EmojiSize currentEmojiSize;
    private int[] categoryPositions;

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

        tapIndicatorLayout = ViewUtils.getView(this, R.id.til__emoji_keyboard);
        recyclerView = ViewUtils.getView(this, R.id.rv__emoji_keyboard);
        init();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setEmojis(List<String> recent, Set<String> unsupported) {
        populateEmojis(recent, unsupported);
        emojiAdapter.setEmojis(emojis, currentEmojiSize);
        updateCategoryPositions(unsupported);
        if (recent != null && recent.size() > 0) {
            tapIndicatorLayout.setSelected(0);
        } else {
            tapIndicatorLayout.setSelected(1);
        }
    }

    private void init() {
        currentEmojiSize = EmojiSize.MEDIUM;
        categoryPositions = new int[CATEGORY_COUNT];

        emojiAdapter = new EmojiAdapter(getContext());

        layoutManager = new GridLayoutManager(getContext(),
                                              SPAN_COUNT,
                                              LinearLayoutManager.HORIZONTAL,
                                              false);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return spaces.contains(position) ? SPAN_COUNT : 1;
            }
        });
        layoutManager.setSpanCount(SPAN_COUNT);

        recyclerView.addOnScrollListener(new EmojiScrollListener());

        tapIndicatorLayout.setShowDivider(false);
        tapIndicatorLayout.setGlyphLabels(Emojis.EMOJI_KEYBOARD_TAB_LABELS);
        tapIndicatorLayout.setTextColor(ContextCompat.getColorStateList(getContext(), R.color.wire__text_color_dark_selector));
        tapIndicatorLayout.setPrimaryColor(ContextCompat.getColor(getContext(), R.color.text__primary_dark));
        tapIndicatorLayout.setLabelHeight(getContext().getResources().getDimensionPixelSize(R.dimen.sketch__emoji__keyboard__tab_label_size));
        tapIndicatorLayout.setCallback(new TabIndicatorLayout.Callback() {
            @Override
            public void onItemSelected(int pos) {
                if (ViewCompat.getLayoutDirection(EmojiKeyboardLayout.this) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                    // Revert tab position for RTL layout
                    pos = (TAB_COUNT - 1) - pos;
                }

                if (pos == TAB_COUNT - 1) {
                    Threading.Background().execute(new Runnable() {
                        @Override
                        public void run() {
                            Instrumentation inst = new Instrumentation();
                            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL);
                        }
                    });
                } else {
                    tapIndicatorLayout.setSelected(pos);
                    layoutManager.scrollToPositionWithOffset(getCategoryByTabPosition(pos), 0);
                }
            }
        });

        recyclerView.setAdapter(emojiAdapter);
        recyclerView.setLayoutManager(layoutManager);
        setRecyclerViewPadding(recyclerView);

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
        if (recent != null && recent.size() > 0) {
            emojis.addAll(recent);
            spaces.add(emojis.size());
            emojis.add(EmojiAdapter.SPACE);
        }
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
                padding = getContext().getResources().getDimensionPixelOffset(R.dimen.sketch__emoji__keyboard__item_padding__small);
                break;
            case MEDIUM:
                padding = getContext().getResources().getDimensionPixelOffset(R.dimen.sketch__emoji__keyboard__item_padding__medium);
                break;
            case LARGE:
                padding = getContext().getResources().getDimensionPixelOffset(R.dimen.sketch__emoji__keyboard__item_padding__large);
                break;
            default:
                padding = getContext().getResources().getDimensionPixelOffset(R.dimen.sketch__emoji__keyboard__item_padding__small);
                break;
        }
        int sidePadding = getContext().getResources().getDimensionPixelSize(R.dimen.sketch__emoji__keyboard__side_padding);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(sidePadding, padding, sidePadding, padding);
    }

    private int getCategoryByTabPosition(int tabPos) {
        if (tabPos >= categoryPositions.length) {
            return 0;
        }
        return categoryPositions[tabPos];
    }

    private int getTabByItemPosition(int itemPos) {
        for (int i = 0; i < categoryPositions.length - 1; i++) {
            if (itemPos >= categoryPositions[categoryPositions.length - 1]) {
                return categoryPositions.length - 1;
            }

            int categoryFirstItem = categoryPositions[i];
            int nextCategoryFirstItem = categoryPositions[i + 1];
            if (itemPos >= categoryFirstItem &&
                itemPos < nextCategoryFirstItem) {
                return i;
            }
        }

        return 0;
    }

    private void updateCategoryPositions(Set<String> unsupported) {
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            int pos = 0;
            List<String> filteredList = null;
            switch (i) {
                case 1:
                    filteredList = getFilteredList(Emojis.PEOPLE, unsupported);
                    break;
                case 2:
                    filteredList = getFilteredList(Emojis.NATURE, unsupported);
                    break;
                case 3:
                    filteredList = getFilteredList(Emojis.FOOD_AND_DRINK, unsupported);
                    break;
                case 4:
                    filteredList = getFilteredList(Emojis.ACTIVITY, unsupported);
                    break;
                case 5:
                    filteredList = getFilteredList(Emojis.TRAVEL_AND_PLACES, unsupported);
                    break;
                case 6:
                    filteredList = getFilteredList(Emojis.OBJECTS, unsupported);
                    break;
                case 7:
                    filteredList = getFilteredList(Emojis.SYMBOLS, unsupported);
                    break;
                case 8:
                    filteredList = getFilteredList(Emojis.FLAGS, unsupported);
                    break;
                default:
                    pos = 0;
                    break;
            }
            if (filteredList != null && filteredList.size() > 1) {
                pos = emojis.indexOf(filteredList.get(0));
                if (pos < 0) {
                    pos = 0;
                }
            }
            categoryPositions[i] = pos;
        }
    }

    private class EmojiScrollListener extends RecyclerView.OnScrollListener {
        private EmojiScrollListener() {
            super();
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            int itemPos = layoutManager.findFirstCompletelyVisibleItemPosition();
            tapIndicatorLayout.setSelected(getTabByItemPosition(itemPos));
        }
    }

    public interface Callback {
        void onEmojiSelected(String emoji);
    }
}
