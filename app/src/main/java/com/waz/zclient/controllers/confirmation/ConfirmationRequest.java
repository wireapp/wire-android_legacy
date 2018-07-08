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
package com.waz.zclient.controllers.confirmation;

import android.support.annotation.DrawableRes;
import android.text.TextUtils;
import com.waz.zclient.ui.theme.OptionsTheme;

public class ConfirmationRequest {
    public String header;
    public String message;
    public String positiveButton;
    public String negativeButton;
    public boolean cancelVisible;
    public String checkboxLabel;
    public boolean checkboxSelectedByDefault;
    public int headerIconRes;
    public int backgroundImage;
    public ConfirmationCallback callback;
    public OptionsTheme optionsTheme;

    private ConfirmationRequest() {}

    public static class Builder {

        private final ConfirmationRequest confirmationRequest;

        public Builder() {
            confirmationRequest = new ConfirmationRequest();
            confirmationRequest.checkboxLabel = "";
        }

        public Builder withHeader(String header) {
            confirmationRequest.header = header;
            return this;
        }

        public Builder withMessage(String message) {
            confirmationRequest.message = message;
            return this;
        }

        public Builder withPositiveButton(String positiveButton) {
            confirmationRequest.positiveButton = positiveButton;
            return this;
        }

        public Builder withNegativeButton(String negativeButton) {
            confirmationRequest.negativeButton = negativeButton;
            return this;
        }

        public Builder withCancelButton() {
            confirmationRequest.cancelVisible = true;
            return this;
        }

        public Builder withCheckboxLabel(String checkboxLabel) {
            confirmationRequest.checkboxLabel = checkboxLabel;
            return this;
        }

        public Builder withHeaderIcon(@DrawableRes int headerIconRes) {
            confirmationRequest.headerIconRes = headerIconRes;
            return this;
        }

        public Builder withBackgroundImage(@DrawableRes int backgroundImage) {
            confirmationRequest.backgroundImage = backgroundImage;
            return this;
        }

        public Builder withCheckboxSelectedByDefault() {
            confirmationRequest.checkboxSelectedByDefault = true;
            return this;
        }

        public Builder withConfirmationCallback(ConfirmationCallback confirmationCallback) {
            confirmationRequest.callback = confirmationCallback;
            return this;
        }

        public Builder withWireTheme(OptionsTheme optionsTheme) {
            confirmationRequest.optionsTheme = optionsTheme;
            return this;
        }

        public ConfirmationRequest build() {
            assertExist(confirmationRequest.header);
            assertExist(confirmationRequest.message);
            assertExist(confirmationRequest.negativeButton);
            assertExist(confirmationRequest.positiveButton);
            return confirmationRequest;
        }

        private void assertExist(String text) {
            if (TextUtils.isEmpty(text)) {
                throw new IllegalStateException("ConfirmationRequest must be fully equipped!!");
            }
        }
    }
}
