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

package com.android.car;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.car.Car;
import android.car.builtin.util.Slogf;
import android.car.storagemonitoring.CarStorageMonitoringManager;
import android.car.storagemonitoring.ICarStorageMonitoring;
import android.car.storagemonitoring.IIoStatsListener;
import android.car.storagemonitoring.IoStats;
import android.car.storagemonitoring.IoStatsEntry;
import android.car.storagemonitoring.IoStatsEntry.Metrics;
import android.car.storagemonitoring.LifetimeWriteInfo;
import android.car.storagemonitoring.UidIoRecord;
import android.car.storagemonitoring.WearEstimate;
import android.car.storagemonitoring.WearEstimateChange;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.JsonWriter;
import android.util.Log;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.internal.CarPermission;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.storagemonitoring.IoStatsTracker;
import com.android.car.storagemonitoring.UidIoStatsProvider;
import com.android.car.storagemonitoring.WearEstimateRecord;
import com.android.car.storagemonitoring.WearHistory;
import com.android.car.storagemonitoring.WearInformation;
import com.android.car.storagemonitoring.WearInformationProvider;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.util.SlidingWindow;
import com.android.car.util.SparseArrayStream;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A service to provide storage monitoring data like I/O statistics. In order to receive such data,
 * users need to implement {@link IIoStatsListener} and register themselves against this service.
 */
public class CarStorageMonitoringService extends ICarStorageMonitoring.Stub
        implements CarServiceBase {
    public static final String INTENT_EXCESSIVE_IO =
            CarStorageMonitoringManager.INTENT_EXCESSIVE_IO;

    public static final long SHUTDOWN_COST_INFO_MISSING =
            CarStorageMonitoringManager.SHUTDOWN_COST_INFO_MISSING;

    private static final String TAG = CarLog.tagFor(CarStorageMonitoringService.class);

    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private static final int MIN_WEAR_ESTIMATE_OF_CONCERN = 80;

    // uptime tracker filename.
    @VisibleForTesting
    public static final String UPTIME_TRACKER_FILENAME = "service_uptime";
    // wear info filename.
    @VisibleForTesting
    public static final String WEAR_INFO_FILENAME = "wear_info";
    // lifetime writes filename.
    @VisibleForTesting
    public static final String LIFETIME_WRITES_FILENAME = "lifetime_write";

    private final WearInformationProvider[] mWearInformationProviders;
    private final Context mContext;
    private final File mUptimeTrackerFile;
    private final File mWearInfoFile;
    private final File mLifetimeWriteFile;
    private final OnShutdownReboot mOnShutdownReboot;
    private final SystemInterface mSystemInterface;
    private final UidIoStatsProvider mUidIoStatsProvider;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SlidingWindow<IoStats> mIoStatsSamples;

    private final RemoteCallbackList<IIoStatsListener> mListeners;
    private final Configuration mConfiguration;
    private final CarPermission mStorageMonitoringPermission;

    @GuardedBy("mLock")
    private UptimeTracker mUptimeTracker = null;

    @GuardedBy("mLock")
    private Optional<WearInformation> mWearInformation = Optional.empty();

    @GuardedBy("mLock")
    private List<WearEstimateChange> mWearEstimateChanges;

    @GuardedBy("mLock")
    private List<IoStatsEntry> mBootIoStats = Collections.emptyList();

    @GuardedBy("mLock")
    private IoStatsTracker mIoStatsTracker = null;

    @GuardedBy("mLock")
    private boolean mInitialized = false;

    @GuardedBy("mLock")
    private long mShutdownCostInfo = SHUTDOWN_COST_INFO_MISSING;

    @GuardedBy("mLock")
    private String mShutdownCostMissingReason;

    public CarStorageMonitoringService(Context context, SystemInterface systemInterface) {
        mContext = context;
        Resources resources = mContext.getResources();
        mConfiguration = new Configuration(resources);

        if (Slogf.isLoggable(TAG, Log.DEBUG)) {
            Slogf.d(TAG, "service configuration: " + mConfiguration);
        }

        mUidIoStatsProvider = systemInterface.getUidIoStatsProvider();
        mUptimeTrackerFile = new File(systemInterface.getSystemCarDir(), UPTIME_TRACKER_FILENAME);
        mWearInfoFile = new File(systemInterface.getSystemCarDir(), WEAR_INFO_FILENAME);
        mLifetimeWriteFile = new File(systemInterface.getSystemCarDir(), LIFETIME_WRITES_FILENAME);
        mOnShutdownReboot = new OnShutdownReboot(mContext);
        mSystemInterface = systemInterface;
        mWearInformationProviders = systemInterface.getFlashWearInformationProviders(
                mConfiguration.eMmcLifetimeFilePath,
                mConfiguration.eMmcEolFilePath);
        mStorageMonitoringPermission =
                new CarPermission(mContext, Car.PERMISSION_STORAGE_MONITORING);
        mWearEstimateChanges = Collections.emptyList();
        mIoStatsSamples = new SlidingWindow<>(mConfiguration.ioStatsNumSamplesToStore);
        mListeners = new RemoteCallbackList<>();
        systemInterface.scheduleActionForBootCompleted(() -> {
            synchronized (mLock) {
                doInitServiceIfNeededLocked();
            }}, Duration.ofSeconds(10));
    }

    private Optional<WearInformation> loadWearInformation() {
        for (WearInformationProvider provider : mWearInformationProviders) {
            WearInformation wearInfo = provider.load();
            if (wearInfo != null) {
                Slogf.d(TAG, "retrieved wear info " + wearInfo + " via provider " + provider);
                return Optional.of(wearInfo);
            }
        }

        Slogf.d(TAG, "no wear info available");
        return Optional.empty();
    }

    private WearHistory loadWearHistory() {
        if (mWearInfoFile.exists()) {
            try {
                WearHistory wearHistory = WearHistory.fromJson(mWearInfoFile);
                Slogf.d(TAG, "retrieved wear history " + wearHistory);
                return wearHistory;
            } catch (IOException | JSONException e) {
                Slogf.e(TAG, "unable to read wear info file " + mWearInfoFile, e);
            }
        }

        Slogf.d(TAG, "no wear history available");
        return new WearHistory();
    }

    // returns true iff a new event was added (and hence the history needs to be saved)
    @GuardedBy("mLock")
    private boolean addEventIfNeededLocked(WearHistory wearHistory) {
        if (!mWearInformation.isPresent()) return false;

        WearInformation wearInformation = mWearInformation.get();
        WearEstimate lastWearEstimate;
        WearEstimate currentWearEstimate = wearInformation.toWearEstimate();

        if (wearHistory.size() == 0) {
            lastWearEstimate = WearEstimate.UNKNOWN_ESTIMATE;
        } else {
            lastWearEstimate = wearHistory.getLast().getNewWearEstimate();
        }

        if (currentWearEstimate.equals(lastWearEstimate)) return false;

        WearEstimateRecord newRecord = new WearEstimateRecord(lastWearEstimate,
                currentWearEstimate,
                mUptimeTracker.getTotalUptime(),
                Instant.now());
        Slogf.d(TAG, "new wear record generated " + newRecord);
        wearHistory.add(newRecord);
        return true;
    }

    private void storeWearHistory(WearHistory wearHistory) {
        try (JsonWriter jsonWriter = new JsonWriter(new FileWriter(mWearInfoFile))) {
            wearHistory.writeToJson(jsonWriter);
        } catch (IOException e) {
            Slogf.e(TAG, "unable to write wear info file" + mWearInfoFile, e);
        }
    }

    @Override
    public void init() {
        Slogf.d(TAG, "CarStorageMonitoringService init()");
        mOnShutdownReboot.init();
        synchronized (mLock) {
            mUptimeTracker = new UptimeTracker(mUptimeTrackerFile,
                    mConfiguration.uptimeIntervalBetweenUptimeDataWriteMs,
                    mSystemInterface);
        }
    }

    private void launchWearChangeActivity() {
        final String activityPath = mConfiguration.activityHandlerForFlashWearChanges;
        if (activityPath.isEmpty()) return;
        try {
            final ComponentName activityComponent =
                    Objects.requireNonNull(ComponentName.unflattenFromString(activityPath));
            Intent intent = new Intent();
            intent.setComponent(activityComponent);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException | NullPointerException e) {
            Slogf.e(TAG, "value of activityHandlerForFlashWearChanges invalid non-empty string "
                    + activityPath, e);
        }
    }

    private static void logOnAdverseWearLevel(WearInformation wearInformation) {
        if (wearInformation.preEolInfo > WearInformation.PRE_EOL_INFO_NORMAL ||
                Math.max(wearInformation.lifetimeEstimateA,
                        wearInformation.lifetimeEstimateB) >= MIN_WEAR_ESTIMATE_OF_CONCERN) {
            Slogf.w(TAG, "flash storage reached wear a level that requires attention: "
                    + wearInformation);
        }
    }

    private SparseArray<UidIoRecord> loadNewIoStats() {
        SparseArray<UidIoRecord> ioRecords = mUidIoStatsProvider.load();
        return (ioRecords == null ? new SparseArray<>() : ioRecords);
    }

    private void collectNewIoMetrics() {
        SparseArray<IoStatsEntry> currentSample;
        boolean needsExcessiveIoBroadcast;
        IoStats ioStats;
        synchronized (mLock) {
            mIoStatsTracker.update(loadNewIoStats());
            currentSample = mIoStatsTracker.getCurrentSample();
            ioStats = new IoStats(
                    SparseArrayStream.valueStream(currentSample).collect(Collectors.toList()),
                    mSystemInterface.getUptime());
            mIoStatsSamples.add(ioStats);
            needsExcessiveIoBroadcast = needsExcessiveIoBroadcastLocked();
        }

        dispatchNewIoEvent(ioStats);

        if (DBG) {
            if (currentSample.size() == 0) {
                Slogf.d(TAG, "no new I/O stat data");
            } else {
                SparseArrayStream.valueStream(currentSample).forEach(
                        uidIoStats -> Slogf.d(TAG, "updated I/O stat data: " + uidIoStats));
            }
        }

        if (needsExcessiveIoBroadcast) {
            Slogf.d(TAG, "about to send " + INTENT_EXCESSIVE_IO);
            sendExcessiveIoBroadcast();
        }
    }

    private void sendExcessiveIoBroadcast() {
        Slogf.w(TAG, "sending " + INTENT_EXCESSIVE_IO);

        final String receiverPath = mConfiguration.intentReceiverForUnacceptableIoMetrics;
        if (receiverPath.isEmpty()) return;

        final ComponentName receiverComponent;
        try {
            receiverComponent = Objects.requireNonNull(
                    ComponentName.unflattenFromString(receiverPath));
        } catch (NullPointerException e) {
            Slogf.e(TAG, "value of intentReceiverForUnacceptableIoMetrics non-null but invalid:"
                    + receiverPath, e);
            return;
        }

        Intent intent = new Intent(INTENT_EXCESSIVE_IO);
        intent.setComponent(receiverComponent);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent, mStorageMonitoringPermission.toString());
    }

    @GuardedBy("mLock")
    private boolean needsExcessiveIoBroadcastLocked() {
        return mIoStatsSamples.count((IoStats delta) -> {
            Metrics total = delta.getTotals();
            final boolean tooManyBytesWritten =
                    (total.bytesWrittenToStorage > mConfiguration.acceptableBytesWrittenPerSample);
            final boolean tooManyFsyncCalls =
                    (total.fsyncCalls > mConfiguration.acceptableFsyncCallsPerSample);
            return tooManyBytesWritten || tooManyFsyncCalls;
        }) > mConfiguration.maxExcessiveIoSamplesInWindow;
    }

    private void dispatchNewIoEvent(IoStats delta) {
        final int listenersCount = mListeners.beginBroadcast();
        IntStream.range(0, listenersCount).forEach(
                i -> {
                    try {
                        mListeners.getBroadcastItem(i).onSnapshot(delta);
                    } catch (RemoteException e) {
                        Slogf.w(TAG, "failed to dispatch snapshot", e);
                    }
                });
        mListeners.finishBroadcast();
    }

    @GuardedBy("mLock")
    private void doInitServiceIfNeededLocked() {
        if (mInitialized) return;

        Slogf.d(TAG, "initializing CarStorageMonitoringService");

        mWearInformation = loadWearInformation();

        // TODO(egranata): can this be done lazily?
        final WearHistory wearHistory = loadWearHistory();
        final boolean didWearChangeHappen = addEventIfNeededLocked(wearHistory);
        if (didWearChangeHappen) {
            storeWearHistory(wearHistory);
        }
        Slogf.d(TAG, "wear history being tracked is " + wearHistory);
        mWearEstimateChanges = wearHistory.toWearEstimateChanges(
                mConfiguration.acceptableHoursPerOnePercentFlashWear);

        mOnShutdownReboot.addAction((c, i) -> logLifetimeWrites())
                .addAction((c, i) -> release());

        mWearInformation.ifPresent(CarStorageMonitoringService::logOnAdverseWearLevel);

        if (didWearChangeHappen) {
            launchWearChangeActivity();
        }

        long bootUptime = mSystemInterface.getUptime();
        mBootIoStats = SparseArrayStream.valueStream(loadNewIoStats())
                .map(record -> {
                    // at boot, assume all UIDs have been running for as long as the system has
                    // been up, since we don't really know any better
                    IoStatsEntry stats = new IoStatsEntry(record, bootUptime);
                    if (DBG) {
                        Slogf.d(TAG, "loaded boot I/O stat data: " + stats);
                    }
                    return stats;
                }).collect(Collectors.toList());

        mIoStatsTracker = new IoStatsTracker(mBootIoStats,
                mConfiguration.ioStatsRefreshRateMs,
                mSystemInterface.getSystemStateInterface());

        if (mConfiguration.ioStatsNumSamplesToStore > 0) {
            mSystemInterface.scheduleAction(this::collectNewIoMetrics,
                    mConfiguration.ioStatsRefreshRateMs);
        } else {
            Slogf.i(TAG, "service configuration disabled I/O sample window. not collecting "
                    + "samples");
        }

        mShutdownCostInfo = computeShutdownCostLocked();
        Slogf.d(TAG, "calculated data written in last shutdown was " + mShutdownCostInfo
                + " bytes");
        mLifetimeWriteFile.delete();

        Slogf.i(TAG, "CarStorageMonitoringService is up");

        mInitialized = true;
    }

    @GuardedBy("mLock")
    private long computeShutdownCostLocked() {
        List<LifetimeWriteInfo> shutdownWrites = loadLifetimeWrites();
        if (shutdownWrites.isEmpty()) {
            Slogf.d(TAG, "lifetime write data from last shutdown missing");
            mShutdownCostMissingReason = "no historical writes stored at last shutdown";
            return SHUTDOWN_COST_INFO_MISSING;
        }
        List<LifetimeWriteInfo> currentWrites =
                Arrays.asList(mSystemInterface.getLifetimeWriteInfoProvider().load());
        if (currentWrites.isEmpty()) {
            Slogf.d(TAG, "current lifetime write data missing");
            mShutdownCostMissingReason = "current write data cannot be obtained";
            return SHUTDOWN_COST_INFO_MISSING;
        }

        long shutdownCost = 0;

        Map<String, Long> shutdownLifetimeWrites = new HashMap<>();
        shutdownWrites.forEach(li ->
                shutdownLifetimeWrites.put(li.partition, li.writtenBytes));

        // for every partition currently available, look for it in the shutdown data
        for (int i = 0; i < currentWrites.size(); ++i) {
            LifetimeWriteInfo li = currentWrites.get(i);
            // if this partition was not available when we last shutdown the system, then
            // just pretend we had written the same amount of data then as we have now
            final long writtenAtShutdown =
                    shutdownLifetimeWrites.getOrDefault(li.partition, li.writtenBytes);
            final long costDelta = li.writtenBytes - writtenAtShutdown;
            if (costDelta >= 0) {
                Slogf.d(TAG, "partition " + li.partition + " had " + costDelta
                        + " bytes written to it during shutdown");
                shutdownCost += costDelta;
            } else {
                // the counter of written bytes should be monotonic; a decrease might mean
                // corrupt data, improper shutdown or that the kernel in use does not
                // have proper monotonic guarantees on the lifetime write data. If any of these
                // occur, it's probably safer to just bail out and say we don't know
                mShutdownCostMissingReason = li.partition + " has a negative write amount ("
                        + costDelta + " bytes)";
                Slogf.e(TAG, "partition " + li.partition + " reported " + costDelta
                        + " bytes written to it during shutdown. assuming we can't"
                        + " determine proper shutdown information.");
                return SHUTDOWN_COST_INFO_MISSING;
            }
        }

        return shutdownCost;
    }

    private List<LifetimeWriteInfo> loadLifetimeWrites() {
        if (!mLifetimeWriteFile.exists() || !mLifetimeWriteFile.isFile()) {
            Slogf.d(TAG, "lifetime write file missing or inaccessible " + mLifetimeWriteFile);
            return new ArrayList<>();
        }
        try {
            JSONObject jsonObject = new JSONObject(
                    new String(Files.readAllBytes(mLifetimeWriteFile.toPath())));

            JSONArray jsonArray = jsonObject.getJSONArray("lifetimeWriteInfo");

            List<LifetimeWriteInfo> result = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); ++i) {
                result.add(new LifetimeWriteInfo(jsonArray.getJSONObject(i)));
            }
            return result;
        } catch (JSONException | IOException e) {
            Slogf.e(TAG, "lifetime write file does not contain valid JSON", e);
            return new ArrayList<>();
        }
    }

    private void logLifetimeWrites() {
        try {
            LifetimeWriteInfo[] lifetimeWriteInfos =
                    mSystemInterface.getLifetimeWriteInfoProvider().load();
            JsonWriter jsonWriter = new JsonWriter(new FileWriter(mLifetimeWriteFile));
            jsonWriter.beginObject();
            jsonWriter.name("lifetimeWriteInfo").beginArray();
            for (LifetimeWriteInfo writeInfo : lifetimeWriteInfos) {
                Slogf.d(TAG, "storing lifetime write info " + writeInfo);
                writeInfo.writeToJson(jsonWriter);
            }
            jsonWriter.endArray().endObject();
            jsonWriter.close();
        } catch (IOException e) {
            Slogf.e(TAG, "unable to save lifetime write info on shutdown", e);
        }
    }

    @Override
    public void release() {
        Slogf.i(TAG, "tearing down CarStorageMonitoringService");
        synchronized (mLock) {
            if (mUptimeTracker != null) {
                mUptimeTracker.onDestroy();
            }
        }
        mOnShutdownReboot.release();
        mListeners.kill();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*CarStorageMonitoringService*");
        synchronized (mLock) {
            doInitServiceIfNeededLocked();
            writer.println("last wear information retrieved: "
                    + mWearInformation.map(WearInformation::toString).orElse("missing"));
            writer.println(listToString(mWearEstimateChanges, "\n",
                    "wear change history: "));
            writer.println(listToString(mBootIoStats, "\n", "boot I/O stats: "));
            writer.println("aggregate I/O stats: "
                    + SparseArrayStream.valueStream(mIoStatsTracker.getTotal())
                    .map(IoStatsEntry::toString)
                    .collect(Collectors.joining("\n")));
            writer.println("I/O stats snapshots: ");
            writer.println(
                    mIoStatsSamples.stream().map(
                            sample -> listToString(sample.getStats(), "\n", ""))
                            .collect(Collectors.joining("\n------\n")));
            if (mShutdownCostInfo < 0) {
                writer.print("last shutdown cost: missing. ");
                if (mShutdownCostMissingReason != null && !mShutdownCostMissingReason.isEmpty()) {
                    writer.println("reason: " + mShutdownCostMissingReason);
                }
            } else {
                writer.println("last shutdown cost: " + mShutdownCostInfo + " bytes, estimated");
            }
        }
    }

    private <T> String listToString(List<T> list, CharSequence delimiter, String prefix) {
        StringJoiner stringJoiner = new StringJoiner(delimiter, prefix, /* suffix= */ "");
        for (int index = 0; index < list.size(); index++) {
            stringJoiner.add(list.get(index).toString());
        }
        return stringJoiner.toString();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    // ICarStorageMonitoring implementation

    @Override
    public int getPreEolIndicatorStatus() {
        mStorageMonitoringPermission.assertGranted();
        synchronized (mLock) {
            doInitServiceIfNeededLocked();

            return mWearInformation.map(wi -> wi.preEolInfo)
                    .orElse(WearInformation.UNKNOWN_PRE_EOL_INFO);
        }
    }

    /**
     * @deprecated wear estimate data is unreliable
     */
    @Deprecated
    @Override
    public WearEstimate getWearEstimate() {
        mStorageMonitoringPermission.assertGranted();
        synchronized (mLock) {
            doInitServiceIfNeededLocked();

            return mWearInformation.map(wi ->
                    new WearEstimate(wi.lifetimeEstimateA, wi.lifetimeEstimateB)).orElse(
                    WearEstimate.UNKNOWN_ESTIMATE);
        }
    }

    /**
     * @deprecated wear estimate data is unreliable
     */
    @Deprecated
    @Override
    public List<WearEstimateChange> getWearEstimateHistory() {
        mStorageMonitoringPermission.assertGranted();
        synchronized (mLock) {
            doInitServiceIfNeededLocked();

            return Collections.unmodifiableList(mWearEstimateChanges);
        }
    }

    /**
     * @deprecated use
     * {@link com.android.car.watchdog.CarWatchdogService#getResourceOveruseStats(int, int)}
     * instead.
     * WARNING: The metrics provided are aggregated through time and could include data retrieved
     * after system boot. Also, the I/O stats are only for the calling package.
     */
    @Deprecated
    @Override
    public List<IoStatsEntry> getBootIoStats() {
        mStorageMonitoringPermission.assertGranted();
        synchronized (mLock) {
            doInitServiceIfNeededLocked();

            return Collections.unmodifiableList(mBootIoStats);
        }
    }

    /**
     * @deprecated use
     * {@link com.android.car.watchdog.CarWatchdogService#getResourceOveruseStats(int, int)} instead.
     * WARNING: The I/O stats returned are only for the calling package.
     */
    @Deprecated
    @Override
    public List<IoStatsEntry> getAggregateIoStats() {
        mStorageMonitoringPermission.assertGranted();
        synchronized (mLock) {
            doInitServiceIfNeededLocked();

            return Collections.unmodifiableList(SparseArrayStream.valueStream(
                    mIoStatsTracker.getTotal()).collect(Collectors.toList()));
        }
    }

    /**
     * @deprecated use
     * {@link com.android.car.watchdog.CarWatchdogService#getResourceOveruseStats(int, int)}
     * instead.
     * WARNING: The metrics provided are aggregated through time and could include data not related
     * to system shutdown. Also, the I/O stats are only for the calling package.
     */
    @Deprecated
    @Override
    public long getShutdownDiskWriteAmount() {
        mStorageMonitoringPermission.assertGranted();
        synchronized (mLock) {
            doInitServiceIfNeededLocked();

            return mShutdownCostInfo;
        }
    }

    /**
     * @deprecated use
     * {@link com.android.car.watchdog.CarWatchdogService#getResourceOveruseStats(int, int)} instead.
     * WARNING: The I/O stats returned are only for the calling package.
     */
    @Deprecated
    @Override
    public List<IoStats> getIoStatsDeltas() {
        mStorageMonitoringPermission.assertGranted();
        synchronized (mLock) {
            doInitServiceIfNeededLocked();

            return Collections.unmodifiableList(
                    mIoStatsSamples.stream().collect(Collectors.toList()));
        }
    }

    /**
     * @deprecated {@link IIoStatsListener} is deprecated
     */
    @Deprecated
    @Override
    public void registerListener(IIoStatsListener listener) {
        mStorageMonitoringPermission.assertGranted();
        synchronized (mLock) {
            doInitServiceIfNeededLocked();
        }
        mListeners.register(listener);
    }

    /**
     * @deprecated {@link IIoStatsListener} is deprecated
     */
    @Deprecated
    @Override
    public void unregisterListener(IIoStatsListener listener) {
        mStorageMonitoringPermission.assertGranted();
        // no need to initialize service if unregistering

        mListeners.unregister(listener);
    }

    private static final class Configuration {
        final long acceptableBytesWrittenPerSample;
        final int acceptableFsyncCallsPerSample;
        final int acceptableHoursPerOnePercentFlashWear;
        final String activityHandlerForFlashWearChanges;
        final String intentReceiverForUnacceptableIoMetrics;
        final int ioStatsNumSamplesToStore;
        final int ioStatsRefreshRateMs;
        final int maxExcessiveIoSamplesInWindow;
        final long uptimeIntervalBetweenUptimeDataWriteMs;
        final String eMmcLifetimeFilePath;
        final String eMmcEolFilePath;

        Configuration(Resources resources) throws Resources.NotFoundException {
            ioStatsNumSamplesToStore = resources.getInteger(R.integer.ioStatsNumSamplesToStore);
            acceptableBytesWrittenPerSample =
                    1024L * resources.getInteger(R.integer.acceptableWrittenKBytesPerSample);
            acceptableFsyncCallsPerSample =
                    resources.getInteger(R.integer.acceptableFsyncCallsPerSample);
            maxExcessiveIoSamplesInWindow =
                    resources.getInteger(R.integer.maxExcessiveIoSamplesInWindow);
            uptimeIntervalBetweenUptimeDataWriteMs = 60L * 60 * 1000
                    * resources.getInteger(R.integer.uptimeHoursIntervalBetweenUptimeDataWrite);
            acceptableHoursPerOnePercentFlashWear =
                    resources.getInteger(R.integer.acceptableHoursPerOnePercentFlashWear);
            ioStatsRefreshRateMs = 1000 * resources.getInteger(R.integer.ioStatsRefreshRateSeconds);
            activityHandlerForFlashWearChanges =
                    resources.getString(R.string.activityHandlerForFlashWearChanges);
            intentReceiverForUnacceptableIoMetrics =
                    resources.getString(R.string.intentReceiverForUnacceptableIoMetrics);
            eMmcLifetimeFilePath = resources.getString(R.string.eMmcLifetimeFilePath);
            eMmcEolFilePath = resources.getString(R.string.eMmcEolFilePath);
        }

        @Override
        public String toString() {
            return String.format("acceptableBytesWrittenPerSample = %d, "
                            + "acceptableFsyncCallsPerSample = %d, "
                            + "acceptableHoursPerOnePercentFlashWear = %d, "
                            + "activityHandlerForFlashWearChanges = %s, "
                            + "intentReceiverForUnacceptableIoMetrics = %s, "
                            + "ioStatsNumSamplesToStore = %d, "
                            + "ioStatsRefreshRateMs = %d, "
                            + "maxExcessiveIoSamplesInWindow = %d, "
                            + "uptimeIntervalBetweenUptimeDataWriteMs = %d, "
                            + "eMmcLifetimeFilePath = %s, "
                            + "eMmcEolFilePath = %s",
                    acceptableBytesWrittenPerSample,
                    acceptableFsyncCallsPerSample,
                    acceptableHoursPerOnePercentFlashWear,
                    activityHandlerForFlashWearChanges,
                    intentReceiverForUnacceptableIoMetrics,
                    ioStatsNumSamplesToStore,
                    ioStatsRefreshRateMs,
                    maxExcessiveIoSamplesInWindow,
                    uptimeIntervalBetweenUptimeDataWriteMs,
                    eMmcLifetimeFilePath,
                    eMmcEolFilePath);
        }
    }
}
