/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.storagemonitoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.car.Car;
import android.car.storagemonitoring.CarStorageMonitoringManager;
import android.car.storagemonitoring.IoStats;
import android.car.storagemonitoring.IoStatsEntry;
import android.car.storagemonitoring.LifetimeWriteInfo;
import android.car.storagemonitoring.UidIoRecord;
import android.car.storagemonitoring.WearEstimate;
import android.car.storagemonitoring.WearEstimateChange;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.JsonWriter;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.car.CarStorageMonitoringService;
import com.android.car.MockedCarTestBase;
import com.android.car.R;
import com.android.car.procfsinspector.ProcessInfo;
import com.android.car.systeminterface.StorageMonitoringInterface;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.systeminterface.SystemStateInterface;
import com.android.car.systeminterface.TimeInterface;
import com.android.internal.annotations.GuardedBy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Test the public entry points for the CarStorageMonitoringManager */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarStorageMonitoringTest extends MockedCarTestBase {
    private static final String TAG = CarStorageMonitoringTest.class.getSimpleName();

    @Rule public TestName mTestName = new TestName();

    private static final WearInformation DEFAULT_WEAR_INFORMATION =
        new WearInformation(30, 0, WearInformation.PRE_EOL_INFO_NORMAL);

    static class ResourceOverrides {
        private final HashMap<Integer, Integer> mIntegerOverrides = new HashMap<>();
        private final HashMap<Integer, String> mStringOverrides = new HashMap<>();

        void override(int id, int value) {
            mIntegerOverrides.put(id, value);
        }
        void override(int id, String value) {
            mStringOverrides.put(id, value);
        }

        void overrideResources(MockResources resources) {
            mIntegerOverrides.forEach(resources::overrideResource);
            mStringOverrides.forEach(resources::overrideResource);
        }

        static ResourceOverrides of(int id, int value) {
            ResourceOverrides result = new ResourceOverrides();
            result.override(id, value);
            return result;
        }

        static ResourceOverrides of(
                int id1, int value1,
                int id2, int value2,
                int id3, int value3,
                int id4, int value4) {
            ResourceOverrides result = new ResourceOverrides();
            result.override(id1, value1);
            result.override(id2, value2);
            result.override(id3, value3);
            result.override(id4, value4);
            return result;
        }
    }

    private static final Map<String, ResourceOverrides> PER_TEST_RESOURCES = Map.of(
            "testAggregateIoStats", ResourceOverrides.of(
                    R.integer.ioStatsNumSamplesToStore, 5),
            "testIoStatsDeltas", ResourceOverrides.of(
                    R.integer.ioStatsNumSamplesToStore, 5),
            "testEventDelivery", ResourceOverrides.of(
                    R.integer.ioStatsNumSamplesToStore, 5),
            "testIntentOnExcessiveWrite", ResourceOverrides.of(
                    R.integer.ioStatsNumSamplesToStore, 5,
                    R.integer.maxExcessiveIoSamplesInWindow, 0,
                    R.integer.acceptableWrittenKBytesPerSample, 10,
                    R.integer.acceptableFsyncCallsPerSample, 1000),
            "testIntentOnExcessiveFsync", ResourceOverrides.of(
                    R.integer.ioStatsNumSamplesToStore, 5,
                    R.integer.maxExcessiveIoSamplesInWindow, 0,
                    R.integer.acceptableWrittenKBytesPerSample, 1000,
                    R.integer.acceptableFsyncCallsPerSample, 2),
            "testZeroWindowDisablesCollection", ResourceOverrides.of(
                    R.integer.ioStatsNumSamplesToStore, 0));

    private static final class TestData {
        static final TestData DEFAULT = new TestData(0, DEFAULT_WEAR_INFORMATION, null, null);

        final long uptime;
        @NonNull
        final WearInformation wearInformation;
        @Nullable
        final WearHistory wearHistory;
        @NonNull
        final UidIoRecord[] ioStats;
        @NonNull
        final LifetimeWriteInfo[] previousLifetimeWriteInfo;
        @NonNull
        final LifetimeWriteInfo[] currentLifetimeWriteInfo;

        TestData(long uptime,
            @Nullable WearInformation wearInformation,
            @Nullable WearHistory wearHistory,
            @Nullable UidIoRecord[] ioStats) {
            this(uptime, wearInformation, wearHistory, ioStats, null, null);
        }

        TestData(long uptime,
                @Nullable WearInformation wearInformation,
                @Nullable WearHistory wearHistory,
                @Nullable UidIoRecord[] ioStats,
                @Nullable LifetimeWriteInfo[] previousLifetimeWriteInfo,
                @Nullable LifetimeWriteInfo[] currentLifetimeWriteInfo) {
            if (wearInformation == null) wearInformation = DEFAULT_WEAR_INFORMATION;
            if (ioStats == null) ioStats = new UidIoRecord[0];
            if (previousLifetimeWriteInfo == null) {
                previousLifetimeWriteInfo = new LifetimeWriteInfo[0];
            }
            if (currentLifetimeWriteInfo == null) {
                currentLifetimeWriteInfo = new LifetimeWriteInfo[0];
            }
            this.uptime = uptime;
            this.wearInformation = wearInformation;
            this.wearHistory = wearHistory;
            this.ioStats = ioStats;
            this.previousLifetimeWriteInfo = previousLifetimeWriteInfo;
            this.currentLifetimeWriteInfo = currentLifetimeWriteInfo;
        }
    }

    private static final Map<String, TestData> PER_TEST_DATA = Map.of(
            "testReadWearHistory",
                new TestData(6500, DEFAULT_WEAR_INFORMATION,
                    WearHistory.fromRecords(
                        WearEstimateRecord.Builder.newBuilder()
                            .fromWearEstimate(WearEstimate.UNKNOWN_ESTIMATE)
                            .toWearEstimate(new WearEstimate(10, 0))
                            .atUptime(1000)
                            .atTimestamp(Instant.ofEpochMilli(5000)).build(),
                        WearEstimateRecord.Builder.newBuilder()
                            .fromWearEstimate(new WearEstimate(10, 0))
                            .toWearEstimate(new WearEstimate(20, 0))
                            .atUptime(4000)
                            .atTimestamp(Instant.ofEpochMilli(12000)).build(),
                        WearEstimateRecord.Builder.newBuilder()
                            .fromWearEstimate(new WearEstimate(20, 0))
                            .toWearEstimate(new WearEstimate(30, 0))
                            .atUptime(6500)
                            .atTimestamp(Instant.ofEpochMilli(17000)).build()), null),

            "testNotAcceptableWearEvent",
                new TestData(2520006499L,
                    new WearInformation(40, 0, WearInformation.PRE_EOL_INFO_NORMAL),
                    WearHistory.fromRecords(
                        WearEstimateRecord.Builder.newBuilder()
                            .fromWearEstimate(WearEstimate.UNKNOWN_ESTIMATE)
                            .toWearEstimate(new WearEstimate(10, 0))
                            .atUptime(1000)
                            .atTimestamp(Instant.ofEpochMilli(5000)).build(),
                        WearEstimateRecord.Builder.newBuilder()
                            .fromWearEstimate(new WearEstimate(10, 0))
                            .toWearEstimate(new WearEstimate(20, 0))
                            .atUptime(4000)
                            .atTimestamp(Instant.ofEpochMilli(12000)).build(),
                        WearEstimateRecord.Builder.newBuilder()
                            .fromWearEstimate(new WearEstimate(20, 0))
                            .toWearEstimate(new WearEstimate(30, 0))
                            .atUptime(6500)
                            .atTimestamp(Instant.ofEpochMilli(17000)).build()), null),

            "testAcceptableWearEvent",
                new TestData(2520006501L,
                    new WearInformation(40, 0, WearInformation.PRE_EOL_INFO_NORMAL),
                    WearHistory.fromRecords(
                        WearEstimateRecord.Builder.newBuilder()
                            .fromWearEstimate(WearEstimate.UNKNOWN_ESTIMATE)
                            .toWearEstimate(new WearEstimate(10, 0))
                            .atUptime(1000)
                            .atTimestamp(Instant.ofEpochMilli(5000)).build(),
                        WearEstimateRecord.Builder.newBuilder()
                            .fromWearEstimate(new WearEstimate(10, 0))
                            .toWearEstimate(new WearEstimate(20, 0))
                            .atUptime(4000)
                            .atTimestamp(Instant.ofEpochMilli(12000)).build(),
                        WearEstimateRecord.Builder.newBuilder()
                            .fromWearEstimate(new WearEstimate(20, 0))
                            .toWearEstimate(new WearEstimate(30, 0))
                            .atUptime(6500)
                            .atTimestamp(Instant.ofEpochMilli(17000)).build()), null),

            "testBootIoStats",
                new TestData(1000L,
                    new WearInformation(0, 0, WearInformation.PRE_EOL_INFO_NORMAL),
                    null,
                    new UidIoRecord[]{
                        new UidIoRecord(0, 5000, 6000, 3000, 1000, 1,
                            0, 0, 0, 0, 0),
                        new UidIoRecord(1000, 200, 5000, 0, 4000, 0,
                            1000, 0, 500, 0, 0)}),

            "testAggregateIoStats",
                new TestData(1000L,
                    new WearInformation(0, 0, WearInformation.PRE_EOL_INFO_NORMAL),
                    null,
                    new UidIoRecord[]{
                        new UidIoRecord(0, 5000, 6000, 3000, 1000, 1,
                            0, 0, 0, 0, 0),
                        new UidIoRecord(1000, 200, 5000, 0, 4000, 0,
                            1000, 0, 500, 0, 0)}),

            "testIoStatsDeltas",
                new TestData(1000L,
                    new WearInformation(0, 0, WearInformation.PRE_EOL_INFO_NORMAL),
                    null,
                    new UidIoRecord[]{
                        new UidIoRecord(0, 5000, 6000, 3000, 1000, 1,
                            0, 0, 0, 0, 0)}),

            "testComputeShutdownCost",
                new TestData(1000L,
                    new WearInformation(0, 0, WearInformation.PRE_EOL_INFO_NORMAL),
                    null,
                    null,
                    new LifetimeWriteInfo[] { new LifetimeWriteInfo("p1", "ext4", 120),
                                              new LifetimeWriteInfo("p2", "ext4", 100),
                                              new LifetimeWriteInfo("p3", "f2fs", 100)},
                    new LifetimeWriteInfo[] { new LifetimeWriteInfo("p1", "ext4", 200),
                                              new LifetimeWriteInfo("p2", "ext4", 300),
                                              new LifetimeWriteInfo("p3", "f2fs", 100)}),

            "testNegativeShutdownCost",
                new TestData(1000L,
                    new WearInformation(0, 0, WearInformation.PRE_EOL_INFO_NORMAL),
                    null,
                    null,
                    new LifetimeWriteInfo[] { new LifetimeWriteInfo("p1", "ext4", 120),
                        new LifetimeWriteInfo("p2", "ext4", 100),
                        new LifetimeWriteInfo("p3", "f2fs", 200)},
                    new LifetimeWriteInfo[] { new LifetimeWriteInfo("p1", "ext4", 200),
                        new LifetimeWriteInfo("p2", "ext4", 300),
                        new LifetimeWriteInfo("p3", "f2fs", 100)}));

    private final MockSystemStateInterface mMockSystemStateInterface =
            new MockSystemStateInterface();
    private final MockStorageMonitoringInterface mMockStorageMonitoringInterface =
            new MockStorageMonitoringInterface();
    private final MockTimeInterface mMockTimeInterface =
            new MockTimeInterface();

    private CarStorageMonitoringManager mCarStorageMonitoringManager;

    @Override
    protected MockedCarTestContext createMockedCarTestContext(Context context) {
        return new CarStorageMonitoringTestContext(context);
    }

    @Override
    protected SystemInterface.Builder getSystemInterfaceBuilder() {
        SystemInterface.Builder builder = super.getSystemInterfaceBuilder();
        return builder.withSystemStateInterface(mMockSystemStateInterface)
            .withStorageMonitoringInterface(mMockStorageMonitoringInterface)
            .withTimeInterface(mMockTimeInterface);
    }

    @Override
    protected void configureFakeSystemInterface() {
        try {
            final TestData wearData = PER_TEST_DATA.getOrDefault(getName(), TestData.DEFAULT);
            final WearHistory wearHistory = wearData.wearHistory;

            mMockStorageMonitoringInterface.setWearInformation(wearData.wearInformation);
            mMockStorageMonitoringInterface.setWriteInfo(wearData.currentLifetimeWriteInfo);

            if (wearHistory != null) {
                File wearHistoryFile = new File(getFakeSystemInterface().getSystemCarDir(),
                    CarStorageMonitoringService.WEAR_INFO_FILENAME);
                try (JsonWriter jsonWriter = new JsonWriter(new FileWriter(wearHistoryFile))) {
                    wearHistory.writeToJson(jsonWriter);
                }
            }

            if (wearData.uptime > 0) {
                File uptimeFile = new File(getFakeSystemInterface().getSystemCarDir(),
                    CarStorageMonitoringService.UPTIME_TRACKER_FILENAME);
                try (JsonWriter jsonWriter = new JsonWriter(new FileWriter(uptimeFile))) {
                    jsonWriter.beginObject();
                    jsonWriter.name("uptime").value(wearData.uptime);
                    jsonWriter.endObject();
                }
            }

            if (wearData.previousLifetimeWriteInfo.length > 0) {
                File previousLifetimeFile = new File(getFakeSystemInterface().getSystemCarDir(),
                    CarStorageMonitoringService.LIFETIME_WRITES_FILENAME);
                try (JsonWriter jsonWriter = new JsonWriter(new FileWriter(previousLifetimeFile))) {
                    jsonWriter.beginObject();
                    jsonWriter.name("lifetimeWriteInfo").beginArray();
                    for (LifetimeWriteInfo writeInfo : wearData.previousLifetimeWriteInfo) {
                        writeInfo.writeToJson(jsonWriter);
                    }
                    jsonWriter.endArray().endObject();
                }
            }

            Arrays.stream(wearData.ioStats).forEach(
                    mMockStorageMonitoringInterface::addIoStatsRecord);

        } catch (IOException e) {
            Log.e(TAG, "failed to configure fake system interface", e);
            fail("failed to configure fake system interface instance");
        }
    }

    @Override
    protected void configureResourceOverrides(MockResources resources) {
        super.configureResourceOverrides(resources);
        resources.overrideResource(com.android.car.R.array.config_allowed_optional_car_features,
                new String[] {Car.STORAGE_MONITORING_SERVICE});
        final ResourceOverrides overrides = PER_TEST_RESOURCES.getOrDefault(getName(), null);
        if (overrides != null) {
            overrides.overrideResources(resources);
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mMockSystemStateInterface.executeBootCompletedActions();
        mCarStorageMonitoringManager =
            (CarStorageMonitoringManager) getCar().getCarManager(Car.STORAGE_MONITORING_SERVICE);
    }

    @Test
    public void testReadPreEolInformation() throws Exception {
        assertEquals(DEFAULT_WEAR_INFORMATION.preEolInfo,
                mCarStorageMonitoringManager.getPreEolIndicatorStatus());
    }

    @Test
    public void testReadWearEstimate() throws Exception {
        final WearEstimate wearEstimate = mCarStorageMonitoringManager.getWearEstimate();

        assertNotNull(wearEstimate);
        assertEquals(DEFAULT_WEAR_INFORMATION.lifetimeEstimateA, wearEstimate.typeA);
        assertEquals(DEFAULT_WEAR_INFORMATION.lifetimeEstimateB, wearEstimate.typeB);
    }

    @Test
    public void testReadWearHistory() throws Exception {
        final List<WearEstimateChange> wearEstimateChanges =
                mCarStorageMonitoringManager.getWearEstimateHistory();

        assertNotNull(wearEstimateChanges);
        assertFalse(wearEstimateChanges.isEmpty());

        final WearHistory expectedWearHistory = PER_TEST_DATA.get(getName()).wearHistory;

        assertEquals(expectedWearHistory.size(), wearEstimateChanges.size());
        for (int i = 0; i < wearEstimateChanges.size(); ++i) {
            final WearEstimateRecord expected = expectedWearHistory.get(i);
            final WearEstimateChange actual = wearEstimateChanges.get(i);

            assertTrue(expected.isSameAs(actual));
        }
    }

    private void checkLastWearEvent(boolean isAcceptable) throws Exception {
        final List<WearEstimateChange> wearEstimateChanges =
            mCarStorageMonitoringManager.getWearEstimateHistory();

        assertNotNull(wearEstimateChanges);
        assertFalse(wearEstimateChanges.isEmpty());

        final TestData wearData = PER_TEST_DATA.get(getName());

        final WearInformation expectedCurrentWear = wearData.wearInformation;
        final WearEstimate expectedPreviousWear = wearData.wearHistory.getLast().getNewWearEstimate();

        final WearEstimateChange actualCurrentWear =
                wearEstimateChanges.get(wearEstimateChanges.size() - 1);

        assertEquals(isAcceptable, actualCurrentWear.isAcceptableDegradation);
        assertEquals(expectedCurrentWear.toWearEstimate(), actualCurrentWear.newEstimate);
        assertEquals(expectedPreviousWear, actualCurrentWear.oldEstimate);
    }

    @Test
    public void testNotAcceptableWearEvent() throws Exception {
        checkLastWearEvent(false);
    }

    @Test
    public void testAcceptableWearEvent() throws Exception {
        checkLastWearEvent(true);
    }

    @Test
    public void testBootIoStats() throws Exception {
        final List<IoStatsEntry> bootIoStats =
            mCarStorageMonitoringManager.getBootIoStats();

        assertNotNull(bootIoStats);
        assertFalse(bootIoStats.isEmpty());

        final UidIoRecord[] bootIoRecords = PER_TEST_DATA.get(getName()).ioStats;

        bootIoStats.forEach(uidIoStats -> assertTrue(Arrays.stream(bootIoRecords).anyMatch(
                ioRecord -> uidIoStats.representsSameMetrics(ioRecord))));
    }

    @Test
    public void testAggregateIoStats() throws Exception {
        UidIoRecord oldRecord1000 = mMockStorageMonitoringInterface.getIoStatsRecord(1000);

        UidIoRecord newRecord1000 = new UidIoRecord(1000,
            oldRecord1000.foreground_rchar,
            oldRecord1000.foreground_wchar + 50,
            oldRecord1000.foreground_read_bytes,
            oldRecord1000.foreground_write_bytes + 100,
            oldRecord1000.foreground_fsync + 1,
            oldRecord1000.background_rchar,
            oldRecord1000.background_wchar,
            oldRecord1000.background_read_bytes,
            oldRecord1000.background_write_bytes,
            oldRecord1000.background_fsync);

        mMockSystemStateInterface.addProcess(1, 1000);
        mMockStorageMonitoringInterface.addIoStatsRecord(newRecord1000);

        UidIoRecord record2000 = new UidIoRecord(2000,
            1024,
            2048,
            0,
            1024,
            1,
            0,
            0,
            0,
            0,
            0);

        mMockSystemStateInterface.addProcess(1, 2000);
        mMockStorageMonitoringInterface.addIoStatsRecord(record2000);

        mMockTimeInterface.tick();

        List<IoStatsEntry> aggregateIoStats = mCarStorageMonitoringManager.getAggregateIoStats();

        assertNotNull(aggregateIoStats);
        assertFalse(aggregateIoStats.isEmpty());

        aggregateIoStats.forEach(serviceIoStat -> {
            UidIoRecord mockIoStat = mMockStorageMonitoringInterface.getIoStatsRecord(
                    serviceIoStat.uid);

            assertNotNull(mockIoStat);

            assertTrue(serviceIoStat.representsSameMetrics(mockIoStat));
        });
    }

    @Test
    public void testIoStatsDeltas() throws Exception {
        UidIoRecord oldRecord0 = mMockStorageMonitoringInterface.getIoStatsRecord(0);

        UidIoRecord newRecord0 = new UidIoRecord(0,
            oldRecord0.foreground_rchar,
            oldRecord0.foreground_wchar + 100,
            oldRecord0.foreground_read_bytes,
            oldRecord0.foreground_write_bytes + 50,
            oldRecord0.foreground_fsync,
            oldRecord0.background_rchar,
            oldRecord0.background_wchar,
            oldRecord0.background_read_bytes + 100,
            oldRecord0.background_write_bytes,
            oldRecord0.background_fsync);

        mMockStorageMonitoringInterface.addIoStatsRecord(newRecord0);
        mMockTimeInterface.setUptime(500).tick();

        List<IoStats> deltas = mCarStorageMonitoringManager.getIoStatsDeltas();
        assertNotNull(deltas);
        assertEquals(1, deltas.size());

        IoStats delta0 = deltas.get(0);
        assertNotNull(delta0);
        assertEquals(500, delta0.getTimestamp());

        List<IoStatsEntry> delta0Stats = delta0.getStats();
        assertNotNull(delta0Stats);
        assertEquals(1, delta0Stats.size());

        IoStatsEntry deltaRecord0 = delta0Stats.get(0);

        assertTrue(deltaRecord0.representsSameMetrics(newRecord0.delta(oldRecord0)));

        UidIoRecord newerRecord0 = new UidIoRecord(0,
            newRecord0.foreground_rchar + 200,
            newRecord0.foreground_wchar + 10,
            newRecord0.foreground_read_bytes,
            newRecord0.foreground_write_bytes,
            newRecord0.foreground_fsync,
            newRecord0.background_rchar,
            newRecord0.background_wchar + 100,
            newRecord0.background_read_bytes,
            newRecord0.background_write_bytes + 30,
            newRecord0.background_fsync + 2);

        mMockStorageMonitoringInterface.addIoStatsRecord(newerRecord0);
        mMockTimeInterface.setUptime(1000).tick();

        deltas = mCarStorageMonitoringManager.getIoStatsDeltas();
        assertNotNull(deltas);
        assertEquals(2, deltas.size());

        delta0 = deltas.get(0);
        assertNotNull(delta0);
        assertEquals(500, delta0.getTimestamp());

        delta0Stats = delta0.getStats();
        assertNotNull(delta0Stats);
        assertEquals(1, delta0Stats.size());

        deltaRecord0 = delta0Stats.get(0);

        assertTrue(deltaRecord0.representsSameMetrics(newRecord0.delta(oldRecord0)));

        IoStats delta1 = deltas.get(1);
        assertNotNull(delta1);
        assertEquals(1000, delta1.getTimestamp());

        List<IoStatsEntry> delta1Stats = delta1.getStats();
        assertNotNull(delta1Stats);
        assertEquals(1, delta1Stats.size());

        deltaRecord0 = delta1Stats.get(0);

        assertTrue(deltaRecord0.representsSameMetrics(newerRecord0.delta(newRecord0)));
    }

    @Test
    public void testEventDelivery() throws Exception {
        final Duration eventDeliveryDeadline = Duration.ofSeconds(5);

        UidIoRecord record = new UidIoRecord(0,
            0,
            100,
            0,
            75,
            1,
            0,
            0,
            0,
            0,
            0);

        Listener listener1 = new Listener("listener1");
        Listener listener2 = new Listener("listener2");

        mCarStorageMonitoringManager.registerListener(listener1);
        mCarStorageMonitoringManager.registerListener(listener2);

        mMockStorageMonitoringInterface.addIoStatsRecord(record);
        mMockSystemStateInterface.addProcess(1, 0);
        mMockTimeInterface.setUptime(500).tick();

        assertTrue(listener1.waitForEvent(eventDeliveryDeadline));
        assertTrue(listener2.waitForEvent(eventDeliveryDeadline));

        IoStats event1 = listener1.reset();
        IoStats event2 = listener2.reset();

        assertEquals(event1, event2);
        event1.getStats().forEach(stats -> assertTrue(stats.representsSameMetrics(record)));

        mCarStorageMonitoringManager.unregisterListener(listener1);

        mMockTimeInterface.setUptime(600).tick();
        assertFalse(listener1.waitForEvent(eventDeliveryDeadline));
        assertTrue(listener2.waitForEvent(eventDeliveryDeadline));
    }

    @Test
    public void testIntentOnExcessiveWrite() throws Exception {
        UidIoRecord record = new UidIoRecord(0,
            0,
            5120,
            0,
            5000,
            1,
            0,
            7168,
            0,
            7000,
            0);

        mMockStorageMonitoringInterface.addIoStatsRecord(record);
        mMockTimeInterface.setUptime(500).tick();

        CarStorageMonitoringTestContext context = (CarStorageMonitoringTestContext) getContext();
        assertBroadcastArgs(context.getLastBroadcastedIntent(),
                context.getLastBroadcastedString());
    }

    @Test
    public void testIntentOnExcessiveFsync() throws Exception {
        UidIoRecord record = new UidIoRecord(0,
            0,
            0,
            0,
            0,
            2,
            0,
            0,
            0,
            0,
            3);

        mMockStorageMonitoringInterface.addIoStatsRecord(record);
        mMockTimeInterface.setUptime(500).tick();

        CarStorageMonitoringTestContext context = (CarStorageMonitoringTestContext) getContext();
        assertBroadcastArgs(context.getLastBroadcastedIntent(),
                context.getLastBroadcastedString());
    }

    @Test
    public void testZeroWindowDisablesCollection() throws Exception {
        final Duration eventDeliveryDeadline = Duration.ofSeconds(5);

        UidIoRecord record = new UidIoRecord(0,
            0,
            100,
            0,
            75,
            1,
            0,
            0,
            0,
            0,
            0);

        Listener listener = new Listener("listener");

        mMockStorageMonitoringInterface.addIoStatsRecord(record);
        mMockTimeInterface.setUptime(500).tick();

        assertFalse(listener.waitForEvent(eventDeliveryDeadline));

        assertEquals(0, mCarStorageMonitoringManager.getIoStatsDeltas().size());
    }

    @Test
    public void testComputeShutdownCost() throws Exception {
        assertEquals(280, mCarStorageMonitoringManager.getShutdownDiskWriteAmount());
    }

    @Test
    public void testNegativeShutdownCost() throws Exception {
        assertEquals(CarStorageMonitoringManager.SHUTDOWN_COST_INFO_MISSING,
                mCarStorageMonitoringManager.getShutdownDiskWriteAmount());
    }

    private String getName() {
        return mTestName.getMethodName();
    }

    private void assertBroadcastArgs(Intent intent, String receiverPermission) {
        assertEquals(CarStorageMonitoringManager.INTENT_EXCESSIVE_IO, intent.getAction());
        assertEquals(Car.PERMISSION_STORAGE_MONITORING, receiverPermission);
    }

    static final class Listener implements CarStorageMonitoringManager.IoStatsListener {
        private final String mName;
        private final Object mSync = new Object();

        private IoStats mLastEvent = null;

        Listener(String name) {
            mName = name;
        }

        IoStats reset() {
            synchronized (mSync) {
                IoStats lastEvent = mLastEvent;
                mLastEvent = null;
                return lastEvent;
            }
        }

        boolean waitForEvent(Duration duration) {
            long start = SystemClock.elapsedRealtime();
            long end = start + duration.toMillis();
            synchronized (mSync) {
                while (mLastEvent == null && SystemClock.elapsedRealtime() < end) {
                    try {
                        mSync.wait(10L);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }

            return (mLastEvent != null);
        }

        @Override
        public void onSnapshot(IoStats event) {
            synchronized (mSync) {
                Log.d(TAG, "listener " + mName + " received event " + event);
                // We're going to hold a reference to this object
                mLastEvent = event;
                mSync.notifyAll();
            }
        }

    }

    /**
     * Special version of {@link MockedCarTestContext} that stores the last arguments used when
     * invoking {@link  MockedCarTestContext#sendBroadcast(Intent, String)} to be retrieved later
     * by the test.
     */
    private static final class CarStorageMonitoringTestContext extends MockedCarTestContext {
        private Intent mLastBroadcastedIntent;
        private String mLastBroadcastedString;

        CarStorageMonitoringTestContext(Context base) {
            super(base);
        }

        @Override
        public void sendBroadcast(@RequiresPermission Intent intent,
                @Nullable String receiverPermission) {
            mLastBroadcastedIntent = intent;
            mLastBroadcastedString = receiverPermission;
            super.sendBroadcast(intent, receiverPermission);
        }

        Intent getLastBroadcastedIntent() {
            return mLastBroadcastedIntent;
        }

        String getLastBroadcastedString() {
            return mLastBroadcastedString;
        }
    }

    static final class MockStorageMonitoringInterface implements StorageMonitoringInterface,
        WearInformationProvider {
        private WearInformation mWearInformation = null;
        private SparseArray<UidIoRecord> mIoStats = new SparseArray<>();
        private UidIoStatsProvider mIoStatsProvider = () -> mIoStats;
        private LifetimeWriteInfo[] mWriteInfo = new LifetimeWriteInfo[0];

        void setWearInformation(WearInformation wearInformation) {
            mWearInformation = wearInformation;
        }

        void setWriteInfo(LifetimeWriteInfo[] writeInfo) {
            mWriteInfo = writeInfo;
        }

        void addIoStatsRecord(UidIoRecord record) {
            mIoStats.append(record.uid, record);
        }

        UidIoRecord getIoStatsRecord(int uid) {
            return mIoStats.get(uid);
        }

        void deleteIoStatsRecord(int uid) {
            mIoStats.delete(uid);
        }

        @Override
        public WearInformation load() {
            return mWearInformation;
        }

        @Override
        public LifetimeWriteInfoProvider getLifetimeWriteInfoProvider() {
            // cannot make this directly implement because Java does not allow
            // overloading based on return type and there already is a method named
            // load() in WearInformationProvider
            return new LifetimeWriteInfoProvider() {
                @Override
                public LifetimeWriteInfo[] load() {
                    return mWriteInfo;
                }
            };
        }

        @Override
        public WearInformationProvider[] getFlashWearInformationProviders(
                String lifetimePath, String eolPath) {
            return new WearInformationProvider[] {this};
        }

        @Override
        public UidIoStatsProvider getUidIoStatsProvider() {
            return mIoStatsProvider;
        }
    }

    static final class MockTimeInterface implements TimeInterface {
        private final List<Pair<Runnable, Long>> mActionsList = new ArrayList<>();
        private long mUptime = 0;

        @Override
        public long getUptime(boolean includeDeepSleepTime) {
            return mUptime;
        }

        @Override
        public void scheduleAction(Runnable r, long delayMs) {
            mActionsList.add(Pair.create(r, delayMs));
            mActionsList.sort(Comparator.comparing(d -> d.second));
        }

        @Override
        public void cancelAllActions() {
            mActionsList.clear();
        }

        void tick() {
            mActionsList.forEach(pair -> pair.first.run());
        }

        MockTimeInterface setUptime(long time) {
            mUptime = time;
            return this;
        }
    }

    static final class MockSystemStateInterface implements SystemStateInterface {
        private final List<Pair<Runnable, Duration>> mActionsList = new ArrayList<>();
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final List<ProcessInfo> mProcesses = new ArrayList<>();

        @Override
        public void shutdown() {}

        @Override
        public int enterDeepSleep() {
            return SUSPEND_RESULT_SUCCESS;
        }

        @Override
        public int enterHibernation() {
            return SUSPEND_RESULT_SUCCESS;
        }

        @Override
        public void scheduleActionForBootCompleted(Runnable action, Duration delay,
                Duration delayRange) {
            mActionsList.add(Pair.create(action, delay));
            mActionsList.sort(Comparator.comparing(d -> d.second));
        }

        @Override
        public List<ProcessInfo> getRunningProcesses() {
            synchronized (mLock) {
                return mProcesses;
            }
        }

        void executeBootCompletedActions() {
            for (Pair<Runnable, Duration> action : mActionsList) {
                action.first.run();
            }
        }

        void addProcess(int pid, int uid) {
            synchronized (mLock) {
                mProcesses.add(new ProcessInfo(pid, uid));
            }
        }

        void removeUserProcesses(int uid) {
            synchronized (mLock) {
                mProcesses.removeAll(mProcesses.stream().filter(pi -> pi.uid == uid)
                        .collect(Collectors.toList()));
            }
        }
    }
}
