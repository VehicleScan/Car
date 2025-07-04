/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.internal.os;

import android.annotation.NonNull;
import android.car.builtin.os.ServiceManagerHelper;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * A real implementation for ServiceManager.
 */
public final class SystemServiceManager implements ServiceManager {
    @Override
    public IBinder getService(String name) {
        return ServiceManagerHelper.getService(name);
    }

    @Override
    public void registerForNotifications(@NonNull String name,
            @NonNull ServiceManagerHelper.IServiceRegistrationCallback callback)
            throws RemoteException {
        ServiceManagerHelper.registerForNotifications(name, callback);
    }
}
