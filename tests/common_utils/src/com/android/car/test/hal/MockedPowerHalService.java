/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.car.test.hal;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DEBUGGING_CODE;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.feature.FeatureFlagsImpl;
import android.car.test.mocks.JavaMockitoHelper;
import android.content.Context;
import android.hardware.automotive.vehicle.VehicleApPowerStateReq;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Display;

import com.android.car.CarServiceUtils;
import com.android.car.VehicleStub;
import com.android.car.hal.ClusterHalService;
import com.android.car.hal.DiagnosticHalService;
import com.android.car.hal.HalPropValueBuilder;
import com.android.car.hal.InputHalService;
import com.android.car.hal.PowerHalService;
import com.android.car.hal.PropertyHalService;
import com.android.car.hal.TimeHalService;
import com.android.car.hal.UserHalService;
import com.android.car.hal.VehicleHal;
import com.android.car.hal.VmsHalService;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.systeminterface.DisplayHelperInterface;
import com.android.internal.annotations.GuardedBy;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

@ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
public class MockedPowerHalService extends PowerHalService {
    private static final String TAG = MockedPowerHalService.class.getSimpleName();

    private final boolean mIsPowerStateSupported;
    private final boolean mIsTimedWakeupAllowed;

    private final Object mLock = new Object();
    private final CountDownLatch mBrightnessSendLatch = new CountDownLatch(1);

    @GuardedBy("mLock")
    private PowerState mCurrentPowerState = new PowerState(VehicleApPowerStateReq.ON, 0);

    @GuardedBy("mLock")
    private PowerEventListener mListener;

    @GuardedBy("mLock")
    private SignalListener mSignalListener;

    @GuardedBy("mLock")
    private final LinkedList<int[]> mSentStates = new LinkedList<>();

    @GuardedBy("mLock")
    private final SparseIntArray mDisplayBrightnessSet = new SparseIntArray();

    private boolean mIsDeepSleepAllowed;
    private boolean mIsHibernationAllowed;
    @PowerState.ShutdownType
    private int mRequestedShutdownPowerState = PowerState.SHUTDOWN_TYPE_UNDEFINED;

    public interface SignalListener {
        /**
         * Sends signals
         * @param signal The signal to send
         */
        void sendingSignal(int signal);
    }

    private static VehicleHal createVehicleHalWithMockedServices() {
        HalPropValueBuilder propValueBuilder = new HalPropValueBuilder(/*isAidl=*/true);
        VehicleStub vehicleStub = mock(VehicleStub.class);
        when(vehicleStub.getHalPropValueBuilder()).thenReturn(propValueBuilder);
        VehicleHal mockedVehicleHal = new VehicleHal(
                mock(Context.class),
                mock(PowerHalService.class),
                mock(PropertyHalService.class),
                mock(InputHalService.class),
                mock(VmsHalService.class),
                mock(UserHalService.class),
                mock(DiagnosticHalService.class),
                mock(ClusterHalService.class),
                mock(TimeHalService.class),
                CarServiceUtils.getHandlerThread(VehicleHal.class.getSimpleName()),
                vehicleStub);

        return mockedVehicleHal;
    }

    public MockedPowerHalService(boolean isPowerStateSupported, boolean isDeepSleepAllowed,
            boolean isHibernationAllowed, boolean isTimedWakeupAllowed) {
        super(mock(Context.class), new FeatureFlagsImpl(),
                createVehicleHalWithMockedServices(), new DisplayHelperInterface.DefaultImpl());
        mIsPowerStateSupported = isPowerStateSupported;
        mIsDeepSleepAllowed = isDeepSleepAllowed;
        mIsHibernationAllowed = isHibernationAllowed;
        mIsTimedWakeupAllowed = isTimedWakeupAllowed;
    }

    @Override
    public void setListener(PowerEventListener listener) {
        synchronized (mLock) {
            mListener = listener;
        }
    }

    /**
     * Sets the signalListener
     * @param listener The listener to set
     */
    public void setSignalListener(SignalListener listener) {
        synchronized (mLock) {
            mSignalListener = listener;
        }
    }

    @Override
    public void sendOn() {
        Log.i(TAG, "sendOn");
        doSendState(SET_ON, 0);
    }

    @Override
    public void sendWaitForVhal() {
        Log.i(TAG, "sendWaitForVhal");
        doSendState(SET_WAIT_FOR_VHAL, 0);
    }

    @Override
    public void sendSleepEntry(int wakeupTimeSec) {
        Log.i(TAG, "sendSleepEntry");
        doSendState(SET_DEEP_SLEEP_ENTRY, wakeupTimeSec);
    }

    @Override
    public void sendSleepExit() {
        Log.i(TAG, "sendSleepExit");
        doSendState(SET_DEEP_SLEEP_EXIT, 0);
    }

    @Override
    public void sendShutdownPostpone(int postponeTimeMs) {
        Log.i(TAG, "sendShutdownPostpone");
        doSendState(SET_SHUTDOWN_POSTPONE, postponeTimeMs);
    }

    @Override
    public void sendShutdownStart(int wakeupTimeSec) {
        Log.i(TAG, "sendShutdownStart");
        doSendState(SET_SHUTDOWN_START, wakeupTimeSec);
    }

    @Override
    public void sendShutdownCancel() {
        Log.i(TAG, "sendShutdownCancel");
        doSendState(SET_SHUTDOWN_CANCELLED, 0);
    }

    @Override
    public void sendShutdownPrepare() {
        Log.i(TAG, "sendShutdownPrepare");
        super.sendShutdownPrepare();
    }

    @Override
    public void sendHibernationEntry(int wakeupTimeSec) {
        Log.i(TAG, "sendHibernationEntry");
        doSendState(SET_HIBERNATION_ENTRY, wakeupTimeSec);
    }

    @Override
    public void sendHibernationExit() {
        Log.i(TAG, "sendHibernationExit");
        doSendState(SET_HIBERNATION_EXIT, 0);
    }

    @Override
    public void sendDisplayBrightnessLegacy(int brightness) {
        sendDisplayBrightness(Display.DEFAULT_DISPLAY, brightness);
    }

    @Override
    public void sendDisplayBrightness(int displayId, int brightness) {
        synchronized (mLock) {
            mDisplayBrightnessSet.put(displayId, brightness);
        }
        mBrightnessSendLatch.countDown();
    }

    /**
     * Gets the display brightness
     *
     * @param displayId DisplayId to check brightness
     * @return brightness of the displayId
     */
    public int getDisplayBrightness(int displayId) {
        synchronized (mLock) {
            return mDisplayBrightnessSet.get(displayId, -1);
        }
    }

    /**
     * Waits for the brightness to be sent
     *
     * @param displayId The displayId of the brightness
     * @param brightness the brightness to set
     * @param timeoutMs Amount of ms to wait before timeout.
     * @throws Exception If the brightness is not sent by timeout.
     */
    public void waitForBrightnessSent(int displayId, int brightness, long timeoutMs)
            throws Exception {
        if (getDisplayBrightness(displayId) == brightness) {
            return;
        }
        JavaMockitoHelper.await(mBrightnessSendLatch, timeoutMs);
    }

    @Override
    public void requestShutdownAp(@PowerState.ShutdownType int powerState, boolean runGarageMode) {
        mRequestedShutdownPowerState = powerState;
    }

    public int getRequestedShutdownPowerState() {
        return mRequestedShutdownPowerState;
    }

    /**
     * Wait for sent events to be finished
     *
     * @param timeoutMs Timeout when events are not all sent by this time
     * @return All the events that were sent
     * @throws TimeoutException SentState is empty
     */
    public int[] waitForSend(long timeoutMs) throws Exception {
        long now = System.currentTimeMillis();
        long deadline = now + timeoutMs;
        synchronized (mLock) {
            while (mSentStates.isEmpty() && now < deadline) {
                mLock.wait(deadline - now);
                now = System.currentTimeMillis();
            }
            if (mSentStates.isEmpty()) {
                throw new TimeoutException("mSentStates is still empty "
                        + "(monitor was not notified in " + timeoutMs + " ms)");
            }
            return mSentStates.removeFirst();
        }
    }

    private void doSendState(int state, int param) {
        int[] toSend = new int[] {state, param};
        SignalListener listener;
        synchronized (mLock) {
            listener = mSignalListener;
            mSentStates.addLast(toSend);
            mLock.notifyAll();
        }
        if (listener != null) {
            listener.sendingSignal(state);
        }
    }

    @Override
    public boolean isPowerStateSupported() {
        return mIsPowerStateSupported;
    }

    @Override
    public boolean isDeepSleepAllowed() {
        return mIsDeepSleepAllowed;
    }

    @Override
    public boolean isHibernationAllowed() {
        return mIsHibernationAllowed;
    }

    @Override
    public boolean isTimedWakeupAllowed() {
        return mIsTimedWakeupAllowed;
    }

    @Override
    public PowerState getCurrentPowerState() {
        synchronized (mLock) {
            return mCurrentPowerState;
        }
    }

    public void setDeepSleepEnabled(boolean enabled) {
        mIsDeepSleepAllowed = enabled;
    }

    public void setHibernationEnabled(boolean enabled) {
        mIsHibernationAllowed = enabled;
    }

    /**
     * Sets the current power state to the state that is given.
     * @param state The power state to set
     */
    public void setCurrentPowerState(PowerState state) {
        setCurrentPowerState(state, true);
    }

    /**
     * Sets the current power state
     * @param state The state to set
     * @param notify If to notify listeners
     */
    public void setCurrentPowerState(PowerState state, boolean notify) {
        PowerEventListener listener;
        synchronized (mLock) {
            mCurrentPowerState = state;
            listener = mListener;
        }
        if (listener != null && notify) {
            listener.onApPowerStateChange(state);
        }
    }
}
