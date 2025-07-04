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

package com.android.systemui.wmshell;

import com.android.systemui.wm.CarUiPortraitDisplaySystemBarsController;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;
import com.android.wm.shell.transition.Transitions;

import dagger.Subcomponent;

/**
 * Dagger Subcomponent for WindowManager.
 */
@WMSingleton
@Subcomponent(modules = {CarUiPortraitWMShellModule.class})
public interface CarUiPortraitWMComponent extends CarWMComponent {
    /**
     * Builder for a SysUIComponent.
     */
    @Subcomponent.Builder
    interface Builder extends CarWMComponent.Builder {
        CarUiPortraitWMComponent build();
    }

    /**
     * get root TDA
     */
    @WMSingleton
    RootTaskDisplayAreaOrganizer getRootTaskDisplayAreaOrganizer();

    /**
     * get CarUiPortraitDisplaySystemBarsController
     */
    @WMSingleton
    CarUiPortraitDisplaySystemBarsController getCarUiPortraitDisplaySystemBarsController();

    /**
     * Get FullscreenTaskListener.
     */
    @WMSingleton
    FullscreenTaskListener getFullscreenTaskListener();

    /**
     * Get Transitions.
     */
    @WMSingleton
    Transitions getTransitions();
}
