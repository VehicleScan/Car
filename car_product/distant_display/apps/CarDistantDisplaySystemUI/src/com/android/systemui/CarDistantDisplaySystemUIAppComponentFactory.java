/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.Context;

/**
 * Starts up SystemUI using {@link CarSystemUIInitializer}.
 *
 * The {@link SystemUIAppComponentFactoryBase} is required for proper SystemUI functionality.
 *
 * @see SystemUIAppComponentFactoryBase
 */
public class CarDistantDisplaySystemUIAppComponentFactory extends
        SystemUIAppComponentFactoryBase {
    @Override
    protected SystemUIInitializer createSystemUIInitializer(Context context) {
        return new CarDistantDisplaySystemUIInitializer(context);
    }
}
