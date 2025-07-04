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

package com.android.car.internal.dep;

import android.annotation.NonNull;

/**
 * A wrapper for {@link android.os.Trace}.
 *
 * @hide
 */
public final class Trace {

    private Trace() {
        throw new UnsupportedOperationException("Trace must be used statically");
    }

    /**
     * Begins a trace section.
     */
    public static void beginSection(@NonNull String sectionName) {
        android.os.Trace.beginSection(sectionName);
    }

    /**
     * Ends a trace section.
     */
    public static void endSection() {
        android.os.Trace.endSection();
    }

    /**
     * Begins async trace.
     */
    public static void asyncTraceBegin(long traceTag, String methodName, int cookie) {
        android.os.Trace.asyncTraceBegin(traceTag, methodName, cookie);
    }

    /**
     * Ends async trace.
     */
    public static void asyncTraceEnd(long traceTag, String methodName, int cookie) {
        android.os.Trace.asyncTraceEnd(traceTag, methodName, cookie);
    }

    /**
     * Begins trace.
     */
    public static void traceBegin(long traceTag, String methodName) {
        android.os.Trace.traceBegin(traceTag, methodName);
    }

    /**
     * Ends trace.
     */
    public static void traceEnd(long traceTag) {
        android.os.Trace.traceEnd(traceTag);
    }
}
