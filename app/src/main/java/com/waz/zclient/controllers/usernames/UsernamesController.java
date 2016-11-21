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
package com.waz.zclient.controllers.usernames;
import android.app.Activity;
import android.text.TextUtils;

import com.waz.api.Self;
import com.waz.api.UsernameValidation;
import com.waz.api.Usernames;
import com.waz.api.UsernamesRequestCallback;
import com.waz.zclient.ZApplication;
import com.waz.zclient.core.api.scala.ModelObserver;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

import timber.log.Timber;

public class UsernamesController implements IUsernamesController {

    private static final int MAX_ATTEMPTS = 10;
    private static final int MAX_RANDOM_TRAILLING_NUMBER = 10000;

    private ZApplication application = null;
    private Random randomGenerator;
    private Set<UsernamesControllerObserver> usernamesControllerObservers = Collections.newSetFromMap(
        new WeakHashMap<UsernamesControllerObserver, Boolean>());

    private class GeneratedUsername {
        final String username;
        final String searchedName;
        GeneratedUsername(String username, String searchedName) {
            this.username = username;
            this.searchedName = searchedName;
        }
        boolean isValid() {
            return !TextUtils.isEmpty(username);
        }
    }

    private GeneratedUsername generatedUsername = null;
    private String currentSearch;

    private ModelObserver<Self> userModelObserver = new ModelObserver<Self>() {
        @Override
        public void updated(Self model) {
            if (model.isUpToDate() && !TextUtils.isEmpty(model.getName()) && !model.hasSetUsername()) {
                if (hasGeneratedUsername() && model.getName().equals(generatedUsername.searchedName)) {
                    notifyObserversValidUsernameGenerated(model.getName(), getGeneratedUsername());
                } else {
                    startUsernameGenerator(model.getName());
                }
            }
        }
    };

    public UsernamesController() {
        randomGenerator = new Random();
    }

    @Override
    public void setActivity(Activity activity) {
        if (application != null) {
            return;
        }
        application = ZApplication.from(activity);
    }

    @Override
    public void addUsernamesObserver(UsernamesControllerObserver usernamesControllerObserver) {
        usernamesControllerObservers.add(usernamesControllerObserver);
        if (hasGeneratedUsername()) {
            if (generatedUsername.isValid()) {
                notifyObserversValidUsernameGenerated(generatedUsername.searchedName, generatedUsername.username);
            } else {
                notifyObserversAttemptsExhausted(generatedUsername.searchedName);
            }
        } else {
            userModelObserver.forceUpdate();
        }
    }

    @Override
    public void removeUsernamesObserver(UsernamesControllerObserver usernamesControllerObserver) {
        usernamesControllerObservers.remove(usernamesControllerObserver);
    }

    private void notifyObserversValidUsernameGenerated(String name, String generatedUsername) {
        for (UsernamesControllerObserver observer : usernamesControllerObservers) {
            observer.onValidUsernameGenerated(name, generatedUsername);
        }
    }

    private void notifyObserversAttemptsExhausted(String name) {
        for (UsernamesControllerObserver observer : usernamesControllerObservers) {
            observer.onUsernameAttemptsExhausted(name);
        }
    }

    @Override
    public boolean hasGeneratedUsername() {
        return generatedUsername != null;
    }

    @Override
    public String getGeneratedUsername() {
        return hasGeneratedUsername() ? generatedUsername.username : null;
    }

    @Override
    public void startUsernameGenerator(String baseName) {
        if (currentSearch != null && currentSearch.equals(baseName)) {
            return;
        }
        generatedUsername = null;
        currentSearch = baseName;
        Usernames usernames = application.getStoreFactory().getZMessagingApiStore().getApi().getUsernames();
        String baseGeneratedUsername = usernames.generateUsernameFromName(baseName, application);

        attemptUsername(baseName, baseGeneratedUsername);
    }

    @Override
    public void setUser(Self self) {
        userModelObserver.setAndUpdate(self);
    }

    private void attemptUsername(final String baseName, final String baseUsername) {
        Usernames usernames = application.getStoreFactory().getZMessagingApiStore().getApi().getUsernames();
        List<String> attempts = new ArrayList<>();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            attempts.add(baseUsername + getTrailingNumber(i));
        }
        String[] attemptsArray = new String[attempts.size()];
        attempts.toArray(attemptsArray);
        usernames.areUsernamesAvailable(attemptsArray, new UsernamesRequestCallback() {

            @Override
            public void onUsernameRequestResult(UsernameValidation[] usernameValidation) {
                for (UsernameValidation attemptValidation : usernameValidation) {
                    if (attemptValidation.isValid()) {
                        generatedUsername = new GeneratedUsername(attemptValidation.username(), baseName);
                        currentSearch = "";
                        notifyObserversValidUsernameGenerated(baseName, attemptValidation.username());
                        return;
                    }
                }
                notifyObserversAttemptsExhausted(baseName);
                generatedUsername = new GeneratedUsername("", baseName);
                currentSearch = "";
            }

            @Override
            public void onRequestFailed(Integer errorCode) {
                Timber.d("areUsernamesAvailable request failed with error " + errorCode);
                currentSearch = "";
            }
        });
    }

    private String getTrailingNumber(int attempt) {
        if (attempt > 0) {
            return String.format(Locale.getDefault(), "%04d", randomGenerator.nextInt(MAX_RANDOM_TRAILLING_NUMBER));
        }
        return "";
    }

    @Override
    public void logout() {
        tearDown();
    }

    @Override
    public void tearDown() {
        userModelObserver.clear();
        usernamesControllerObservers.clear();
        randomGenerator = null;
        currentSearch = null;
    }
}
