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
package com.waz.zclient.core.api.scala;

import android.support.annotation.NonNull;
import com.waz.api.UiObservable;
import com.waz.api.UpdateListener;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.waz.zclient.core.api.scala.ModelObserver.Reason.FORCED_UPDATE;
import static com.waz.zclient.core.api.scala.ModelObserver.Reason.INTERNAL_CHANGE;
import static com.waz.zclient.core.api.scala.ModelObserver.Reason.NEW_MODEL;

public abstract class ModelObserver<T extends UiObservable> {

    public enum Reason {
        NEW_MODEL,
        INTERNAL_CHANGE,
        FORCED_UPDATE
    }

    /**
     * A set of observers for each model that we want to listen to in the collection of models passed to us. We only
     * want one observer for any given model object.
     *
     * protected for testing purposes
     */
    protected Set<SingleModelObserver> observers;

    public ModelObserver() {
        observers = new HashSet<>();
    }

    /**
     * @see #setAndUpdate(Collection)
     * @param model
     */
    public void setAndUpdate(T model) { //avoids possibly heap pollution.
        setAndUpdate(Collections.singletonList(model));
    }

    /**
     * @see #setAndUpdate(Collection)
     * @param models
     */
    public void setAndUpdate(T[] models) {
        setAndUpdate(Arrays.asList(models));
    }

    /**
     * <p>
     * For a given list of {@link UiObservable} 'models', register an observer to each one and call update on that observer.
     * When any of the observers is updated, then it will call through to {@link #updated(UiObservable)} with the model
     * that was updated, in effect funnelling all of the update listeners into one method call.
     * </p>
     *
     * <p>
     * Note, this method clears any previous {@link SingleModelObserver}s that this {@link ModelObserver} was using,
     * before creating new observers for any models passed in. If we were previously watching models A and B, and then
     * call #setAndUpdate on models B and C, this {@link ModelObserver} will then be listening to B and C, and NOT A.
     * Furthermore, a call to update on B will NOT be performed, as we were already listening to it.
     * </p>
     * <p>
     * If a particular model in the collection is null, then nothing will happen.
     * </p>
     * @param models
     */
    public void setAndUpdate(Collection<T> models) {
        pauseListening();
        Set<SingleModelObserver> newModels = createObserverCollection(models);
        if (!observers.isEmpty() && !observers.retainAll(newModels)) {
            resumeListening();
            return;
        }
        resumeListening();
        newModels.removeAll(observers);
        for (SingleModelObserver observer : newModels) {
            observer.startListening();
        }
        observers.addAll(newModels);
    }

    /**
     * @see #setAndPause(Collection)
     * @param model
     */
    public void setAndPause(T model) { //avoids possibly heap pollution.
        setAndPause(Collections.singletonList(model));
    }

    public void setAndPause(Collection<T> models) {
        pauseListening();
        Set<SingleModelObserver> newModels = createObserverCollection(models);
        if (!observers.isEmpty() && !observers.retainAll(newModels)) {
            resumeListening();
            return;
        }
        resumeListening();
        observers.addAll(newModels);
    }

    /**
     * Create a set of SingleModelObservers for a collection of Models, but don't yet update them
     */
    private Set<SingleModelObserver> createObserverCollection(Collection<T> models) {
        Set<SingleModelObserver> set = new HashSet<>();
        for (T model : models) {
            if (model != null) {
                set.add(new SingleModelObserver(model));
            }
        }
        return set;
    }

    /**
     * @see #addAndUpdate(Collection)
     * @param model
     */
    public void addAndUpdate(T model) {
        addAndUpdate(Collections.singletonList(model));
    }

    /**
     * @see #addAndUpdate(Collection)
     * @param models
     */
    public void addAndUpdate(T[] models) {
        addAndUpdate(Arrays.asList(models));
    }

    /**
     * <p>
     * Add an extra model to the set of models that this {@link ModelObserver} watches and call update only to those models
     * added. If a particular model was already being watched by this {@link ModelObserver}, then the particular observer
     * for that model will be replaced with a new one, and update will be called on it.
     * </p>
     * <p>
     * Note, this method preserves the observers of any models added previously that are NOT being replaced. If models A
     * and B were being listened to already, and we call #addAndUpdate on a model C, then this {@link ModelObserver}
     * will be observer A, B and C. If the model was already being listened to by this particular {@link ModelObserver},
     * then the observer for that model will be replaced by a new one and update will be called again.
     * </p>
     * <p>
     * If a particular model in the collection is null, then nothing will happen.
     * </p>
     * @param models
     */
    public void addAndUpdate(Collection<T> models) {
        for (T model : models) {
            addModelAndUpdate(model);
        }
    }

    private void addModelAndUpdate(T model) {
        if (model == null) {
            return;
        }
        SingleModelObserver observer = new SingleModelObserver(model);
        if (observers.add(observer)) {
            observer.startListening();
        } //else the model was already being observed, do nothing
    }

    /**
     * For any calls to {@link #pauseListening()} that were made, resume listening again.
     */
    public void resumeListening() {
        for (SingleModelObserver observer : observers) {
            observer.resumeListening();
        }
    }

    public String debugCurentModels() {
        StringBuilder sb = new StringBuilder();
        for (SingleModelObserver observer : observers) {
            sb.append(String.format("listening: %s, to %s", observer.listening, observer.model));
        }
        return sb.toString();
    }

    /**
     * <p>
     * If there any models that this {@link ModelObserver} was observing, then pause listening to it. This method does
     * not destroy the observers that were watching the models. This is useful if we need to pause listening briefly to
     * prevent a UI element from being updated when we don't want it to be.
     * </p>
     * <p>
     * Note, this method doesn't do any clean up. It does not set the references to the observers (which in turn hold
     * references to models) to null. As such, calling this method in the clean-up methods of other components has little
     * purpose aside from assuring that no calls to update will occur during that clean up process.
     * </p>
     */
    public void pauseListening() {
        for (SingleModelObserver observer : observers) {
            observer.pauseListening();
        }
    }

    /**
     * Remove any observers and clear the references to them in case we want to re-use the {@link ModelObserver}. For
     * example, if we are listening to models A and B, we have a new set of models B and C and we want to stop listening
     * to A, we must first call clear before registering to B and C.
     */
    public void clear() {
        pauseListening();
        observers.clear();
    }

    /**
     * Cause all observed models to have their observers updated.
     * This will result in a call to {@link #updated(UiObservable)} for every model this ModelObserver is watching.
     */
    public void forceUpdate() {
        for (SingleModelObserver observer : observers) {
            observer.updated(FORCED_UPDATE);
        }
    }

    /**
     * Use this update method if you don't care why the model updated
     * @param model the model that has been updated for whatever reason
     */
    public void updated(T model) {
    }

    /**
     * Use this update method if you want to perform different logic for different update reasons. This should prevent
     * you having to keep reference to the model in your own classes as much as possible.
     * @param model the model that has been updated
     * @param reason the reason the model was updated
     */
    public void updated(T model, Reason reason) {
    }

    /**
     * Protected for testing purposes
     */
    protected final class SingleModelObserver implements UpdateListener {
        private final T model;

        protected boolean listening = false;

        SingleModelObserver(@NonNull T model) {
            this.model = model;
        }

        public void startListening() {
            resumeListening();
            updated(NEW_MODEL);
        }

        public void resumeListening() {
            model.addUpdateListener(this);
            listening = true;
        }

        public void pauseListening() {
            model.removeUpdateListener(this);
            listening = false;
        }

        @Override
        public void updated() {
            updated(INTERNAL_CHANGE);
        }

        public void updated(Reason reason) {
            ModelObserver.this.updated(model);
            ModelObserver.this.updated(model, reason);
        }

        /**
         * Protected for testing purposes
         *
         * @return
         */
        protected T getModel() {
            return model;
        }

        /**
         * Returns the model's equals result so that we can maintain a one-to-one mapping of {@link SingleModelObserver}s
         * to {@link UiObservable} objects.
         * @param o
         * @return
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SingleModelObserver that = (SingleModelObserver) o;

            return model.equals(that.model);

        }

        /**
         * @see #equals(Object)
         * @return
         */
        @Override
        public int hashCode() {
            return model.hashCode();
        }
    }
}
