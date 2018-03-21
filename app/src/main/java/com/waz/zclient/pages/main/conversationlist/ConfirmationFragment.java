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
package com.waz.zclient.pages.main.conversationlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.confirmation.TwoButtonConfirmationCallback;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.menus.ConfirmationMenu;

public class ConfirmationFragment extends BaseFragment<ConfirmationFragment.Container> implements OnBackPressedListener,
                                                                                                  AccentColorObserver {

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_CONFIRM = "confirm";
    public static final String OPTIONAL_FIELD_CANCEL = "cancel";
    public static final String OPTIONAL_FIELD_DIALOG_ID = "dialog_id";
    public static final String OPTIONAL_FIELD_USE_BLACK_BACKGROUND = "black_background";

    public static final String TAG = ConfirmationFragment.class.getName();

    private String dialogId;
    private ConfirmationMenu confirmationMenu;

    public static ConfirmationFragment newMessageOnlyInstance(String title,
                                                              String message,
                                                              String confirm,
                                                              String dialogId) {
        ConfirmationFragment confirmationFragment = new ConfirmationFragment();
        Bundle args = new Bundle();
        args.putString(FIELD_TITLE, title);
        args.putString(FIELD_MESSAGE, message);
        args.putString(FIELD_CONFIRM, confirm);
        args.putString(OPTIONAL_FIELD_DIALOG_ID, dialogId);
        args.putBoolean(OPTIONAL_FIELD_USE_BLACK_BACKGROUND, true);
        confirmationFragment.setArguments(args);
        return confirmationFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.confirmation_fragment, container, false);

        dialogId = getArguments().getString(OPTIONAL_FIELD_DIALOG_ID);

        confirmationMenu = ViewUtils.getView(view, R.id.cm__confirm_menu);
        confirmationMenu.setHeader(getArguments().getString(FIELD_TITLE));
        confirmationMenu.setText(getArguments().getString(FIELD_MESSAGE));
        confirmationMenu.setPositiveButton(getArguments().getString(FIELD_CONFIRM));
        confirmationMenu.setNegativeButton(getArguments().getString(OPTIONAL_FIELD_CANCEL));
        confirmationMenu.useModalBackground(getArguments().getBoolean(OPTIONAL_FIELD_USE_BLACK_BACKGROUND));

        confirmationMenu.setCallback(new TwoButtonConfirmationCallback() {

            @Override
            public void positiveButtonClicked(boolean checkboxIsSelected) {
                getContainer().onDialogConfirm(dialogId);
            }

            @Override
            public void negativeButtonClicked() {
                getContainer().onDialogCancel(dialogId);
            }

            @Override
            public void onHideAnimationEnd(boolean confirmed, boolean canceled, boolean checkboxIsSelected) {

            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        confirmationMenu.animateToShow(true);
    }

    @Override
    public void onStop() {
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);

        super.onStop();
    }

    @Override
    public void onDestroyView() {
        confirmationMenu = null;
        super.onDestroyView();
    }

    @Override
    public boolean onBackPressed() {
        confirmationMenu.animateToShow(false);
        getContainer().onDialogCancel(dialogId);
        return true;
    }

    @Override
    public void onAccentColorHasChanged(int color) {
        confirmationMenu.setButtonColor(color);
    }

    public interface Container {
        /**
         * @param dialogId The {@link ConfirmationFragment#OPTIONAL_FIELD_DIALOG_ID} argument passed to the Fragment.
         *                 Can be useful for identifying the callback. Will be an empty string if no dialogId
         *                 argument is passed to the Fragment.
         */
        void onDialogConfirm(String dialogId);

        /**
         * @param dialogId The {@link ConfirmationFragment#OPTIONAL_FIELD_DIALOG_ID} argument passed to the Fragment.
         *                 Can be useful for identifying the callback. Will be an empty string if no dialogId
         *                 argument is passed to the Fragment.
         */
        void onDialogCancel(String dialogId);
    }
}
