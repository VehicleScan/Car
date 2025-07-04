/*
 * Copyright (c) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "carwatchdogd"

#include "ServiceManager.h"

#include "PackageInfoResolver.h"
#include "PerformanceProfiler.h"

#include <android/binder_interface_utils.h>
#include <log/log.h>
#include <utils/SystemClock.h>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::sp;
using ::android::base::Error;
using ::android::base::Result;
using ::android::car::feature::car_watchdog_memory_profiling;
using ::ndk::SharedRefBase;

Result<void> ServiceManager::startServices(const sp<Looper>& mainLooper) {
    if (mWatchdogBinderMediator != nullptr || mWatchdogServiceHelper != nullptr ||
        mWatchdogProcessService != nullptr || mWatchdogPerfService != nullptr) {
        return Error(INVALID_OPERATION) << "Cannot start services more than once";
    }
    /*
     * PackageInfoResolver must be initialized first on the main thread before starting any other
     * thread because the PackageInfoResolver::getInstance method isn't thread safe. Thus initialize
     * PackageInfoResolver by calling the PackageInfoResolver::getInstance method before starting
     * other services as they may access PackageInfoResolver's instance during initialization.
     */
    std::shared_ptr<PackageInfoResolverInterface> packageInfoResolver =
        PackageInfoResolver::getInstance();
    if (auto result = startWatchdogProcessService(mainLooper); !result.ok()) {
        return result;
    }
    mWatchdogServiceHelper = sp<WatchdogServiceHelper>::make();
    if (auto result = mWatchdogServiceHelper->init(mWatchdogProcessService); !result.ok()) {
        return Error() << "Failed to initialize watchdog service helper: " << result.error();
    }
    if (car_watchdog_memory_profiling()) {
        if (auto result = startPressureMonitor(); !result.ok()) {
            ALOGE("%s", result.error().message().c_str());
        }
    }
    if (auto result = startWatchdogPerfService(mWatchdogServiceHelper); !result.ok()) {
        return result;
    }
    if (auto result = packageInfoResolver->initWatchdogServiceHelper(mWatchdogServiceHelper);
        !result.ok()) {
        return Error() << "Failed to initialize package name resolver: " << result.error();
    }
    mIoOveruseMonitor = sp<IoOveruseMonitor>::make(mWatchdogServiceHelper);
    mWatchdogBinderMediator =
            SharedRefBase::make<WatchdogBinderMediator>(mWatchdogProcessService,
                                                        mWatchdogPerfService,
                                                        mWatchdogServiceHelper, mIoOveruseMonitor);
    if (auto result = mWatchdogBinderMediator->init(); !result.ok()) {
        return Error(result.error().code())
                << "Failed to initialize watchdog binder mediator: " << result.error();
    }
    return {};
}

void ServiceManager::terminateServices() {
    if (mWatchdogProcessService != nullptr) {
        mWatchdogProcessService->terminate();
        mWatchdogProcessService.clear();
    }
    if (mWatchdogPerfService != nullptr) {
        mWatchdogPerfService->terminate();
        mWatchdogPerfService.clear();
    }
    if (mWatchdogBinderMediator != nullptr) {
        mWatchdogBinderMediator->terminate();
        mWatchdogBinderMediator.reset();
    }
    if (mWatchdogServiceHelper != nullptr) {
        mWatchdogServiceHelper->terminate();
        mWatchdogServiceHelper.clear();
    }
    if (mPressureMonitor != nullptr) {
        mPressureMonitor->terminate();
        mPressureMonitor.clear();
    }
    mIoOveruseMonitor.clear();
    PackageInfoResolver::terminate();
}

Result<void> ServiceManager::startWatchdogProcessService(const sp<Looper>& mainLooper) {
    mWatchdogProcessService = sp<WatchdogProcessService>::make(mainLooper);
    if (auto result = mWatchdogProcessService->start(); !result.ok()) {
        return Error(result.error().code())
                << "Failed to start watchdog process monitoring service: " << result.error();
    }
    return {};
}

Result<void> ServiceManager::startPressureMonitor() {
    mPressureMonitor = sp<PressureMonitor>::make();
    if (auto result = mPressureMonitor->init(); !result.ok()) {
        return Error() << "Failed to initialize pressure monitor: " << result.error();
    }
    if (auto result = mPressureMonitor->start(); !result.ok()) {
        return Error() << "Failed to start pressure monitor: " << result.error();
    }
    return {};
}

Result<void> ServiceManager::startWatchdogPerfService(
        const sp<WatchdogServiceHelperInterface>& watchdogServiceHelper) {
    mWatchdogPerfService = sp<WatchdogPerfService>::make(watchdogServiceHelper, elapsedRealtime);
    if (auto result = mWatchdogPerfService->registerDataProcessor(
                sp<PerformanceProfiler>::make(mPressureMonitor));
        !result.ok()) {
        return Error() << "Failed to register performance profiler: " << result.error();
    }
    if (auto result = mWatchdogPerfService->start(); !result.ok()) {
        return Error(result.error().code())
                << "Failed to start watchdog performance service: " << result.error();
    }
    return {};
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
