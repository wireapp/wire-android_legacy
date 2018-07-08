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
package com.waz.zclient.controllers.verification;

import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;

public class VerificationController implements IVerificationController {

    private IUserPreferencesController userPreferencesController;

    private String verificationCode;

    public VerificationController(IUserPreferencesController userPreferencesController) {
        this.userPreferencesController = userPreferencesController;
        if (userPreferencesController.hasVerificationCode()) {
            verificationCode = userPreferencesController.getVerificationCode();
        }
    }

    @Override
    public void tearDown() {
        userPreferencesController = null;
        verificationCode = null;
    }

    @Override
    public void setVerificationCode(String code) {
        verificationCode = code;
        userPreferencesController.setVerificationCode(verificationCode);
    }

    @Override
    public void startVerification() {
        finishVerification();
    }

    @Override
    public String getVerificationCode() {
        return verificationCode;
    }

    @Override
    public void finishVerification() {
        verificationCode = null;
        userPreferencesController.removeVerificationCode();
    }
}
