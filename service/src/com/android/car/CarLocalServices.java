/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car;

import static android.car.feature.Flags.asyncAudioServiceInit;

import android.annotation.Nullable;
import android.car.Car;
import android.car.builtin.util.Slogf;
import android.car.hardware.power.CarPowerManager;
import android.content.Context;
import android.util.ArrayMap;
import android.util.Log;

import com.android.car.audio.CarAudioService;
import com.android.car.power.CarPowerManagementService;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Copy of frameworks/base/core/java/com/android/server/LocalServices.java
 * This is for accessing other car service components.
 */
public final class CarLocalServices {

    private static final String TAG = CarLog.tagFor(CarLocalServices.class);

    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private CarLocalServices() {}

    private static final ArrayMap<Class<?>, Object> sLocalServiceObjects =
            new ArrayMap<Class<?>, Object>();

    /**
     * Returns a local service instance that implements the specified interface.
     *
     * @param type The type of service.
     * @return The service object.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T getService(Class<T> type) {
        if (DBG) {
            Slogf.d(TAG, " getService " + type.getSimpleName());
        }
        if (asyncAudioServiceInit()) {
            if (type.equals(CarAudioService.class)) {
                throw new UnsupportedOperationException("Use getCarAudioService instead.");
            }
        }
        synchronized (sLocalServiceObjects) {
            return (T) sLocalServiceObjects.get(type);
        }
    }

     /**
     * Returns a {@link CarAudioService}.
     *
     * <p>Note that the returned service is not fully initialized. Caller should call
     * {@link CarAudioService#waitForInitComplete} to make sure the initialization is complete.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static CarAudioService getCarAudioService() {
        if (DBG) {
            Slogf.d(TAG, " getService CarAudioService");
        }
        synchronized (sLocalServiceObjects) {
            return (CarAudioService) sLocalServiceObjects.get(CarAudioService.class);
        }
    }

    /**
     * Adds a service instance of the specified interface to the global registry of local services.
     */
    public static <T> void addService(Class<T> type, T service) {
        synchronized (sLocalServiceObjects) {
            if (sLocalServiceObjects.containsKey(type)) {
                throw new IllegalStateException("Overriding service registration");
            }
            if (DBG) {
                Slogf.d(TAG, " Adding " + type.getSimpleName());
            }
            sLocalServiceObjects.put(type, service);
        }
    }

    /**
     * Remove a service instance, must be only used in tests.
     */
    @VisibleForTesting
    public static <T> void removeServiceForTest(Class<T> type) {
        if (DBG) {
            Slogf.d(TAG, " Removing " + type.getSimpleName());
        }
        synchronized (sLocalServiceObjects) {
            sLocalServiceObjects.remove(type);
        }
    }

    /**
     * Remove all registered services. Should be called when car service restarts.
     */
    public static void removeAllServices() {
        if (DBG) {
            Slogf.d(TAG, " removeAllServices");
        }
        synchronized (sLocalServiceObjects) {
            sLocalServiceObjects.clear();
        }
    }

    /**
     * Create CarPowerManager from registered CarPowerManagementService.
     * @param context
     * @return Newly created CarPowerManager. It will return null if CarPowerManagementService is
     * not registered, which can only happen in test setup.
     */
    @Nullable
    public static CarPowerManager createCarPowerManager(Context context) {
        // This does not require connection as binder will be passed to CarPowerManager directly.
        Car car = new Car(context, /* service= */null, /* handler= */ null);
        CarPowerManagementService service = getService(CarPowerManagementService.class);
        if (service == null) {
            return null;
        }
        return new CarPowerManager(car, service);
    }
}
