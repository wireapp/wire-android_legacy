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

import com.waz.zclient.testutils.MockObservable;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

import static com.waz.zclient.core.api.scala.ModelObserver.Reason.FORCED_UPDATE;
import static com.waz.zclient.core.api.scala.ModelObserver.Reason.INTERNAL_CHANGE;
import static com.waz.zclient.core.api.scala.ModelObserver.Reason.NEW_MODEL;
import static com.waz.zclient.core.api.scala.ModelObserverTest.FailedMessages.shouldHaveBeenCalled;
import static com.waz.zclient.core.api.scala.ModelObserverTest.FailedMessages.updateReasonShouldHaveBeen;
import static junit.framework.Assert.assertEquals;

@SuppressWarnings("PMD")
public class ModelObserverTest {

    TestObserver observer;

    @Before
    public void setup() {
        observer = new TestObserver();
    }

    @Test
    public void nullModelsShouldNotTriggerAnUpdate() {
        MockObservable model = null;
        observer.setAndUpdate(model);
        observer.addAndUpdate(model);

        assertEquals("The observer's update method should never have called", 0, observer.callsToUpdate);
    }

    @Test
    public void setAndUpdateRemovesOldModels() {
        MockObservable modelOld = new MockObservable(0);
        observer.setAndUpdate(modelOld);

        MockObservable modelNew = new MockObservable(1);
        observer.setAndUpdate(modelNew);

        assertEquals("The observer should only have one model", 1, observer.getModels().size());
        assertEquals("The observer should be listening to modelNew", modelNew, observer.getModels().first());
    }

    @Test
    public void addAndUpdateAddsNewModels() {
        MockObservable model1 = new MockObservable(1);
        MockObservable model2 = new MockObservable(2);

        observer.addAndUpdate(model1);
        observer.addAndUpdate(model2);

        assertEquals("The observer should be listening to both models", 2, observer.getModels().size());
        assertEquals(observer.getModels().first(), model1);
        assertEquals(observer.getModels().last(), model2);
    }

    @Test
    public void addAndUpdateOnlyAddsNonDuplicateModelsAndDoesntTriggerUpdate() {
        MockObservable model1 = new MockObservable(1);
        MockObservable modelDup = new MockObservable(1);

        observer.addAndUpdate(model1);
        observer.addAndUpdate(modelDup);

        assertEquals("The observer should only be listening to one model", 1, observer.getModels().size());
        assertEquals("The observer's update method should only have been called once", 1, observer.callsToUpdate);
    }

    @Test
    public void setAndUpdateSameModelShouldNotTriggerNewUpdate() {
        MockObservable model1 = new MockObservable(1);
        MockObservable modelDup = new MockObservable(1);

        observer.setAndUpdate(model1);
        observer.setAndUpdate(modelDup);

        assertEquals("The observer should only be listening to one model", 1, observer.getModels().size());
        assertEquals("The observer's update method should only have been called once", 1, observer.callsToUpdate);
    }

    @Test
    public void setAndUpdateNewModelsShouldBeUpdated() {
        MockObservable model1 = new MockObservable(1);
        MockObservable model2 = new MockObservable(2);
        MockObservable model3 = new MockObservable(3);

        observer.setAndUpdate(Arrays.asList(model1, model2));

        assertEquals(shouldHaveBeenCalled("twice"), 2, observer.callsToUpdate);


        observer.setAndUpdate(Arrays.asList(model1, model3));

        assertEquals("The observer should be listening to 2 models", 2, observer.getModels().size());
        assertEquals("The observer's update method should only have been called once more for the new model", 3, observer.callsToUpdate);
    }

    @Test
    public void forceUpdatingPassesUpdateReason() {
        MockObservable model = new MockObservable(1);

        observer.setAndUpdate(model);
        observer.forceUpdate();

        assertEquals(shouldHaveBeenCalled("twice"), 2, observer.callsToUpdate);
        assertEquals(updateReasonShouldHaveBeen(FORCED_UPDATE), FORCED_UPDATE, observer.lastUpdateReasons.pop());
    }

    @Test
    public void internalModelUpdatePassesUpdateReason() {
        MockObservable model = new MockObservable(1);

        observer.setAndUpdate(model);

        model.triggerInternalUpdate();

        assertEquals(shouldHaveBeenCalled("twice"), 2, observer.callsToUpdate);
        assertEquals(updateReasonShouldHaveBeen(INTERNAL_CHANGE), INTERNAL_CHANGE, observer.lastUpdateReasons.pop());
    }

    @Test
    public void assigningNewModelPassesUpdateReason() {
        MockObservable model1 = new MockObservable(1);
        MockObservable model2 = new MockObservable(2);

        observer.setAndUpdate(model1);
        observer.setAndUpdate(model2);

        assertEquals(shouldHaveBeenCalled("twice"), 2, observer.callsToUpdate);
        assertEquals(updateReasonShouldHaveBeen(NEW_MODEL), NEW_MODEL, observer.lastUpdateReasons.pop());
    }

    @Test
    public void mixtureOfUpdateReasonsWithSets() {
        MockObservable model1 = new MockObservable(1);
        MockObservable model2 = new MockObservable(2);
        MockObservable model3 = new MockObservable(3);

        observer.setAndUpdate(Arrays.asList(model1, model2));
        assertEquals(shouldHaveBeenCalled("twice"), 2, observer.callsToUpdate);

        assertEquals(updateReasonShouldHaveBeen(NEW_MODEL), NEW_MODEL, observer.lastUpdateReasons.pop());
        assertEquals(updateReasonShouldHaveBeen(NEW_MODEL), NEW_MODEL, observer.lastUpdateReasons.pop());

        model1.triggerInternalUpdate();
        assertEquals(shouldHaveBeenCalled("three times"), 3, observer.callsToUpdate);
        assertEquals(updateReasonShouldHaveBeen(INTERNAL_CHANGE), INTERNAL_CHANGE, observer.lastUpdateReasons.pop());

        observer.setAndUpdate(Arrays.asList(model1, model3));
        assertEquals(shouldHaveBeenCalled("four times"), 4, observer.callsToUpdate);
        assertEquals(updateReasonShouldHaveBeen(NEW_MODEL), NEW_MODEL, observer.lastUpdateReasons.pop());
    }

    private class TestObserver extends ModelObserver<MockObservable> {

        public int callsToUpdate = 0;
        public Stack<Reason> lastUpdateReasons = new Stack<>();

        @Override
        public void updated(MockObservable model, Reason reason) {
            callsToUpdate++;
            lastUpdateReasons.add(reason);
        }

        public SortedSet<MockObservable> getModels() {
            SortedSet<MockObservable> set = new TreeSet<>();
            for (SingleModelObserver observer : observers) {
                set.add(observer.getModel());
            }
            return set;
        }
    }
    /**
     * Commonly used failed test messages
     */
    public static final class FailedMessages {
        public static String shouldHaveBeenCalled(String times) {
            return String.format("The observers update method should now have been called %s", times);
        }

        public static String updateReasonShouldHaveBeen(ModelObserver.Reason reason) {
            return String.format("The update reason should be %s", reason.name());
        }
    }

}

