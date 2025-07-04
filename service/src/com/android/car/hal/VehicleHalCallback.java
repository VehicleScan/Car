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

package com.android.car.hal;

import android.hardware.automotive.vehicle.VehiclePropError;

import com.android.car.internal.property.PropIdAreaId;

import java.util.ArrayList;
import java.util.List;

/**
 * VehicleHalCallback is the callback functions that VehicleHal supports.
 */
public interface VehicleHalCallback {
    /**
     * Called when new property events happen.
     */
    void onPropertyEvent(ArrayList<HalPropValue> values);

    /**
     * Called when property set errors happen.
     */
    void onPropertySetError(ArrayList<VehiclePropError> errors);

    /**
     * Method called when supported values change.
     */
    void onSupportedValuesChange(List<PropIdAreaId> propIdAreaIds);
}
