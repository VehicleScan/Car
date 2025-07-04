/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui;

import com.android.systemui.dagger.DependencyProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.SystemUIModule;
import com.android.systemui.scene.ShadelessSceneContainerFrameworkModule;
import com.android.systemui.wm.CarUiPortraitDisplaySystemBarsController;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;
import com.android.wm.shell.transition.Transitions;

import dagger.BindsInstance;
import dagger.Subcomponent;

import java.util.Optional;

/**
 * Dagger Subcomponent for Core SysUI.
 */
@SysUISingleton
@Subcomponent(modules = {
        CarComponentBinder.class,
        DependencyProvider.class,
        SystemUIModule.class,
        CarSystemUIModule.class,
        CarSystemUICoreStartableModule.class,
        CarUiPortraitSystemUIBinder.class,
        ShadelessSceneContainerFrameworkModule.class})
public interface CarUiPortraitSysUIComponent extends CarSysUIComponent {
    /**
     * Builder for a CarSysUIComponent.
     */
    @Subcomponent.Builder
    interface Builder extends CarSysUIComponent.Builder {
        @BindsInstance
        CarUiPortraitSysUIComponent.Builder setRootTaskDisplayAreaOrganizer(
                Optional<RootTaskDisplayAreaOrganizer> r);

        @BindsInstance
        CarUiPortraitSysUIComponent.Builder setCarUiPortraitDisplaySystemBarsController(
                CarUiPortraitDisplaySystemBarsController c);

        /**
         * Set FullscreenTaskListener.
         */
        @BindsInstance
        CarUiPortraitSysUIComponent.Builder setFullscreenTaskListener(
                FullscreenTaskListener s);

        /**
         * Set Transitions.
         */
        @BindsInstance
        CarUiPortraitSysUIComponent.Builder setTransitions(
                Transitions t);

        CarUiPortraitSysUIComponent build();
    }
}
