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
import com.waz.api.ValidatedUsernames;
import com.waz.zclient.ZApplication;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.utils.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

public class UsernamesController implements IUsernamesController {

    private static final int USERNAME_MAX_LENGTH = 21;
    private static final int NORMAL_ATTEMPTS = 30;
    private static final int RANDOM_ATTEMPTS = 20;
    private static final int MAX_RANDOM_TRAILLING_NUMBER = 1000;

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
    private String[] currentAttemptsArray;

    private ModelObserver<Self> userModelObserver = new ModelObserver<Self>() {
        @Override
        public void updated(Self model) {
            if (model.isUpToDate() && !TextUtils.isEmpty(model.getName()) && !model.hasSetUsername()) {
                if (hasGeneratedUsername() && model.getName().equals(generatedUsername.searchedName)) {
                    notifyObserversValidUsernameGenerated(model.getName(), getGeneratedUsername());
                } else {
                    startUsernameGenerator(model.getName());
                }
            } else if (model.hasSetUsername()) {
                currentAttemptsArray = null;
                generatedUsername = null;
                closeFirstAssignUsernameScreen();
            }
        }
    };

    private ModelObserver<ValidatedUsernames> validatedUsernamesModelObserver = new ModelObserver<ValidatedUsernames>() {
        @Override
        public void updated(ValidatedUsernames model) {
            if (currentAttemptsArray == null) {
                return;
            }
            UsernameValidation[] usernameValidations = model.getValidations(currentAttemptsArray);
            for (UsernameValidation attemptValidation : usernameValidations) {
                if (attemptValidation.isValid()) {
                    generatedUsername = new GeneratedUsername(attemptValidation.username(), currentSearch);
                    notifyObserversValidUsernameGenerated(currentSearch, attemptValidation.username());
                    currentSearch = "";
                    currentAttemptsArray = null;
                    return;
                }
            }
            notifyObserversAttemptsExhausted(currentSearch);
            generatedUsername = new GeneratedUsername("", currentSearch);
            currentSearch = "";
            currentAttemptsArray = null;
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
        validatedUsernamesModelObserver.setAndUpdate(application.getStoreFactory().zMessagingApiStore().getApi().getUsernames().getValidatedUsernames());
    }

    @Override
    public void addUsernamesObserver(UsernamesControllerObserver usernamesControllerObserver) {
        usernamesControllerObservers.add(usernamesControllerObserver);
    }

    @Override
    public void addUsernamesObserverAndUpdate(UsernamesControllerObserver usernamesControllerObserver) {
        addUsernamesObserver(usernamesControllerObserver);
        if (hasGeneratedUsername()) {
            if (generatedUsername.isValid()) {
                usernamesControllerObserver.onValidUsernameGenerated(generatedUsername.searchedName, generatedUsername.username);
            } else {
                usernamesControllerObserver.onUsernameAttemptsExhausted(generatedUsername.searchedName);
            }
        } else {
            userModelObserver.forceUpdate();
        }
    }

    @Override
    public void removeUsernamesObserver(UsernamesControllerObserver usernamesControllerObserver) {
        usernamesControllerObservers.remove(usernamesControllerObserver);
    }

    @Override
    public void closeFirstAssignUsernameScreen() {
        for (UsernamesControllerObserver observer : usernamesControllerObservers) {
            observer.onCloseFirstAssignUsernameScreen();
        }
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
        return generatedUsername != null && generatedUsername.isValid();
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
        currentAttemptsArray = null;
        Usernames usernames = application.getStoreFactory().zMessagingApiStore().getApi().getUsernames();
        String baseGeneratedUsername = usernames.generateUsernameFromName(baseName, application);
        String randomUsername = usernames.generateUsernameFromName("", application);

        List<String> attempts = new ArrayList<>();
        for (int i = 0; i < NORMAL_ATTEMPTS; i++) {
            String trailingNumber = getTrailingNumber(i);
            attempts.add(StringUtils.truncate(baseGeneratedUsername, USERNAME_MAX_LENGTH - trailingNumber.length()) + trailingNumber);
        }
        for (int i = 0; i < RANDOM_ATTEMPTS; i++) {
            String trailingNumber = getTrailingNumber(i);
            attempts.add(StringUtils.truncate(randomUsername, USERNAME_MAX_LENGTH - trailingNumber.length()) + trailingNumber);
        }
        currentAttemptsArray = new String[attempts.size()];
        attempts.toArray(currentAttemptsArray);
        usernames.validateUsernames(currentAttemptsArray);
    }

    @Override
    public void setUser(Self self) {
        userModelObserver.setAndUpdate(self);
    }

    private String getTrailingNumber(int attempt) {
        if (attempt > 0) {
            return String.format(Locale.getDefault(), "%d", randomGenerator.nextInt(MAX_RANDOM_TRAILLING_NUMBER * 10 ^ (attempt / 10)));
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
        currentSearch = null;
        currentAttemptsArray = null;
    }
}
