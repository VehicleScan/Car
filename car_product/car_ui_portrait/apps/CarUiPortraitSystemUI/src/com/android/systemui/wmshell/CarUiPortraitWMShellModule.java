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

import android.content.Context;
import android.os.Handler;
import android.view.IWindowManager;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.wm.CarFullscreenTaskMonitorListener;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.wm.CarUiPortraitDisplaySystemBarsController;
import com.android.systemui.wm.DisplaySystemBarsController;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.dagger.DynamicOverride;
import com.android.wm.shell.dagger.WMShellBaseModule;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;
import com.android.wm.shell.pip.Pip;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.Provides;

import java.util.Optional;

/** Provides dependencies from {@link com.android.wm.shell} for CarSystemUI. */
@Module(includes = WMShellBaseModule.class)
public abstract class CarUiPortraitWMShellModule {

    @WMSingleton
    @Provides
    static DisplaySystemBarsController bindDisplaySystemBarsController(
            CarUiPortraitDisplaySystemBarsController controller) {
        return controller;
    }

    @WMSingleton
    @Provides
    static CarUiPortraitDisplaySystemBarsController bindCarUiPortraitDisplaySystemBarsController(
            Context context, IWindowManager wmService, DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            @Main Handler mainHandler) {
        return new CarUiPortraitDisplaySystemBarsController(context, wmService, displayController,
                displayInsetsController, mainHandler);
    }

    @BindsOptionalOf
    abstract Pip optionalPip();

    @WMSingleton
    @Provides
    @DynamicOverride
    static FullscreenTaskListener provideFullScreenTaskListener(Context context,
            CarServiceProvider carServiceProvider,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            Optional<RecentTasksController> recentTasksOptional,
            Optional<WindowDecorViewModel> windowDecorViewModelOptional,
            TaskViewTransitions taskViewTransitions) {
        return new CarFullscreenTaskMonitorListener(context,
                carServiceProvider,
                shellInit,
                shellTaskOrganizer,
                syncQueue,
                recentTasksOptional,
                windowDecorViewModelOptional,
                taskViewTransitions);
    }
}
