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

package com.android.car.internal;

import android.view.accessibility.AccessibilityEvent;

/**
 * Base class for CarSafetyAccessibilityServiceImpl. This is used as an interface between builtin
 * and updatable car service. Do not change it without compatibility check.
 */
public abstract class CarSafetyAccessibilityServiceImplBase {
    /**
     * Check {@link android.accessibilityservice.AccessibilityService#onAccessibilityEvent(
     * android.view.accessibility.AccessibilityEvent)}
     */
    public void onAccessibilityEvent(AccessibilityEvent event) {};
}
