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
package com.waz.zclient.pages.main.profile.preferences.dialogs;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorChangeRequester;
import com.waz.zclient.core.controllers.tracking.events.settings.ChangedAccentColorEvent;
import com.waz.zclient.pages.BaseDialogFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.utils.ViewUtils;

public class AccentColorPreferenceDialogFragment extends BaseDialogFragment<AccentColorPreferenceDialogFragment.Container> implements OnItemClickListener {

    public static final String TAG = AccentColorPreferenceDialogFragment.class.getName();
    private static final String ARG_COLOR = "ARG_COLOR";

    public static Fragment newInstance(@ColorInt int accentColor) {
        AccentColorPreferenceDialogFragment f = new AccentColorPreferenceDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLOR, accentColor);
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    @SuppressLint("InflateParams")
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final LayoutInflater inflater = LayoutInflater.from(getActivity());
        final View view = inflater.inflate(R.layout.preference_dialog_accent_color, null);
        final RecyclerView recyclerView = ViewUtils.getView(view, R.id.rv__accent_color);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        AccentColorAdapter adapter = new AccentColorAdapter(getContext(), getArguments().getInt(ARG_COLOR));
        adapter.setOnItemClickListener(this);
        recyclerView.setAdapter(adapter);
        return new AlertDialog.Builder(getActivity()).setView(view)
                                                     .setCancelable(true)
                                                     .create();
    }

    @Override
    public void onItemClick(@ColorInt int accentColor) {
        getControllerFactory().getAccentColorController().setColor(AccentColorChangeRequester.SETTINGS, accentColor);
        getStoreFactory().getProfileStore().setAccentColor(this, accentColor);
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ChangedAccentColorEvent());
        dismiss();
    }

    public interface Container {
    }

    private static class AccentColorAdapter extends RecyclerView.Adapter<AccentColorViewHolder> {
        private int[] accentColors;
        private Context context;
        private int selectedAccentColor;
        private OnItemClickListener onItemClickListener;

        AccentColorAdapter(Context context, @ColorInt int selectedAccentColor) {
            this.context = context;
            this.selectedAccentColor = selectedAccentColor;
            this.accentColors = context.getResources().getIntArray(R.array.selectable_accents_color);
        }

        @Override
        public AccentColorViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(context);
            View view = layoutInflater.inflate(R.layout.preference_dialog_accent_color_item, parent, false);
            return new AccentColorViewHolder(view, onItemClickListener);
        }

        @Override
        public void onBindViewHolder(AccentColorViewHolder holder, int position) {
            holder.bind(accentColors[position], selectedAccentColor == accentColors[position]);
        }

        @Override
        public int getItemCount() {
            return accentColors.length;
        }

        public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
            this.onItemClickListener = onItemClickListener;
        }
    }

    private static class AccentColorViewHolder extends RecyclerView.ViewHolder {

        private View selectionView;
        private View view;
        private OnItemClickListener listener;

        AccentColorViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            view = itemView;
            this.listener = listener;
            view = ViewUtils.getView(itemView, R.id.v__accent_color);
            selectionView = ViewUtils.getView(itemView, R.id.gtv__accent_color__selected);
        }

        public void bind(@ColorInt final int color, boolean selected) {
            selectionView.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            view.setBackgroundColor(color);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onItemClick(color);
                    }
                }
            });
        }
    }
}

interface OnItemClickListener {
    void onItemClick(@ColorInt int accentColor);
}
