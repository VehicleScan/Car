/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.audio;

import static com.android.car.audio.FocusInteraction.AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL_URI;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.car.settings.CarSettings;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.audio.ContentObserverFactory.ContentChangeCallback;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public final class ContentObserverFactoryTest {

    private static final Uri TEST_URI = Settings.Secure.getUriFor(
            CarSettings.Secure.KEY_AUDIO_PERSIST_VOLUME_GROUP_MUTE_STATES);

    private ContentObserverFactory mFactory =
            new ContentObserverFactory(AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL_URI);

    @Test
    public void constructor_withNullUri_fails() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class,
                        () -> new ContentObserverFactory(null));

        assertWithMessage("Constructor with Null Uri Exception")
                .that(thrown).hasMessageThat().contains("Uri");
    }

    @Test
    public void createObserver_withNullCallback_fails() {
        ContentObserverFactory factory =
                new ContentObserverFactory(AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL_URI);
        NullPointerException thrown =
                assertThrows(NullPointerException.class,
                        () -> factory.createObserver(null));

        assertWithMessage("Create Observer with Null Callback Exception")
                .that(thrown).hasMessageThat().contains("Content Change Callback");
    }

    @Test
    public void createObserver_withCallback_createsContentObserver() {
        ContentChangeCallback callback = Mockito.mock(ContentChangeCallback.class);
        ContentObserver observer = mFactory.createObserver(callback);

        assertWithMessage("Created Content Observer").that(observer).isNotNull();
    }

    @Test
    public void onChange_calledWithCreatedUri_callsCallback() {
        ContentChangeCallback callback = Mockito.mock(ContentChangeCallback.class);
        ContentObserver observer = mFactory.createObserver(callback);

        observer.onChange(true, AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL_URI);

        verify(callback).onChange();
    }

    @Test
    public void onChange_calledWithDifferentUri_doesNotCallCallback() {
        ContentChangeCallback callback = Mockito.mock(ContentChangeCallback.class);
        ContentObserver observer = mFactory.createObserver(callback);

        observer.onChange(true, TEST_URI);

        verify(callback, never()).onChange();
    }
}
