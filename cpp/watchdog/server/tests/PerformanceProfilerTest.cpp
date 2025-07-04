/*
 * Copyright 2020 The Android Open Source Project
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

#include "MockPressureMonitor.h"
#include "MockProcStatCollector.h"
#include "MockUidStatsCollector.h"
#include "MockWatchdogServiceHelper.h"
#include "PackageInfoTestUtils.h"
#include "PerformanceProfiler.h"

#include <WatchdogProperties.sysprop.h>
#include <android-base/file.h>
#include <gmock/gmock.h>
#include <utils/RefBase.h>

#include <android_car_feature.h>
#include <sys/types.h>
#include <unistd.h>

#include <algorithm>
#include <string>
#include <type_traits>
#include <vector>

#include <packages/services/Car/service/proto/android/car/watchdog/carwatchdog_daemon_dump.pb.h>
#include <packages/services/Car/service/proto/android/car/watchdog/performance_stats.pb.h>

namespace android {
namespace automotive {
namespace watchdog {

namespace {

using ::aidl::android::automotive::watchdog::internal::ResourceStats;
using ::aidl::android::automotive::watchdog::internal::ResourceUsageStats;
using ::aidl::android::automotive::watchdog::internal::UidResourceUsageStats;
using ::android::RefBase;
using ::android::sp;
using ::android::base::ReadFdToString;
using ::android::base::Result;
using ::android::base::StringAppendF;
using ::android::car::feature::car_watchdog_memory_profiling;
using ::android::util::ProtoReader;
using ::google::protobuf::RepeatedPtrField;
using ::testing::_;
using ::testing::AllOf;
using ::testing::ElementsAreArray;
using ::testing::Eq;
using ::testing::ExplainMatchResult;
using ::testing::Field;
using ::testing::IsSubsetOf;
using ::testing::Matcher;
using ::testing::Pointer;
using ::testing::Property;
using ::testing::Return;
using ::testing::Test;
using ::testing::UnorderedElementsAreArray;
using ::testing::VariantWith;

using PressureLevelDurationPair = std::pair<PressureMonitorInterface::PressureLevel, int64_t>;
using PressureLevelTransitions = std::vector<PressureLevelDurationPair>;
using PressureLevelDurations =
        std::unordered_map<PressureMonitorInterface::PressureLevel, std::chrono::milliseconds>;

constexpr int kTestBaseUserId = 100;
constexpr bool kTestIsSmapsRollupSupported = true;
constexpr int kTestTopNStatsPerCategory = 5;
constexpr int kTestTopNStatsPerSubcategory = 5;
constexpr int kTestMaxUserSwitchEvents = 3;
constexpr size_t kTestPeriodicCollectionBufferSize = 3;
constexpr std::chrono::seconds kTestSystemEventDataCacheDurationSec = 60s;
const auto kTestNowMillis = std::chrono::time_point_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::from_time_t(1'683'270'000));
constexpr int64_t kTestElapsedRealtimeSinceBootMillis = 19'000;

void applyFeatureFilter(UserPackageSummaryStats* userPackageSummaryStatsOut) {
    if (car_watchdog_memory_profiling()) {
        return;
    }
    userPackageSummaryStatsOut->totalRssKb = 0;
    userPackageSummaryStatsOut->totalPssKb = 0;
    userPackageSummaryStatsOut->topNMemStats = {};
}

MATCHER_P(UidIoSingleOpStatsEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("bytes", &UserPackageStats::UidIoSingleOpStats::bytes,
                                          ElementsAreArray(expected.bytes)),
                                    Field("fsync", &UserPackageStats::UidIoSingleOpStats::fsync,
                                          ElementsAreArray(expected.fsync))),
                              arg, result_listener);
}

MATCHER_P(ProcessSingleStatsEq, expected, "") {
    return ExplainMatchResult(AllOf(
              Field(
                    "comm",
                    &UserPackageStats::UidSingleStats::ProcessSingleStats::
                            comm,
                    Eq(expected.comm)),
              Field("value",
                    &UserPackageStats::UidSingleStats::ProcessSingleStats::
                            value,
                    Eq(expected.value))),
        arg, result_listener);
}

MATCHER_P(UidSingleStatsEq, expected, "") {
    std::vector<Matcher<const UserPackageStats::UidSingleStats::ProcessSingleStats&>>
            processSingleStatsMatchers;
    processSingleStatsMatchers.reserve(expected.topNProcesses.size());
    for (const auto& processSingleStats : expected.topNProcesses) {
        processSingleStatsMatchers.push_back(ProcessSingleStatsEq(processSingleStats));
    }
    return ExplainMatchResult(AllOf(
              Field("value", &UserPackageStats::UidSingleStats::value,
                                          Eq(expected.value)),
              Field("topNProcesses",
                    &UserPackageStats::UidSingleStats::topNProcesses,
                    ElementsAreArray(processSingleStatsMatchers))),
        arg, result_listener);
}

MATCHER_P(ProcessCpuStatsEq, expected, "") {
    return ExplainMatchResult(AllOf(
              Field("pid",
                    &UserPackageStats::UidCpuStats::ProcessCpuStats::pid,
                    Eq(expected.pid)),
              Field("comm",
                    &UserPackageStats::UidCpuStats::ProcessCpuStats::
                            comm,
                    Eq(expected.comm)),
              Field("cpuTimeMillis",
                    &UserPackageStats::UidCpuStats::ProcessCpuStats::
                            cpuTimeMillis,
                    Eq(expected.cpuTimeMillis)),
              Field("cpuCycles",
                    &UserPackageStats::UidCpuStats::ProcessCpuStats::
                            cpuCycles,
                    Eq(expected.cpuCycles))),
        arg, result_listener);
}

MATCHER_P(UidCpuStatsEq, expected, "") {
    std::vector<Matcher<const UserPackageStats::UidCpuStats::ProcessCpuStats&>>
            processSingleStatsMatchers;
    processSingleStatsMatchers.reserve(expected.topNProcesses.size());
    for (const auto& processSingleStats : expected.topNProcesses) {
        processSingleStatsMatchers.push_back(ProcessCpuStatsEq(processSingleStats));
    }
    return ExplainMatchResult(AllOf(Field("cpuTimeMillis",
                                          &UserPackageStats::UidCpuStats::cpuTimeMillis,
                                          Eq(expected.cpuTimeMillis)),
                                    Field("cpuCycles",
                                          &UserPackageStats::UidCpuStats::cpuCycles,
                                          Eq(expected.cpuCycles)),
                                    Field("topNProcesses",
                                          &UserPackageStats::UidCpuStats::topNProcesses,
                                          ElementsAreArray(processSingleStatsMatchers))),
                              arg, result_listener);
}

MATCHER_P(MemoryStatsEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("rssKb", &UserPackageStats::MemoryStats::rssKb,
                                          Eq(expected.rssKb)),
                                    Field("pssKb", &UserPackageStats::MemoryStats::pssKb,
                                          Eq(expected.pssKb)),
                                    Field("ussKb", &UserPackageStats::MemoryStats::ussKb,
                                          Eq(expected.ussKb)),
                                    Field("swapPssKb", &UserPackageStats::MemoryStats::swapPssKb,
                                          Eq(expected.swapPssKb))),
                              arg, result_listener);
}

MATCHER_P(ProcessMemoryStatsEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("comm",
                                          &UserPackageStats::UidMemoryStats::ProcessMemoryStats::
                                                  comm,
                                          Eq(expected.comm)),
                                    Field("memoryStats",
                                          &UserPackageStats::UidMemoryStats::ProcessMemoryStats::
                                                  memoryStats,
                                          MemoryStatsEq(expected.memoryStats))),
                              arg, result_listener);
}

MATCHER_P(UidMemoryStatsEq, expected, "") {
    std::vector<Matcher<const UserPackageStats::UidMemoryStats::ProcessMemoryStats&>>
            processSingleStatsMatchers;
    processSingleStatsMatchers.reserve(expected.topNProcesses.size());
    for (const auto& processSingleStats : expected.topNProcesses) {
        processSingleStatsMatchers.push_back(ProcessMemoryStatsEq(processSingleStats));
    }
    return ExplainMatchResult(AllOf(Field("memoryStats",
                                          &UserPackageStats::UidMemoryStats::memoryStats,
                                          MemoryStatsEq(expected.memoryStats)),
                                    Field("isSmapsRollupSupported",
                                          &UserPackageStats::UidMemoryStats::isSmapsRollupSupported,
                                          Eq(expected.isSmapsRollupSupported)),
                                    Field("topNProcesses",
                                          &UserPackageStats::UidMemoryStats::topNProcesses,
                                          ElementsAreArray(processSingleStatsMatchers))),
                              arg, result_listener);
}

MATCHER_P(UserPackageStatsEq, expected, "") {
    const auto uidMatcher = Field("uid", &UserPackageStats::uid, Eq(expected.uid));
    const auto packageNameMatcher =
            Field("genericPackageName", &UserPackageStats::genericPackageName,
                  Eq(expected.genericPackageName));
    return std::visit(
            [&](const auto& statsVariant) -> bool {
                using T = std::decay_t<decltype(statsVariant)>;
                if constexpr (std::is_same_v<T, UserPackageStats::UidIoSingleOpStats>) {
                    return ExplainMatchResult(
                      AllOf(uidMatcher, packageNameMatcher,
                            Field("statsVariant:UidIoSingleOpStats",
                                  &UserPackageStats::statsVariant,
                                  VariantWith<
                                          UserPackageStats::UidIoSingleOpStats>(
                                          UidIoSingleOpStatsEq(statsVariant)))),
                      arg, result_listener);
                } else if constexpr (std::is_same_v<T, UserPackageStats::UidSingleStats>) {
                    return ExplainMatchResult(AllOf(
                                  uidMatcher,
                                  packageNameMatcher,
                                  Field("statsVariant:UidSingleStats",
                                        &UserPackageStats::statsVariant,
                                        VariantWith<UserPackageStats::
                                                            UidSingleStats>(
                                                UidSingleStatsEq(
                                                        statsVariant)))),
                            arg, result_listener);
                } else if constexpr (std::is_same_v<T, UserPackageStats::UidCpuStats>) {
                    return ExplainMatchResult(AllOf(
                                  uidMatcher,
                                  packageNameMatcher,
                                  Field("statsVariant:UidCpuStats",
                                        &UserPackageStats::statsVariant,
                                        VariantWith<UserPackageStats::
                                                            UidCpuStats>(
                                                UidCpuStatsEq(statsVariant)))),
                            arg, result_listener);
                } else if constexpr (std::is_same_v<T, UserPackageStats::UidMemoryStats>) {
                    return ExplainMatchResult(AllOf(uidMatcher, packageNameMatcher,
                                                    Field("statsVariant:UidMemoryStats",
                                                          &UserPackageStats::statsVariant,
                                                          VariantWith<
                                                                  UserPackageStats::UidMemoryStats>(
                                                                  UidMemoryStatsEq(statsVariant)))),
                                              arg, result_listener);
                }
                *result_listener << "Unexpected variant in UserPackageStats::stats";
                return false;
            },
            expected.statsVariant);
}

MATCHER_P(UserPackageSummaryStatsEq, expected, "") {
    const auto& userPackageStatsMatchers = [&](const std::vector<UserPackageStats>& stats) {
        std::vector<Matcher<const UserPackageStats&>> matchers;
        for (const auto& curStats : stats) {
            matchers.push_back(UserPackageStatsEq(curStats));
        }
        return ElementsAreArray(matchers);
    };
    const auto& totalIoStatsArrayMatcher = [&](const int64_t expected[][UID_STATES]) {
        std::vector<Matcher<const int64_t[UID_STATES]>> matchers;
        for (int i = 0; i < METRIC_TYPES; ++i) {
            matchers.push_back(ElementsAreArray(expected[i], UID_STATES));
        }
        return ElementsAreArray(matchers);
    };
    return ExplainMatchResult(AllOf(Field("topNCpuTimes", &UserPackageSummaryStats::topNCpuTimes,
                                          userPackageStatsMatchers(expected.topNCpuTimes)),
                                    Field("topNIoReads", &UserPackageSummaryStats::topNIoReads,
                                          userPackageStatsMatchers(expected.topNIoReads)),
                                    Field("topNIoWrites", &UserPackageSummaryStats::topNIoWrites,
                                          userPackageStatsMatchers(expected.topNIoWrites)),
                                    Field("topNIoBlocked", &UserPackageSummaryStats::topNIoBlocked,
                                          userPackageStatsMatchers(expected.topNIoBlocked)),
                                    Field("topNMajorFaults",
                                          &UserPackageSummaryStats::topNMajorFaults,
                                          userPackageStatsMatchers(expected.topNMajorFaults)),
                                    Field("topNMemStats", &UserPackageSummaryStats::topNMemStats,
                                          userPackageStatsMatchers(expected.topNMemStats)),
                                    Field("totalIoStats", &UserPackageSummaryStats::totalIoStats,
                                          totalIoStatsArrayMatcher(expected.totalIoStats)),
                                    Field("taskCountByUid",
                                          &UserPackageSummaryStats::taskCountByUid,
                                          IsSubsetOf(expected.taskCountByUid)),
                                    Field("totalCpuTimeMillis",
                                          &UserPackageSummaryStats::totalCpuTimeMillis,
                                          Eq(expected.totalCpuTimeMillis)),
                                    Field("totalCpuCycles",
                                          &UserPackageSummaryStats::totalCpuCycles,
                                          Eq(expected.totalCpuCycles)),
                                    Field("totalMajorFaults",
                                          &UserPackageSummaryStats::totalMajorFaults,
                                          Eq(expected.totalMajorFaults)),
                                    Field("totalRssKb", &UserPackageSummaryStats::totalRssKb,
                                          Eq(expected.totalRssKb)),
                                    Field("totalPssKb", &UserPackageSummaryStats::totalPssKb,
                                          Eq(expected.totalPssKb)),
                                    Field("majorFaultsPercentChange",
                                          &UserPackageSummaryStats::majorFaultsPercentChange,
                                          Eq(expected.majorFaultsPercentChange))),
                              arg, result_listener);
}

MATCHER_P(SystemSummaryStatsEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("cpuIoWaitTimeMillis",
                                          &SystemSummaryStats::cpuIoWaitTimeMillis,
                                          Eq(expected.cpuIoWaitTimeMillis)),
                                    Field("cpuIdleTimeMillis",
                                          &SystemSummaryStats::cpuIdleTimeMillis,
                                          Eq(expected.cpuIdleTimeMillis)),
                                    Field("totalCpuTimeMillis",
                                          &SystemSummaryStats::totalCpuTimeMillis,
                                          Eq(expected.totalCpuTimeMillis)),
                                    Field("totalCpuCycles", &SystemSummaryStats::totalCpuCycles,
                                          Eq(expected.totalCpuCycles)),
                                    Field("contextSwitchesCount",
                                          &SystemSummaryStats::contextSwitchesCount,
                                          Eq(expected.contextSwitchesCount)),
                                    Field("ioBlockedProcessCount",
                                          &SystemSummaryStats::ioBlockedProcessCount,
                                          Eq(expected.ioBlockedProcessCount)),
                                    Field("totalProcessCount",
                                          &SystemSummaryStats::totalProcessCount,
                                          Eq(expected.totalProcessCount))),
                              arg, result_listener);
}

MATCHER_P(PerfStatsRecordEq, expected, "") {
    return ExplainMatchResult(AllOf(Field(&PerfStatsRecord::collectionTimeMillis,
                                          Eq(expected.collectionTimeMillis)),
                                    Field(&PerfStatsRecord::systemSummaryStats,
                                          SystemSummaryStatsEq(expected.systemSummaryStats)),
                                    Field(&PerfStatsRecord::userPackageSummaryStats,
                                          UserPackageSummaryStatsEq(
                                                  expected.userPackageSummaryStats)),
                                    Field(&PerfStatsRecord::memoryPressureLevelDurations,
                                          UnorderedElementsAreArray(
                                                  expected.memoryPressureLevelDurations))),
                              arg, result_listener);
}

const std::vector<Matcher<const PerfStatsRecord&>> constructPerfStatsRecordMatchers(
        const std::vector<PerfStatsRecord>& records) {
    std::vector<Matcher<const PerfStatsRecord&>> matchers;
    for (const auto& record : records) {
        matchers.push_back(PerfStatsRecordEq(record));
    }
    return matchers;
}

MATCHER_P(CollectionInfoEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("maxCacheSize", &CollectionInfo::maxCacheSize,
                                          Eq(expected.maxCacheSize)),
                                    Field("records", &CollectionInfo::records,
                                          ElementsAreArray(constructPerfStatsRecordMatchers(
                                                  expected.records)))),
                              arg, result_listener);
}

MATCHER_P(UserSwitchCollectionInfoEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("from", &UserSwitchCollectionInfo::from,
                                          Eq(expected.from)),
                                    Field("to", &UserSwitchCollectionInfo::to, Eq(expected.to)),
                                    Field("maxCacheSize", &UserSwitchCollectionInfo::maxCacheSize,
                                          Eq(expected.maxCacheSize)),
                                    Field("records", &UserSwitchCollectionInfo::records,
                                          ElementsAreArray(constructPerfStatsRecordMatchers(
                                                  expected.records)))),
                              arg, result_listener);
}

MATCHER_P(UserSwitchCollectionsEq, expected, "") {
    std::vector<Matcher<const UserSwitchCollectionInfo&>> userSwitchCollectionMatchers;
    for (const auto& curCollection : expected) {
        userSwitchCollectionMatchers.push_back(UserSwitchCollectionInfoEq(curCollection));
    }
    return ExplainMatchResult(ElementsAreArray(userSwitchCollectionMatchers), arg, result_listener);
}

int countOccurrences(std::string str, std::string subStr) {
    size_t pos = 0;
    int occurrences = 0;
    while ((pos = str.find(subStr, pos)) != std::string::npos) {
        ++occurrences;
        pos += subStr.length();
    }
    return occurrences;
}

std::tuple<std::vector<UidStats>, UserPackageSummaryStats> sampleUidStats(
        auto int64Multiplier, auto uint64Multiplier, bool isSmapsRollupSupported = true) {
    /* The number of returned sample stats are less that the top N stats per category/sub-category.
     * The top N stats per category/sub-category is set to % during test setup. Thus, the default
     * testing behavior is # reported stats < top N stats.
     */
    std::vector<UidStats>
            uidStats{{.packageInfo = constructPackageInfo("mount", 1009),
                      .cpuTimeMillis = int64Multiplier(50),
                      .ioStats = {/*fgRdBytes=*/0,
                                  /*bgRdBytes=*/int64Multiplier(14'000),
                                  /*fgWrBytes=*/0,
                                  /*bgWrBytes=*/int64Multiplier(16'000),
                                  /*fgFsync=*/0, /*bgFsync=*/int64Multiplier(100)},
                      .procStats = {.cpuTimeMillis = int64Multiplier(50),
                                    .cpuCycles = 4000,
                                    .totalMajorFaults = uint64Multiplier(11'000),
                                    .totalTasksCount = 1,
                                    .ioBlockedTasksCount = 1,
                                    .totalRssKb = 2010,
                                    .totalPssKb = 1635,
                                    .processStatsByPid =
                                            {{/*pid=*/100,
                                              {/*comm=*/"disk I/O", /*startTime=*/234,
                                               /*cpuTimeMillis=*/int64Multiplier(50),
                                               /*totalCpuCycles=*/4000,
                                               /*totalMajorFaults=*/uint64Multiplier(11'000),
                                               /*totalTasksCount=*/1,
                                               /*ioBlockedTasksCount=*/1,
                                               /*cpuCyclesByTid=*/{{100, 4000}},
                                               /*rssKb=*/2010, /*pssKb=*/1635,
                                               /*ussKb=*/1286, /*swapPssKb=*/600}}}}},
                     {.packageInfo =
                              constructPackageInfo("com.google.android.car.kitchensink", 1002001),
                      .cpuTimeMillis = int64Multiplier(60),
                      .ioStats = {/*fgRdBytes=*/0,
                                  /*bgRdBytes=*/int64Multiplier(3'400),
                                  /*fgWrBytes=*/0,
                                  /*bgWrBytes=*/int64Multiplier(6'700),
                                  /*fgFsync=*/0,
                                  /*bgFsync=*/int64Multiplier(200)},
                      .procStats = {.cpuTimeMillis = int64Multiplier(50),
                                    .cpuCycles = 10'000,
                                    .totalMajorFaults = uint64Multiplier(22'445),
                                    .totalTasksCount = 5,
                                    .ioBlockedTasksCount = 3,
                                    .totalRssKb = 2000,
                                    .totalPssKb = 1645,
                                    .processStatsByPid =
                                            {{/*pid=*/1001,
                                              {/*comm=*/"CTS", /*startTime=*/789,
                                               /*cpuTimeMillis=*/int64Multiplier(30),
                                               /*totalCpuCycles=*/5000,
                                               /*totalMajorFaults=*/uint64Multiplier(10'100),
                                               /*totalTasksCount=*/3,
                                               /*ioBlockedTasksCount=*/2,
                                               /*cpuCyclesByTid=*/{{1001, 3000}, {1002, 2000}},
                                               /*rssKb=*/1000, /*pssKb=*/770,
                                               /*ussKb=*/656, /*swapPssKb=*/200}},
                                             {/*pid=*/1000,
                                              {/*comm=*/"KitchenSinkApp", /*startTime=*/467,
                                               /*cpuTimeMillis=*/int64Multiplier(25),
                                               /*totalCpuCycles=*/4000,
                                               /*totalMajorFaults=*/uint64Multiplier(12'345),
                                               /*totalTasksCount=*/2,
                                               /*ioBlockedTasksCount=*/1,
                                               /*cpuCyclesByTid=*/{{1000, 4000}},
                                               /*rssKb=*/1000, /*pssKb=*/875,
                                               /*ussKb=*/630, /*swapPssKb=*/400}}}}},
                     {.packageInfo = constructPackageInfo("", 1012345),
                      .cpuTimeMillis = int64Multiplier(100),
                      .ioStats = {/*fgRdBytes=*/int64Multiplier(1'000),
                                  /*bgRdBytes=*/int64Multiplier(4'200),
                                  /*fgWrBytes=*/int64Multiplier(300),
                                  /*bgWrBytes=*/int64Multiplier(5'600),
                                  /*fgFsync=*/int64Multiplier(600),
                                  /*bgFsync=*/int64Multiplier(300)},
                      .procStats = {.cpuTimeMillis = int64Multiplier(100),
                                    .cpuCycles = 50'000,
                                    .totalMajorFaults = uint64Multiplier(50'900),
                                    .totalTasksCount = 4,
                                    .ioBlockedTasksCount = 2,
                                    .totalRssKb = 1000,
                                    .totalPssKb = 865,
                                    .processStatsByPid =
                                            {{/*pid=*/2345,
                                              {/*comm=*/"MapsApp", /*startTime=*/6789,
                                               /*cpuTimeMillis=*/int64Multiplier(100),
                                               /*totalCpuCycles=*/50'000,
                                               /*totalMajorFaults=*/uint64Multiplier(50'900),
                                               /*totalTasksCount=*/4,
                                               /*ioBlockedTasksCount=*/2,
                                               /*cpuCyclesByTid=*/{{2345, 50'000}},
                                               /*rssKb=*/1000, /*pssKb=*/865,
                                               /*ussKb=*/656, /*swapPssKb=*/200}}}}},
                     {.packageInfo = constructPackageInfo("com.google.radio", 1015678),
                      .cpuTimeMillis = 0,
                      .ioStats = {/*fgRdBytes=*/0,
                                  /*bgRdBytes=*/0,
                                  /*fgWrBytes=*/0,
                                  /*bgWrBytes=*/0,
                                  /*fgFsync=*/0, /*bgFsync=*/0},
                      .procStats = {.cpuTimeMillis = 0,
                                    .cpuCycles = 0,
                                    .totalMajorFaults = 0,
                                    .totalTasksCount = 4,
                                    .ioBlockedTasksCount = 0,
                                    .processStatsByPid = {
                                            {/*pid=*/2345,
                                             {/*comm=*/"RadioApp", /*startTime=*/10789,
                                              /*cpuTimeMillis=*/0,
                                              /*totalCpuCycles=*/0,
                                              /*totalMajorFaults=*/0,
                                              /*totalTasksCount=*/4,
                                              /*ioBlockedTasksCount=*/0,
                                              /*cpuCyclesByTid=*/{}}}}}}};

    std::vector<UserPackageStats> topNMemStatsRankedByPss =
            {{1002001, "com.google.android.car.kitchensink",
              UserPackageStats::UidMemoryStats{{/*rssKb=*/2000, /*pssKb=*/1645,
                                                /*ussKb=*/1286, /*swapPssKb=*/600},
                                               isSmapsRollupSupported,
                                               {{"KitchenSinkApp",
                                                 {/*rssKb=*/1000, /*pssKb=*/875,
                                                  /*ussKb=*/630, /*swapPssKb=*/400}},
                                                {"CTS",
                                                 {/*rssKb=*/1000, /*pssKb=*/770,
                                                  /*ussKb=*/656, /*swapPssKb=*/200}}}}},
             {1009, "mount",
              UserPackageStats::UidMemoryStats{{/*rssKb=*/2010, /*pssKb=*/1635,
                                                /*ussKb=*/1286, /*swapPssKb=*/600},
                                               isSmapsRollupSupported,
                                               {{"disk I/O",
                                                 {/*rssKb=*/2010, /*pssKb=*/1635,
                                                  /*ussKb=*/1286, /*swapPssKb=*/600}}}}},
             {1012345, "1012345",
              UserPackageStats::UidMemoryStats{{/*rssKb=*/1000, /*pssKb=*/865,
                                                /*ussKb=*/656, /*swapPssKb=*/200},
                                               isSmapsRollupSupported,
                                               {{"MapsApp",
                                                 {/*rssKb=*/1000, /*pssKb=*/865,
                                                  /*ussKb=*/656, /*swapPssKb=*/200}}}}}};
    std::vector<UserPackageStats> topNMemStatsRankedByRss =
            {{1009, "mount",
              UserPackageStats::UidMemoryStats{{/*rssKb=*/2010, /*pssKb=*/1635,
                                                /*ussKb=*/1286, /*swapPssKb=*/600},
                                               isSmapsRollupSupported,
                                               {{"disk I/O",
                                                 {/*rssKb=*/2010, /*pssKb=*/1635,
                                                  /*ussKb=*/1286, /*swapPssKb=*/600}}}}},
             {1002001, "com.google.android.car.kitchensink",
              UserPackageStats::UidMemoryStats{{/*rssKb=*/2000, /*pssKb=*/1645,
                                                /*ussKb=*/1286, /*swapPssKb=*/600},
                                               isSmapsRollupSupported,
                                               {{"KitchenSinkApp",
                                                 {/*rssKb=*/1000, /*pssKb=*/875,
                                                  /*ussKb=*/630, /*swapPssKb=*/400}},
                                                {"CTS",
                                                 {/*rssKb=*/1000, /*pssKb=*/770,
                                                  /*ussKb=*/656, /*swapPssKb=*/200}}}}},
             {1012345, "1012345",
              UserPackageStats::UidMemoryStats{{/*rssKb=*/1000, /*pssKb=*/865,
                                                /*ussKb=*/656, /*swapPssKb=*/200},
                                               isSmapsRollupSupported,
                                               {{"MapsApp",
                                                 {/*rssKb=*/1000, /*pssKb=*/865,
                                                  /*ussKb=*/656, /*swapPssKb=*/200}}}}}};
    UserPackageSummaryStats userPackageSummaryStats{
            .topNCpuTimes = {{1012345, "1012345",
                              UserPackageStats::UidCpuStats{int64Multiplier(100),
                                                                 50'000,
                                                                 {{2345, "MapsApp",
                                                                   int64Multiplier(100), 50'000}}}},
                             {1002001, "com.google.android.car.kitchensink",
                              UserPackageStats::UidCpuStats{int64Multiplier(60),
                                                                 10'000,
                                                                 {{1001, "CTS", int64Multiplier(30),
                                                                   5000},
                                                                  {1000, "KitchenSinkApp",
                                                                   int64Multiplier(25), 4000}}}},
                             {1009, "mount",
                              UserPackageStats::UidCpuStats{int64Multiplier(50),
                                                                 4000,
                                                                 {{100, "disk I/O",
                                                                   int64Multiplier(50), 4000}}}}},
            .topNIoReads = {{1009, "mount",
                             UserPackageStats::UidIoSingleOpStats{{0, int64Multiplier(14'000)},
                                                           {0, int64Multiplier(100)}}},
                            {1012345, "1012345",
                             UserPackageStats::UidIoSingleOpStats{{int64Multiplier(1'000),
                                                            int64Multiplier(4'200)},
                                                           {int64Multiplier(600),
                                                            int64Multiplier(300)}}},
                            {1002001, "com.google.android.car.kitchensink",
                             UserPackageStats::UidIoSingleOpStats{{0, int64Multiplier(3'400)},
                                                           {0, int64Multiplier(200)}}}},
            .topNIoWrites =
                    {{1009, "mount",
                      UserPackageStats::UidIoSingleOpStats{{0, int64Multiplier(16'000)},
                                                    {0, int64Multiplier(100)}}},
                     {1002001, "com.google.android.car.kitchensink",
                      UserPackageStats::UidIoSingleOpStats{{0, int64Multiplier(6'700)},
                                                    {0, int64Multiplier(200)}}},
                     {1012345, "1012345",
                      UserPackageStats::UidIoSingleOpStats{{int64Multiplier(300),
                                                        int64Multiplier(5'600)},
                                                    {int64Multiplier(600),
                                                     int64Multiplier(300)}}}},
            .topNIoBlocked =
                    {{1002001, "com.google.android.car.kitchensink",
                      UserPackageStats::UidSingleStats{3,
                                                            {{"CTS", 2}, {"KitchenSinkApp", 1}}}},
                     {1012345, "1012345",
                      UserPackageStats::UidSingleStats{2, {{"MapsApp", 2}}}},
                     {1009,
                      "mount",
                      UserPackageStats::UidSingleStats{1, {{"disk I/O",
                                                                1}}}}},
            .topNMajorFaults =
                    {{1012345, "1012345",
                      UserPackageStats::UidSingleStats{uint64Multiplier(50'900),
                                                            {{"MapsApp",
                                                              uint64Multiplier(50'900)}}}},
                     {1002001, "com.google.android.car.kitchensink",
                      UserPackageStats::UidSingleStats{uint64Multiplier(22'445),
                                                            {{"KitchenSinkApp",
                                                              uint64Multiplier(12'345)},
                                                             {"CTS", uint64Multiplier(10'100)}}}},
                     {1009, "mount",
                      UserPackageStats::UidSingleStats{uint64Multiplier(11'000),
                                                            {{"disk I/O",
                                                              uint64Multiplier(11'000)}}}}},
            .topNMemStats =
                    isSmapsRollupSupported ? topNMemStatsRankedByPss : topNMemStatsRankedByRss,
            .totalIoStats = {{int64Multiplier(1'000), int64Multiplier(21'600)},
                             {int64Multiplier(300), int64Multiplier(28'300)},
                             {int64Multiplier(600), int64Multiplier(600)}},
            .taskCountByUid = {{1009, 1}, {1002001, 5}, {1012345, 4}},
            .totalCpuTimeMillis = int64Multiplier(48'376),
            .totalCpuCycles = 64'000,
            .totalMajorFaults = uint64Multiplier(84'345),
            .totalRssKb = 5010,
            .totalPssKb = 4145,
            .majorFaultsPercentChange = 0.0,
    };
    applyFeatureFilter(&userPackageSummaryStats);
    return std::make_tuple(uidStats, userPackageSummaryStats);
}

std::tuple<ProcStatInfo, SystemSummaryStats> sampleProcStat(auto int64Multiplier,
                                                            auto uint64Multiplier,
                                                            auto uint32Multiplier) {
    ProcStatInfo procStatInfo{/*stats=*/{int64Multiplier(2'900), int64Multiplier(7'900),
                                         int64Multiplier(4'900), int64Multiplier(8'900),
                                         /*ioWaitTimeMillis=*/int64Multiplier(5'900),
                                         int64Multiplier(6'966), int64Multiplier(7'980), 0, 0,
                                         int64Multiplier(2'930)},
                              /*ctxtSwitches=*/uint64Multiplier(500),
                              /*runnableCnt=*/uint32Multiplier(100),
                              /*ioBlockedCnt=*/uint32Multiplier(57),
                              /*kernelStartTimeEpochSeconds=*/0};
    SystemSummaryStats systemSummaryStats{/*cpuIoWaitTimeMillis=*/int64Multiplier(5'900),
                                          /*cpuIdleTimeMillis=*/int64Multiplier(8'900),
                                          /*totalCpuTimeMillis=*/int64Multiplier(48'376),
                                          /*totalCpuCycles=*/64'000,
                                          /*contextSwitchesCount=*/uint64Multiplier(500),
                                          /*ioBlockedProcessCount=*/uint32Multiplier(57),
                                          /*totalProcessCount=*/uint32Multiplier(157)};
    return std::make_tuple(procStatInfo, systemSummaryStats);
}

std::tuple<PressureLevelTransitions, PressureLevelDurations> samplePressureLevels(
        int advanceUptimeSec = 1) {
    PressureLevelTransitions pressureLevelTransitions{
            PressureLevelDurationPair{PressureMonitor::PRESSURE_LEVEL_NONE, 100 * advanceUptimeSec},
            PressureLevelDurationPair{PressureMonitor::PRESSURE_LEVEL_HIGH, 200 * advanceUptimeSec},
            PressureLevelDurationPair{PressureMonitor::PRESSURE_LEVEL_HIGH, 100 * advanceUptimeSec},
            PressureLevelDurationPair{PressureMonitor::PRESSURE_LEVEL_LOW, 200 * advanceUptimeSec},
            PressureLevelDurationPair{PressureMonitor::PRESSURE_LEVEL_MEDIUM,
                                      100 * advanceUptimeSec},
            PressureLevelDurationPair{PressureMonitor::PRESSURE_LEVEL_LOW, 200 * advanceUptimeSec},
            PressureLevelDurationPair{PressureMonitor::PRESSURE_LEVEL_MEDIUM,
                                      100 * advanceUptimeSec},
    };
    PressureLevelDurations pressureLevelDurations{
            {PressureMonitor::PRESSURE_LEVEL_NONE, 100ms * advanceUptimeSec},
            {PressureMonitor::PRESSURE_LEVEL_LOW, 400ms * advanceUptimeSec},
            {PressureMonitor::PRESSURE_LEVEL_MEDIUM, 200ms * advanceUptimeSec},
            {PressureMonitor::PRESSURE_LEVEL_HIGH, 300ms * advanceUptimeSec},
    };
    return std::make_tuple(pressureLevelTransitions, pressureLevelDurations);
}

ResourceStats getResourceStatsForSampledStats(auto int32Multiplier, auto int64Multiplier,
                                              time_point_millis nowMillis,
                                              int64_t elapsedRealtimeSinceBootMillis) {
    // clang-format off
    return {
        .resourceUsageStats = std::make_optional<ResourceUsageStats>({
            .startTimeEpochMillis = nowMillis.time_since_epoch().count(),
            // Set durationInMillis to zero since this field is set by WatchdogPerfService.
            .durationInMillis = 0,
            .systemSummaryUsageStats = {
                .cpuNonIdleCycles = 64'000,
                .cpuNonIdleTimeMillis = int32Multiplier(39'476),
                .cpuIdleTimeMillis = int32Multiplier(8'900),
                .contextSwitchesCount = int32Multiplier(500),
                .ioBlockedProcessCount = int32Multiplier(57),
                .totalProcessCount = int32Multiplier(157),
                .totalMajorPageFaults = int32Multiplier(84'345),
                .totalIoReads = {
                    .foregroundBytes = int32Multiplier(1'000),
                    .backgroundBytes = int32Multiplier(21'600),
                    .garageModeBytes = 0,
                },
                .totalIoWrites = {
                    .foregroundBytes = int32Multiplier(300),
                    .backgroundBytes = int32Multiplier(28'300),
                    .garageModeBytes = 0,
                },
            },
            .uidResourceUsageStats = {
                {
                    .packageIdentifier = {
                        .name = "mount",
                        .uid = 1009,
                    },
                    .uidUptimeMillis = elapsedRealtimeSinceBootMillis - 234,
                    .cpuUsageStats = {
                        .cpuTimeMillis = int64Multiplier(50),
                        .cpuCycles = 4'000,
                        .cpuTimePercentage = (50. / 48'376.) * 100.0,
                    },
                    .processCpuUsageStats = {
                        {
                            .pid = 100,
                            .name = "disk I/O",
                            .cpuTimeMillis = int64Multiplier(50),
                            .cpuCycles = 4'000,
                        },
                    },
                    .ioUsageStats = {
                        .writtenBytes = {
                            .foregroundBytes = 0,
                            .backgroundBytes = int32Multiplier(16'000),
                            .garageModeBytes = 0,
                        },
                        .readBytes = {
                            .foregroundBytes = 0,
                            .backgroundBytes = int32Multiplier(14'000),
                            .garageModeBytes = 0,
                        },
                    },
                },
                {
                    .packageIdentifier = {
                        .name = "com.google.android.car.kitchensink",
                        .uid = 1002001,
                    },
                    .uidUptimeMillis = elapsedRealtimeSinceBootMillis - 467,
                    .cpuUsageStats = {
                        .cpuTimeMillis = int64Multiplier(60),
                        .cpuCycles = 10'000,
                        .cpuTimePercentage = (60. / 48'376.) * 100.0,
                    },
                    .processCpuUsageStats = {
                        {
                            .pid = 1001,
                            .name = "CTS",
                            .cpuTimeMillis = int64Multiplier(30),
                            .cpuCycles = 5'000,
                        },
                        {
                            .pid = 1000,
                            .name = "KitchenSinkApp",
                            .cpuTimeMillis = int64Multiplier(25),
                            .cpuCycles = 4'000,
                        },
                    },
                    .ioUsageStats = {
                        .writtenBytes = {
                            .foregroundBytes = 0,
                            .backgroundBytes = int32Multiplier(6'700),
                            .garageModeBytes = 0,
                        },
                        .readBytes = {
                            .foregroundBytes = 0,
                            .backgroundBytes = int32Multiplier(3'400),
                            .garageModeBytes = 0,
                        },
                    },
                },
                {
                    .packageIdentifier = {
                        .name = "1012345",
                        .uid = 1012345,
                    },
                    .uidUptimeMillis = elapsedRealtimeSinceBootMillis - 6789,
                    .cpuUsageStats = {
                        .cpuTimeMillis = int64Multiplier(100),
                        .cpuCycles = 50'000,
                        .cpuTimePercentage = (100. / 48'376.) * 100.0,
                    },
                    .processCpuUsageStats = {
                        {
                            .pid = 2345,
                            .name = "MapsApp",
                            .cpuTimeMillis = int64Multiplier(100),
                            .cpuCycles = 50'000,
                        },
                    },
                    .ioUsageStats = {
                        .writtenBytes = {
                            .foregroundBytes = int32Multiplier(300),
                            .backgroundBytes = int32Multiplier(5'600),
                            .garageModeBytes = 0,
                        },
                        .readBytes = {
                            .foregroundBytes = int32Multiplier(1'000),
                            .backgroundBytes = int32Multiplier(4'200),
                            .garageModeBytes = 0,
                        },
                    },
                },
                {
                    .packageIdentifier = {
                        .name = "com.google.radio",
                        .uid = 1015678,
                    },
                    .uidUptimeMillis = elapsedRealtimeSinceBootMillis - 10789,
                    .cpuUsageStats = {
                        .cpuTimeMillis = 0,
                        .cpuCycles = 0,
                        .cpuTimePercentage = 0,
                    },
                    .ioUsageStats = {
                        .writtenBytes = {
                            .foregroundBytes = 0,
                            .backgroundBytes = 0,
                            .garageModeBytes = 0,
                        },
                        .readBytes = {
                            .foregroundBytes = 0,
                            .backgroundBytes = 0,
                            .garageModeBytes = 0,
                        },
                    },
                },
            },
        }),
    };
    // clang-format on
}

struct StatsInfo {
    std::vector<UidStats> uidStats = {};
    UserPackageSummaryStats userPackageSummaryStats = {};
    ProcStatInfo procStatInfo = {};
    SystemSummaryStats systemSummaryStats = {};
    ResourceStats resourceStats = {};
};

MATCHER_P(UserPackageInfoProtoEq, expected, "") {
    return ExplainMatchResult(AllOf(Property("user_id", &UserPackageInfo::user_id,
                                             static_cast<int>(multiuser_get_user_id(expected.uid))),
                                    Property("package_name", &UserPackageInfo::package_name,
                                             expected.genericPackageName)),
                              arg, result_listener);
}

MATCHER_P(CpuStatsProtoEq, expected, "") {
    return ExplainMatchResult(AllOf(Property("cpu_time_millis",
                                             &PackageCpuStats_CpuStats::cpu_time_millis,
                                             expected.cpuTimeMillis),
                                    Property("cpu_cycles", &PackageCpuStats_CpuStats::cpu_cycles,
                                             expected.cpuCycles)),
                              arg, result_listener);
}

MATCHER_P(ProcessCpuStatsProtoEq, expected, "") {
    return ExplainMatchResult(AllOf(Property("command", &PackageCpuStats_ProcessCpuStats::command,
                                             expected.comm),
                                    Property("cpu_stats",
                                             &PackageCpuStats_ProcessCpuStats::cpu_stats,
                                             CpuStatsProtoEq(expected))),
                              arg, result_listener);
}

MATCHER_P(PackageCpuStatsProtoEq, expected, "") {
    const auto& uidCpustatsVariant =
            std::get_if<UserPackageStats::UidCpuStats>(&expected.statsVariant);
    std::vector<Matcher<const PackageCpuStats_ProcessCpuStats&>> processCpuStatsMatchers;
    for (const auto& expectedProcessCpuStats : uidCpustatsVariant->topNProcesses) {
        processCpuStatsMatchers.push_back(ProcessCpuStatsProtoEq(expectedProcessCpuStats));
    }
    return ExplainMatchResult(AllOf(Property("user_package_info",
                                             &PackageCpuStats::user_package_info,
                                             UserPackageInfoProtoEq(expected)),
                                    Property("cpu_stats", &PackageCpuStats::cpu_stats,
                                             CpuStatsProtoEq(*uidCpustatsVariant)),
                                    Property("process_cpu_stats",
                                             &PackageCpuStats::process_cpu_stats,
                                             ElementsAreArray(processCpuStatsMatchers))),
                              arg, result_listener);
}

MATCHER_P4(StorageIoStatsProtoEq, fgBytes, fgFsync, bgBytes, byFsync, "") {
    return ExplainMatchResult(AllOf(Property("fg_bytes", &StorageIoStats::fg_bytes, fgBytes),
                                    Property("fg_fsync", &StorageIoStats::fg_fsync, fgFsync),
                                    Property("bg_bytes", &StorageIoStats::bg_bytes, bgBytes),
                                    Property("bg_fsync", &StorageIoStats::bg_fsync, byFsync)),
                              arg, result_listener);
}

MATCHER_P(PackageStorageIoStatsProtoEq, expected, "") {
    const auto& uidIoSingleOpStats =
        std::get_if<UserPackageStats::UidIoSingleOpStats>(&expected.statsVariant);
    return ExplainMatchResult(AllOf(
              Property("user_package_info",
                       &PackageStorageIoStats::user_package_info,
                       UserPackageInfoProtoEq(expected)),
              Property("storage_io_stats",
                       &PackageStorageIoStats::storage_io_stats,
                       StorageIoStatsProtoEq(uidIoSingleOpStats->bytes[FOREGROUND],
                                             uidIoSingleOpStats->fsync[FOREGROUND],
                                             uidIoSingleOpStats->bytes[BACKGROUND],
                                             uidIoSingleOpStats
                                                     ->fsync[BACKGROUND]))),
        arg, result_listener);
}

MATCHER_P(ProcessTaskStateStatsProtoEq, expected, "") {
    return ExplainMatchResult(AllOf(Property("command",
                                             &PackageTaskStateStats_ProcessTaskStateStats::command,
                                             expected.comm),
                                    Property("io_blocked_task_count",
                                             &PackageTaskStateStats_ProcessTaskStateStats::
                                                     io_blocked_task_count,
                                             expected.value)),
                              arg, result_listener);
}

MATCHER_P2(PackageTaskStateStatsProtoEq, expected, taskCountByUid, "") {
    const auto& uidSingleStats =
            std::get_if<UserPackageStats::UidSingleStats>(&expected.statsVariant);
    std::vector<Matcher<const PackageTaskStateStats_ProcessTaskStateStats&>>
            processTaskStateStatsMatchers;
    for (const auto& expectedProcessSingleStats : uidSingleStats->topNProcesses) {
        processTaskStateStatsMatchers.push_back(
            ProcessTaskStateStatsProtoEq(expectedProcessSingleStats));
    }
    return ExplainMatchResult(AllOf(Property("user_package_info",
                                             &PackageTaskStateStats::user_package_info,
                                             UserPackageInfoProtoEq(expected)),
                                    Property("io_blocked_task_count",
                                             &PackageTaskStateStats::io_blocked_task_count,
                                             uidSingleStats->value),
                                    Property("total_task_count",
                                             &PackageTaskStateStats::total_task_count,
                                             taskCountByUid.at(expected.uid)),
                                    Property("process_task_state_stats",
                                             &PackageTaskStateStats::process_task_state_stats,
                                             ElementsAreArray(processTaskStateStatsMatchers))),
                              arg, result_listener);
}

MATCHER_P(PackageMajorPageFaultsProtoEq, expected, "") {
    const auto& uidSingleStats =
            std::get_if<UserPackageStats::UidSingleStats>(&expected.statsVariant);
    return ExplainMatchResult(AllOf(Property("user_package_info",
                                             &PackageMajorPageFaults::user_package_info,
                                             UserPackageInfoProtoEq(expected)),
                                    Property("major_page_faults_count",
                                             &PackageMajorPageFaults::major_page_faults_count,
                                             uidSingleStats->value)),
                              arg, result_listener);
}

MATCHER_P(DateProtoEq, expected, "") {
    return ExplainMatchResult(AllOf(Property("year", &Date::year, expected.tm_year + 1900),
                                    Property("month", &Date::month, expected.tm_mon),
                                    Property("day", &Date::day, expected.tm_mday)),
                              arg, result_listener);
}

MATCHER_P2(TimeOfDayProtoEq, expected, nowTimeMs, "") {
    return ExplainMatchResult(AllOf(Property("hours", &TimeOfDay::hours, expected.tm_hour),
                                    Property("minutes", &TimeOfDay::minutes, expected.tm_min),
                                    Property("seconds", &TimeOfDay::seconds, expected.tm_sec),
                                    Property("millis", &TimeOfDay::millis, nowTimeMs.count())),
                              arg, result_listener);
}

MATCHER_P2(SystemWideStatsProtoEq, userPackageSummaryStats, systemSummaryStats, "") {
    return ExplainMatchResult(AllOf(Property("io_wait_time_millis",
                                             &SystemWideStats::io_wait_time_millis,
                                             systemSummaryStats.cpuIoWaitTimeMillis),
                                    Property("idle_cpu_time_millis",
                                             &SystemWideStats::idle_cpu_time_millis,
                                             systemSummaryStats.cpuIdleTimeMillis),
                                    Property("total_cpu_time_millis",
                                             &SystemWideStats::total_cpu_time_millis,
                                             systemSummaryStats.totalCpuTimeMillis),
                                    Property("total_cpu_cycles", &SystemWideStats::total_cpu_cycles,
                                             systemSummaryStats.totalCpuCycles),
                                    Property("total_context_switches",
                                             &SystemWideStats::total_context_switches,
                                             systemSummaryStats.contextSwitchesCount),
                                    Property("total_io_blocked_processes",
                                             &SystemWideStats::total_io_blocked_processes,
                                             systemSummaryStats.ioBlockedProcessCount),
                                    Property("total_major_page_faults",
                                             &SystemWideStats::total_major_page_faults,
                                             userPackageSummaryStats.totalMajorFaults),
                                    Property("total_storage_io_stats",
                                             &SystemWideStats::total_storage_io_stats,
                                             StorageIoStatsProtoEq(userPackageSummaryStats
                                                                           .totalIoStats
                                                                                   [WRITE_BYTES]
                                                                                   [FOREGROUND],
                                                                   userPackageSummaryStats
                                                                           .totalIoStats
                                                                                   [FSYNC_COUNT]
                                                                                   [FOREGROUND],
                                                                   userPackageSummaryStats
                                                                           .totalIoStats
                                                                                   [WRITE_BYTES]
                                                                                   [BACKGROUND],
                                                                   userPackageSummaryStats
                                                                           .totalIoStats
                                                                                   [FSYNC_COUNT]
                                                                                   [BACKGROUND]))),
                              arg, result_listener);
}

MATCHER_P3(StatsRecordProtoEq, userPackageSummaryStats, systemSummaryStats, nowMs, "") {
    struct tm timeinfo;
    memset(&timeinfo, 0, sizeof(timeinfo));
    auto dateTime = std::chrono::system_clock::to_time_t(nowMs);
    auto nowTimeMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            nowMs - std::chrono::system_clock::from_time_t(dateTime));
    localtime_r(&dateTime, &timeinfo);

    std::vector<Matcher<const PackageCpuStats&>> packageCpuStatsMatchers;
    for (const auto& expectedUidCpuStats : userPackageSummaryStats.topNCpuTimes) {
        packageCpuStatsMatchers.push_back(PackageCpuStatsProtoEq(expectedUidCpuStats));
    }
    std::vector<Matcher<const PackageStorageIoStats&>> packageStorageReadIoStatsMatchers;
    for (const auto& expectedPackageStorageReadIoStat : userPackageSummaryStats.topNIoReads) {
        packageStorageReadIoStatsMatchers.push_back(
                PackageStorageIoStatsProtoEq(expectedPackageStorageReadIoStat));
    }
    std::vector<Matcher<const PackageStorageIoStats&>> packageStorageWriteIoStatsMatchers;
    for (const auto& expectedPackageStorageWriteIoStat : userPackageSummaryStats.topNIoWrites) {
        packageStorageWriteIoStatsMatchers.push_back(
                PackageStorageIoStatsProtoEq(expectedPackageStorageWriteIoStat));
    }
    std::vector<Matcher<const PackageTaskStateStats&>> packageTaskStateStatsMatchers;
    for (const auto& expectedPackageTaskStateStat : userPackageSummaryStats.topNIoBlocked) {
        packageTaskStateStatsMatchers.push_back(
                PackageTaskStateStatsProtoEq(expectedPackageTaskStateStat,
                                             userPackageSummaryStats.taskCountByUid));
    }
    std::vector<Matcher<const PackageMajorPageFaults&>> packageMajorPageFaultsMatchers;
    for (const auto& expectedPackageMajorPageFault : userPackageSummaryStats.topNMajorFaults) {
        packageMajorPageFaultsMatchers.push_back(
                PackageMajorPageFaultsProtoEq(expectedPackageMajorPageFault));
    }
    return ExplainMatchResult(AllOf(Property("date", &StatsRecord::date, DateProtoEq(timeinfo)),
                                    Property("time", &StatsRecord::time,
                                             TimeOfDayProtoEq(timeinfo, nowTimeMs)),
                                    Property("system_wide_stats", &StatsRecord::system_wide_stats,
                                             SystemWideStatsProtoEq(userPackageSummaryStats,
                                                                    systemSummaryStats)),
                                    Property("package_cpu_stats", &StatsRecord::package_cpu_stats,
                                             ElementsAreArray(packageCpuStatsMatchers)),
                                    Property("package_storage_io_read_stats",
                                             &StatsRecord::package_storage_io_read_stats,
                                             ElementsAreArray(packageStorageReadIoStatsMatchers)),
                                    Property("package_storage_io_read_stats",
                                             &StatsRecord::package_storage_io_write_stats,
                                             ElementsAreArray(packageStorageWriteIoStatsMatchers)),
                                    Property("package_task_state_stats",
                                             &StatsRecord::package_task_state_stats,
                                             ElementsAreArray(packageTaskStateStatsMatchers)),
                                    Property("package_major_page_faults",
                                             &StatsRecord::package_major_page_faults,
                                             ElementsAreArray(packageMajorPageFaultsMatchers))),
                              arg, result_listener);
}

std::string toString(util::ProtoOutputStream* proto) {
    std::string content;
    content.reserve(proto->size());
    sp<ProtoReader> reader = proto->data();
    while (reader->hasNext()) {
        content.push_back(reader->next());
    }
    return content;
}

std::string toString(const std::vector<UserSwitchCollectionInfo>& infos) {
    std::string buffer;
    StringAppendF(&buffer, "{");
    for (const auto& info : infos) {
        StringAppendF(&buffer, "%s\n", info.toString().c_str());
    }
    StringAppendF(&buffer, "}");
    return buffer;
}

}  // namespace

namespace internal {

// TODO(b/289396065): Refactor class such that variable fields are initialized directly in the
// constructor and remove the setter methods.
class PerformanceProfilerPeer final : public RefBase {
public:
    explicit PerformanceProfilerPeer(sp<PerformanceProfiler> collector) : mCollector(collector) {}

    PerformanceProfilerPeer() = delete;
    ~PerformanceProfilerPeer() {
        mCollector->terminate();
        mCollector.clear();
    }

    Result<void> init(int topNStatsPerCategory, int topNStatsPerSubcategory,
                      int maxUserSwitchEvents, std::chrono::seconds systemEventDataCacheDuration,
                      size_t bufferSize, bool doSendResourceUsageStatsEnabled,
                      bool isSmapsRollupSupported) {
        if (auto result = mCollector->init(); !result.ok()) {
            return result;
        }

        mCollector->mTopNStatsPerCategory = topNStatsPerCategory;
        mCollector->mTopNStatsPerSubcategory = topNStatsPerSubcategory;
        mCollector->mMaxUserSwitchEvents = maxUserSwitchEvents;
        mCollector->mSystemEventDataCacheDurationSec = systemEventDataCacheDuration;
        mCollector->mPeriodicCollection.maxCacheSize = bufferSize;
        mCollector->mDoSendResourceUsageStats = doSendResourceUsageStatsEnabled;
        mCollector->mIsSmapsRollupSupported = isSmapsRollupSupported;

        return {};
    }

    void setTopNStatsPerCategory(int value) { mCollector->mTopNStatsPerCategory = value; }

    void setTopNStatsPerSubcategory(int value) { mCollector->mTopNStatsPerSubcategory = value; }

    void setSendResourceUsageStatsEnabled(bool enable) {
        mCollector->mDoSendResourceUsageStats = enable;
    }

    void setSmapsRollupSupportedEnabled(bool enable) {
        mCollector->mIsSmapsRollupSupported = enable;
    }

    const CollectionInfo& getBoottimeCollectionInfo() {
        Mutex::Autolock lock(mCollector->mMutex);
        return mCollector->mBoottimeCollection;
    }

    const CollectionInfo& getPeriodicCollectionInfo() {
        Mutex::Autolock lock(mCollector->mMutex);
        return mCollector->mPeriodicCollection;
    }

    const std::vector<UserSwitchCollectionInfo>& getUserSwitchCollectionInfos() {
        Mutex::Autolock lock(mCollector->mMutex);
        return mCollector->mUserSwitchCollections;
    }

    const CollectionInfo& getWakeUpCollectionInfo() {
        Mutex::Autolock lock(mCollector->mMutex);
        return mCollector->mWakeUpCollection;
    }

    const CollectionInfo& getCustomCollectionInfo() {
        Mutex::Autolock lock(mCollector->mMutex);
        return mCollector->mCustomCollection;
    }

private:
    sp<PerformanceProfiler> mCollector;
};

}  // namespace internal

class PerformanceProfilerTest : public Test {
protected:
    void SetUp() override {
        mPeriodicCollectionBufferSize =
                static_cast<size_t>(sysprop::periodicCollectionBufferSize().value_or(
                        kDefaultPeriodicCollectionBufferSize));
        mElapsedRealtimeSinceBootMillis = kTestElapsedRealtimeSinceBootMillis;
        mNowMillis = kTestNowMillis;
        mMockUidStatsCollector = sp<MockUidStatsCollector>::make();
        mMockPressureMonitor = sp<MockPressureMonitor>::make();
        mMockProcStatCollector = sp<MockProcStatCollector>::make();
        mCollector = sp<PerformanceProfiler>::
                make(mMockPressureMonitor,
                     std::bind(&PerformanceProfilerTest::getTestElapsedRealtimeSinceBootMs, this));
        mCollectorPeer = sp<internal::PerformanceProfilerPeer>::make(mCollector);

        EXPECT_CALL(*mMockPressureMonitor, registerPressureChangeCallback(Eq(mCollector)))
                .Times(car_watchdog_memory_profiling() ? 1 : 0);

        ASSERT_RESULT_OK(
                mCollectorPeer->init(kTestTopNStatsPerCategory, kTestTopNStatsPerSubcategory,
                                     kTestMaxUserSwitchEvents, kTestSystemEventDataCacheDurationSec,
                                     kTestPeriodicCollectionBufferSize,
                                     /*doSendResourceUsageStatsEnabled=*/true,
                                     /*isSmapsRollupSupported=*/true));
    }

    void TearDown() override {
        mMockUidStatsCollector.clear();
        mMockProcStatCollector.clear();

        EXPECT_CALL(*mMockPressureMonitor, unregisterPressureChangeCallback(Eq(mCollector)))
                .Times(car_watchdog_memory_profiling() ? 1 : 0);

        mCollector.clear();
        mCollectorPeer.clear();
    }

    int64_t getTestElapsedRealtimeSinceBootMs() { return mElapsedRealtimeSinceBootMillis; }

protected:
    void checkDumpContents(int wantedEmptyCollectionInstances) {
        TemporaryFile dump;
        ASSERT_RESULT_OK(mCollector->onDump(dump.fd));

        checkDumpFd(wantedEmptyCollectionInstances, dump.fd);
    }

    void checkCustomDumpContents() {
        TemporaryFile dump;
        ASSERT_RESULT_OK(mCollector->onCustomCollectionDump(dump.fd));

        checkDumpFd(/*wantedEmptyCollectionInstances=*/0, dump.fd);
    }

    PressureLevelDurations injectPressureLevelTransitions(int advanceUptimeSec) {
        if (!car_watchdog_memory_profiling()) {
            mElapsedRealtimeSinceBootMillis += advanceUptimeSec * 1000;
            return PressureLevelDurations{};
        }
        auto [pressureLevelTransitions, pressureLevelDurations] =
                samplePressureLevels(advanceUptimeSec);
        for (const auto transition : pressureLevelTransitions) {
            mElapsedRealtimeSinceBootMillis += transition.second;
            mCollector->onPressureChanged(transition.first);
        }
        return pressureLevelDurations;
    }

    // Direct use of this method in tests is not recommended because further setup (such as calling
    // injectPressureLevelTransitions, constructing CollectionInfo struct, advancing time, and
    // setting up EXPECT_CALL) is required before testing a collection. Please consider using one of
    // the PerformanceProfilerTest::setup* methods instead. If none of them work for a new use case,
    // either update the existing PerformanceProfilerTest::setup* methods or add a new
    // PerformanceProfilerTest::setup* method.
    StatsInfo getSampleStatsInfo(int multiplier = 1,
                                 bool isSmapsRollupSupported = kTestIsSmapsRollupSupported) {
        const auto int64Multiplier = [&](int64_t bytes) -> int64_t {
            return static_cast<int64_t>(bytes * multiplier);
        };
        const auto uint64Multiplier = [&](uint64_t count) -> uint64_t {
            return static_cast<uint64_t>(count * multiplier);
        };
        const auto int32Multiplier = [&](int32_t bytes) -> int32_t {
            return static_cast<int32_t>(bytes * multiplier);
        };
        const auto uint32Multiplier = [&](uint32_t bytes) -> uint32_t {
            return static_cast<uint32_t>(bytes * multiplier);
        };

        auto [uidStats, userPackageSummaryStats] =
                sampleUidStats(int64Multiplier, uint64Multiplier, isSmapsRollupSupported);

        applyFeatureFilter(&userPackageSummaryStats);

        auto [procStatInfo, systemSummaryStats] =
                sampleProcStat(int64Multiplier, uint64Multiplier, uint32Multiplier);

        ResourceStats resourceStats =
                getResourceStatsForSampledStats(int32Multiplier, int64Multiplier, mNowMillis,
                                                mElapsedRealtimeSinceBootMillis);

        StatsInfo statsInfo(uidStats, userPackageSummaryStats, procStatInfo, systemSummaryStats,
                            resourceStats);
        return statsInfo;
    }

    void advanceTime(int durationMillis) {
        mNowMillis += std::chrono::milliseconds(durationMillis);
    }

    std::tuple<CollectionInfo, ResourceStats> setupFirstCollection(
            size_t maxCollectionCacheSize = std::numeric_limits<std::size_t>::max(),
            bool isSmapsRollupSupported = kTestIsSmapsRollupSupported) {
        // Trigger pressure level transitions to test the pressure level accounting done by the
        // implementation.
        auto pressureLevelDurations = injectPressureLevelTransitions(/*advanceUptimeSec=*/1);
        auto statsInfo = getSampleStatsInfo(/*multiplier=*/1, isSmapsRollupSupported);

        EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(statsInfo.uidStats));
        EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(statsInfo.procStatInfo));

        auto expectedCollectionInfo =
                CollectionInfo{.maxCacheSize = maxCollectionCacheSize,
                               .records = {{
                                       .collectionTimeMillis = mNowMillis,
                                       .systemSummaryStats = statsInfo.systemSummaryStats,
                                       .userPackageSummaryStats = statsInfo.userPackageSummaryStats,
                                       .memoryPressureLevelDurations = pressureLevelDurations,
                               }}};
        auto expectedResourceStats = statsInfo.resourceStats;
        return std::make_tuple(expectedCollectionInfo, expectedResourceStats);
    }

    void setupNextCollection(CollectionInfo* prevCollectionInfo, ResourceStats* outResourceStats,
                             int multiplier = 1) {
        advanceTime(/*durationMillis=*/1000);
        // Trigger pressure level transitions to test the pressure level accounting done by the
        // implementation.
        auto pressureLevelDurations = injectPressureLevelTransitions(/*advanceUptimeSec=*/1);
        auto statsInfo = getSampleStatsInfo(multiplier, kTestIsSmapsRollupSupported);

        EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(statsInfo.uidStats));
        EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(statsInfo.procStatInfo));

        auto& prevRecord = prevCollectionInfo->records.back();
        statsInfo.userPackageSummaryStats.majorFaultsPercentChange =
                (static_cast<double>(statsInfo.userPackageSummaryStats.totalMajorFaults -
                                     prevRecord.userPackageSummaryStats.totalMajorFaults) /
                 static_cast<double>(prevRecord.userPackageSummaryStats.totalMajorFaults)) *
                100.0;

        prevCollectionInfo->records.push_back(PerfStatsRecord{
                .collectionTimeMillis = mNowMillis,
                .systemSummaryStats = statsInfo.systemSummaryStats,
                .userPackageSummaryStats = statsInfo.userPackageSummaryStats,
                .memoryPressureLevelDurations = pressureLevelDurations,
        });
        *outResourceStats = statsInfo.resourceStats;
    }

    UserSwitchCollectionInfo setupUserSwitchCollection(userid_t fromUserId, userid_t toUserId) {
        auto [collectionInfo, _] = setupFirstCollection();
        return UserSwitchCollectionInfo{
                collectionInfo,
                .from = fromUserId,
                .to = toUserId,
        };
    }

    // Use this method only in tests where the returned CollectionInfo / UserSwitchCollectionInfo
    // is not verified.
    void setupMultipleCollections() {
        auto statsInfo = getSampleStatsInfo();

        EXPECT_CALL(*mMockUidStatsCollector, deltaStats())
                .WillRepeatedly(Return(statsInfo.uidStats));
        EXPECT_CALL(*mMockProcStatCollector, deltaStats())
                .WillRepeatedly(Return(statsInfo.procStatInfo));
    }

    time_point_millis getNowMillis() { return mNowMillis; }

private:
    void checkDumpFd(int wantedEmptyCollectionInstances, int fd) {
        lseek(fd, 0, SEEK_SET);
        std::string dumpContents;
        ASSERT_TRUE(ReadFdToString(fd, &dumpContents));
        ASSERT_FALSE(dumpContents.empty());

        ASSERT_EQ(countOccurrences(dumpContents, kEmptyCollectionMessage),
                  wantedEmptyCollectionInstances)
                << "Dump contents: " << dumpContents;
    }

protected:
    size_t mPeriodicCollectionBufferSize;
    sp<MockUidStatsCollector> mMockUidStatsCollector;
    sp<MockPressureMonitor> mMockPressureMonitor;
    sp<MockProcStatCollector> mMockProcStatCollector;
    sp<PerformanceProfiler> mCollector;
    sp<internal::PerformanceProfilerPeer> mCollectorPeer;

private:
    int64_t mElapsedRealtimeSinceBootMillis;
    time_point_millis mNowMillis;
};

TEST_F(PerformanceProfilerTest, TestOnBoottimeCollection) {
    auto [expectedCollectionInfo, expectedResourceStats] = setupFirstCollection();

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onBoottimeCollection(getNowMillis(), mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    const auto actualCollectionInfo = mCollectorPeer->getBoottimeCollectionInfo();

    EXPECT_THAT(actualCollectionInfo, CollectionInfoEq(expectedCollectionInfo))
            << "Boottime collection info doesn't match.\nExpected:\n"
            << expectedCollectionInfo.toString() << "\nActual:\n"
            << actualCollectionInfo.toString();

    ASSERT_EQ(actualResourceStats, expectedResourceStats)
            << "Expected: " << expectedResourceStats.toString()
            << "\nActual: " << actualResourceStats.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Periodic, wake-up and user-switch collections shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestOnWakeUpCollection) {
    auto [expectedCollectionInfo, expectedResourceStats] = setupFirstCollection();

    ASSERT_RESULT_OK(mCollector->onWakeUpCollection(getNowMillis(), mMockUidStatsCollector,
                                                    mMockProcStatCollector));

    const auto actualCollectionInfo = mCollectorPeer->getWakeUpCollectionInfo();

    EXPECT_THAT(actualCollectionInfo, CollectionInfoEq(expectedCollectionInfo))
            << "Wake-up collection info doesn't match.\nExpected:\n"
            << expectedCollectionInfo.toString() << "\nActual:\n"
            << actualCollectionInfo.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Boot-time, periodic, and user-switch collections shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestOnSystemStartup) {
    setupMultipleCollections();

    ResourceStats resourceStats = {};
    ASSERT_RESULT_OK(mCollector->onBoottimeCollection(getNowMillis(), mMockUidStatsCollector,
                                                      mMockProcStatCollector, &resourceStats));
    ASSERT_RESULT_OK(mCollector->onWakeUpCollection(getNowMillis(), mMockUidStatsCollector,
                                                    mMockProcStatCollector));

    auto actualBoottimeCollection = mCollectorPeer->getBoottimeCollectionInfo();
    auto actualWakeUpCollection = mCollectorPeer->getWakeUpCollectionInfo();

    EXPECT_THAT(actualBoottimeCollection.records.size(), 1)
            << "Boot-time collection records is empty.";
    EXPECT_THAT(actualWakeUpCollection.records.size(), 1) << "Wake-up collection records is empty.";

    ASSERT_RESULT_OK(mCollector->onSystemStartup());

    actualBoottimeCollection = mCollectorPeer->getBoottimeCollectionInfo();
    actualWakeUpCollection = mCollectorPeer->getWakeUpCollectionInfo();

    EXPECT_THAT(actualBoottimeCollection.records.size(), 0)
            << "Boot-time collection records is not empty.";
    EXPECT_THAT(actualWakeUpCollection.records.size(), 0)
            << "Wake-up collection records is not empty.";
}

TEST_F(PerformanceProfilerTest, TestOnUserSwitchCollection) {
    std::vector<UserSwitchCollectionInfo> expected;
    expected.push_back(setupUserSwitchCollection(kTestBaseUserId, kTestBaseUserId + 1));

    ASSERT_RESULT_OK(mCollector->onUserSwitchCollection(getNowMillis(), kTestBaseUserId,
                                                        kTestBaseUserId + 1, mMockUidStatsCollector,
                                                        mMockProcStatCollector));

    auto actual = mCollectorPeer->getUserSwitchCollectionInfos();

    EXPECT_THAT(actual, UserSwitchCollectionsEq(expected))
            << "User switch collection infos doesn't match.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);

    // Continuation of the previous user switch collection
    std::vector<UidStats> nextUidStats = {
            {.packageInfo = constructPackageInfo("mount", 1009),
             .cpuTimeMillis = 0,  // No TopNCpuTimes will be registered
             .ioStats = {/*fgRdBytes=*/0,
                         /*bgRdBytes=*/5'000,
                         /*fgWrBytes=*/0,
                         /*bgWrBytes=*/3'000,
                         /*fgFsync=*/0, /*bgFsync=*/50},
             .procStats = {.cpuTimeMillis = 50,
                           .cpuCycles = 3'500,
                           .totalMajorFaults = 6'000,
                           .totalTasksCount = 1,
                           .ioBlockedTasksCount = 2,
                           .processStatsByPid = {{/*pid=*/100,
                                                  {/*comm=*/"disk I/O", /*startTime=*/234,
                                                   /*cpuTimeMillis=*/50,
                                                   /*totalCpuCycle=*/3'500,
                                                   /*totalMajorFaults=*/6'000,
                                                   /*totalTasksCount=*/1,
                                                   /*ioBlockedTasksCount=*/2,
                                                   /*cpuCyclesByTid=*/{{100, 3'500}}}}}}}};

    UserPackageSummaryStats nextUserPackageSummaryStats = {
            .topNIoReads = {{1009, "mount",
                             UserPackageStats::UidIoSingleOpStats{{0, 5'000},
                                                              {0, 50}}}},
            .topNIoWrites = {{1009, "mount",
                              UserPackageStats::UidIoSingleOpStats{{0, 3'000},
                                                               {0, 50}}}},
            .topNIoBlocked = {{1009, "mount",
                               UserPackageStats::UidSingleStats{2, {{"disk I/O", 2}}}}},
            .topNMajorFaults = {{1009, "mount",
                                 UserPackageStats::UidSingleStats{6'000,
                                                                       {{"disk I/O", 6'000}}}}},
            .totalIoStats = {{0, 5'000}, {0, 3'000}, {0, 50}},
            .taskCountByUid = {{1009, 1}},
            .totalCpuTimeMillis = 48'376,
            .totalCpuCycles = 3'500,
            .totalMajorFaults = 6'000,
            .majorFaultsPercentChange = (6'000.0 - 84'345.0) / 84'345.0 * 100.0,
    };

    // TODO(b/336835345): Revisit this test and update the below logic to use setupNextCollection
    //  instead.
    auto nextPressureLevelDurations = injectPressureLevelTransitions(/*advanceUptimeSec=*/2);
    advanceTime(/*durationMillis=*/2000);

    const auto statsInfo = getSampleStatsInfo();
    ProcStatInfo nextProcStatInfo = statsInfo.procStatInfo;
    SystemSummaryStats nextSystemSummaryStats = statsInfo.systemSummaryStats;

    nextProcStatInfo.contextSwitchesCount = 300;
    nextSystemSummaryStats.totalCpuCycles = 3'500;
    nextSystemSummaryStats.contextSwitchesCount = 300;

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(nextUidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(nextProcStatInfo));

    ASSERT_RESULT_OK(mCollector->onUserSwitchCollection(getNowMillis(), kTestBaseUserId,
                                                        kTestBaseUserId + 1, mMockUidStatsCollector,
                                                        mMockProcStatCollector));

    actual = mCollectorPeer->getUserSwitchCollectionInfos();

    expected[0].records.push_back(
            PerfStatsRecord{.collectionTimeMillis = getNowMillis(),
                            .systemSummaryStats = nextSystemSummaryStats,
                            .userPackageSummaryStats = nextUserPackageSummaryStats,
                            .memoryPressureLevelDurations = nextPressureLevelDurations});

    EXPECT_THAT(actual, UserSwitchCollectionsEq(expected))
            << "User switch collection info after continuation doesn't match.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Boot-time, wake-up and periodic collections shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestUserSwitchCollectionsMaxCacheSize) {
    std::vector<UserSwitchCollectionInfo> expected;
    userid_t userIdToTriggerEviction = kTestBaseUserId + kTestMaxUserSwitchEvents;

    for (userid_t userId = kTestBaseUserId; userId < userIdToTriggerEviction; ++userId) {
        expected.push_back(setupUserSwitchCollection(userId, userId + 1));
        ASSERT_RESULT_OK(mCollector->onUserSwitchCollection(getNowMillis(), userId, userId + 1,
                                                            mMockUidStatsCollector,
                                                            mMockProcStatCollector));
    }

    auto actual = mCollectorPeer->getUserSwitchCollectionInfos();

    EXPECT_THAT(actual.size(), kTestMaxUserSwitchEvents);

    EXPECT_THAT(actual, UserSwitchCollectionsEq(expected))
            << "User switch collection infos don't match before crossing limit.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);

    // Add new user switch event with max cache size. The oldest user switch event should be dropped
    // and the new one added to the cache.
    expected.push_back(
            setupUserSwitchCollection(userIdToTriggerEviction, userIdToTriggerEviction + 1));
    expected.erase(expected.begin());

    ASSERT_RESULT_OK(mCollector->onUserSwitchCollection(getNowMillis(), userIdToTriggerEviction,
                                                        userIdToTriggerEviction + 1,
                                                        mMockUidStatsCollector,
                                                        mMockProcStatCollector));

    actual = mCollectorPeer->getUserSwitchCollectionInfos();

    EXPECT_THAT(actual.size(), kTestMaxUserSwitchEvents);

    EXPECT_THAT(actual, UserSwitchCollectionsEq(expected))
            << "User switch collection infos don't match after crossing limit.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);
}

TEST_F(PerformanceProfilerTest, TestOnPeriodicCollection) {
    const auto [expectedCollectionInfo, expectedResourceStats] =
            setupFirstCollection(kTestPeriodicCollectionBufferSize);

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(getNowMillis(), SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    const auto actualCollectionInfo = mCollectorPeer->getPeriodicCollectionInfo();

    EXPECT_THAT(actualCollectionInfo, CollectionInfoEq(expectedCollectionInfo))
            << "Periodic collection info doesn't match.\nExpected:\n"
            << expectedCollectionInfo.toString() << "\nActual:\n"
            << actualCollectionInfo.toString();

    ASSERT_EQ(actualResourceStats, expectedResourceStats)
            << "Expected: " << expectedResourceStats.toString()
            << "\nActual: " << actualResourceStats.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Boot-time, wake-up and user-switch collections shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestOnPeriodicCollectionWithSendingUsageStatsDisabled) {
    mCollectorPeer->setSendResourceUsageStatsEnabled(false);

    auto [expectedCollectionInfo, _] = setupFirstCollection(kTestPeriodicCollectionBufferSize);

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(getNowMillis(), SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    const auto actualCollectionInfo = mCollectorPeer->getPeriodicCollectionInfo();
    const ResourceStats expectedResourceStats = {};

    EXPECT_THAT(actualCollectionInfo, CollectionInfoEq(expectedCollectionInfo))
            << "Periodic collection info doesn't match.\nExpected:\n"
            << expectedCollectionInfo.toString() << "\nActual:\n"
            << actualCollectionInfo.toString();

    ASSERT_EQ(actualResourceStats, expectedResourceStats)
            << "Expected: " << expectedResourceStats.toString()
            << "\nActual: " << actualResourceStats.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Boot-time, wake-up and user-switch collections shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestOnCustomCollectionWithoutPackageFilter) {
    auto [expectedCollectionInfo, expectedResourceStats] = setupFirstCollection();

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onCustomCollection(getNowMillis(), SystemState::NORMAL_MODE, {},
                                                    mMockUidStatsCollector, mMockProcStatCollector,
                                                    &actualResourceStats));

    const auto actualCollectionInfo = mCollectorPeer->getCustomCollectionInfo();

    EXPECT_THAT(actualCollectionInfo, CollectionInfoEq(expectedCollectionInfo))
            << "Custom collection info doesn't match.\nExpected:\n"
            << expectedCollectionInfo.toString() << "\nActual:\n"
            << actualCollectionInfo.toString();

    ASSERT_EQ(actualResourceStats, expectedResourceStats)
            << "Expected: " << expectedResourceStats.toString()
            << "\nActual: " << actualResourceStats.toString();

    ASSERT_NO_FATAL_FAILURE(checkCustomDumpContents()) << "Custom collection should be reported";

    TemporaryFile customDump;
    ASSERT_RESULT_OK(mCollector->onCustomCollectionDump(customDump.fd));

    // Should clear the cache.
    ASSERT_RESULT_OK(mCollector->onCustomCollectionDump(-1));

    expectedCollectionInfo.records.clear();
    const CollectionInfo& emptyCollectionInfo = mCollectorPeer->getCustomCollectionInfo();
    EXPECT_THAT(emptyCollectionInfo, CollectionInfoEq(expectedCollectionInfo))
            << "Custom collection should be cleared.";
}

TEST_F(PerformanceProfilerTest, TestOnCustomCollectionWithPackageFilter) {
    // Filter by package name should ignore this limit with package filter.
    mCollectorPeer->setTopNStatsPerCategory(1);

    auto [expectedCollectionInfo, expectedResourceStats] = setupFirstCollection();

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onCustomCollection(getNowMillis(), SystemState::NORMAL_MODE,
                                                    {"mount", "com.google.android.car.kitchensink"},
                                                    mMockUidStatsCollector, mMockProcStatCollector,
                                                    &actualResourceStats));
    const auto actualCollectionInfo = mCollectorPeer->getCustomCollectionInfo();

    UserPackageSummaryStats userPackageSummaryStats{
            .topNCpuTimes = {{1009, "mount",
                              UserPackageStats::UidCpuStats{50,
                                                                 4'000,
                                                                 {{100, "disk I/O", 50, 4'000}}}},
                             {1002001, "com.google.android.car.kitchensink",
                              UserPackageStats::UidCpuStats{60,
                                                                 10'000,
                                                                 {{1001, "CTS", 30, 5'000},
                                                                  {1000, "KitchenSinkApp", 25,
                                                                   4'000}}}}},
            .topNIoReads = {{1009, "mount",
                             UserPackageStats::UidIoSingleOpStats{{0, 14'000},
                                                              {0, 100}}},
                            {1002001, "com.google.android.car.kitchensink",
                             UserPackageStats::UidIoSingleOpStats{{0, 3'400}, {0, 200}}}},
            .topNIoWrites = {{1009, "mount",
                              UserPackageStats::UidIoSingleOpStats{{0, 16'000},
                                                               {0, 100}}},
                             {1002001, "com.google.android.car.kitchensink",
                              UserPackageStats::UidIoSingleOpStats{{0, 6'700}, {0, 200}}}},
            .topNIoBlocked =
                    {{1009, "mount", UserPackageStats::UidSingleStats{1, {{"disk I/O", 1}}}},
                     {1002001, "com.google.android.car.kitchensink",
                      UserPackageStats::UidSingleStats{3,
                                                            {{"CTS", 2}, {"KitchenSinkApp", 1}}}}},
            .topNMajorFaults = {{1009, "mount",
                                 UserPackageStats::UidSingleStats{11'000,
                                                                       {{"disk I/O", 11'000}}}},
                                {1002001, "com.google.android.car.kitchensink",
                                 UserPackageStats::UidSingleStats{22'445,
                                                                       {{"KitchenSinkApp", 12'345},
                                                                        {"CTS", 10'100}}}}},
            .topNMemStats =
                    {{1009, "mount",
                      UserPackageStats::UidMemoryStats{{/*rssKb=*/2010, /*pssKb=*/1635,
                                                        /*ussKb=*/1286, /*swapPssKb=*/600},
                                                       kTestIsSmapsRollupSupported,
                                                       {{"disk I/O",
                                                         {/*rssKb=*/2010, /*pssKb=*/1635,
                                                          /*ussKb=*/1286, /*swapPssKb=*/600}}}}},
                     {1002001, "com.google.android.car.kitchensink",
                      UserPackageStats::UidMemoryStats{{/*rssKb=*/2000, /*pssKb=*/1645,
                                                        /*ussKb=*/1286, /*swapPssKb=*/600},
                                                       kTestIsSmapsRollupSupported,
                                                       {{"KitchenSinkApp",
                                                         {/*rssKb=*/1000, /*pssKb=*/875,
                                                          /*ussKb=*/630, /*swapPssKb=*/400}},
                                                        {"CTS",
                                                         {/*rssKb=*/1000, /*pssKb=*/770,
                                                          /*ussKb=*/656, /*swapPssKb=*/200}}}}}},
            .totalIoStats = {{1000, 21'600}, {300, 28'300}, {600, 600}},
            .taskCountByUid = {{1009, 1}, {1002001, 5}},
            .totalCpuTimeMillis = 48'376,
            .totalCpuCycles = 64'000,
            .totalMajorFaults = 84'345,
            .totalRssKb = 5010,
            .totalPssKb = 4145,
            .majorFaultsPercentChange = 0.0,
    };
    applyFeatureFilter(&userPackageSummaryStats);
    expectedCollectionInfo.records[0].userPackageSummaryStats = userPackageSummaryStats;

    EXPECT_THAT(actualCollectionInfo, CollectionInfoEq(expectedCollectionInfo))
            << "Custom collection info doesn't match.\nExpected:\n"
            << expectedCollectionInfo.toString() << "\nActual:\n"
            << actualCollectionInfo.toString();

    ASSERT_EQ(actualResourceStats, expectedResourceStats)
            << "Expected: " << expectedResourceStats.toString()
            << "\nActual: " << actualResourceStats.toString();

    ASSERT_NO_FATAL_FAILURE(checkCustomDumpContents()) << "Custom collection should be reported";

    TemporaryFile customDump;
    ASSERT_RESULT_OK(mCollector->onCustomCollectionDump(customDump.fd));

    // Should clear the cache.
    ASSERT_RESULT_OK(mCollector->onCustomCollectionDump(-1));

    expectedCollectionInfo.records.clear();
    const CollectionInfo& emptyCollectionInfo = mCollectorPeer->getCustomCollectionInfo();
    EXPECT_THAT(emptyCollectionInfo, CollectionInfoEq(expectedCollectionInfo))
            << "Custom collection should be cleared.";
}

TEST_F(PerformanceProfilerTest, TestOnPeriodicCollectionWithTrimmingStatsAfterTopN) {
    mCollectorPeer->setTopNStatsPerCategory(1);
    mCollectorPeer->setTopNStatsPerSubcategory(1);

    auto [expectedCollectionInfo, expectedResourceStats] =
            setupFirstCollection(kTestPeriodicCollectionBufferSize);

    // Top N stats per category/sub-category is set to 1, so remove entries in the
    // expected value to match this.
    ASSERT_FALSE(expectedResourceStats.resourceUsageStats->uidResourceUsageStats.empty());
    UidResourceUsageStats& kitchenSinkStats =
            expectedResourceStats.resourceUsageStats->uidResourceUsageStats.at(1);
    ASSERT_FALSE(kitchenSinkStats.processCpuUsageStats.empty());
    kitchenSinkStats.processCpuUsageStats.pop_back();

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(getNowMillis(), SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    const auto actualCollectionInfo = mCollectorPeer->getPeriodicCollectionInfo();

    UserPackageSummaryStats userPackageSummaryStats{
            .topNCpuTimes = {{1012345, "1012345",
                              UserPackageStats::UidCpuStats{100,
                                                                 50'000,
                                                                 {{2345, "MapsApp", 100,
                                                                   50'000}}}}},
            .topNIoReads = {{1009, "mount",
                             UserPackageStats::UidIoSingleOpStats{{0, 14'000},
                                                              {0, 100}}}},
            .topNIoWrites = {{1009, "mount",
                              UserPackageStats::UidIoSingleOpStats{{0, 16'000},
                                                               {0, 100}}}},
            .topNIoBlocked = {{1002001, "com.google.android.car.kitchensink",
                               UserPackageStats::UidSingleStats{3, {{"CTS", 2}}}}},
            .topNMajorFaults = {{1012345, "1012345",
                                 UserPackageStats::UidSingleStats{50'900,
                                                                       {{"MapsApp", 50'900}}}}},
            .topNMemStats = {{1002001, "com.google.android.car.kitchensink",
                              UserPackageStats::UidMemoryStats{{/*rssKb=*/2000, /*pssKb=*/1645,
                                                                /*ussKb=*/1286, /*swapPssKb=*/600},
                                                               kTestIsSmapsRollupSupported,
                                                               {{"KitchenSinkApp",
                                                                 {/*rssKb=*/1000, /*pssKb=*/875,
                                                                  /*ussKb=*/630,
                                                                  /*swapPssKb=*/400}}}}}},
            .totalIoStats = {{1000, 21'600}, {300, 28'300}, {600, 600}},
            .taskCountByUid = {{1009, 1}, {1002001, 5}, {1012345, 4}},
            .totalCpuTimeMillis = 48'376,
            .totalCpuCycles = 64'000,
            .totalMajorFaults = 84'345,
            .totalRssKb = 5010,
            .totalPssKb = 4145,
            .majorFaultsPercentChange = 0.0,
    };
    applyFeatureFilter(&userPackageSummaryStats);
    expectedCollectionInfo.records[0].userPackageSummaryStats = userPackageSummaryStats;

    EXPECT_THAT(actualCollectionInfo, CollectionInfoEq(expectedCollectionInfo))
            << "Periodic collection info doesn't match.\nExpected:\n"
            << expectedCollectionInfo.toString() << "\nActual:\n"
            << actualCollectionInfo.toString();

    ASSERT_EQ(actualResourceStats, expectedResourceStats)
            << "Expected: " << expectedResourceStats.toString()
            << "\nActual: " << actualResourceStats.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Boot-time, wake-up and user-switch collections shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestConsecutiveOnPeriodicCollection) {
    auto [expectedCollectionInfo, expectedResourceStats] =
            setupFirstCollection(kTestPeriodicCollectionBufferSize);

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(getNowMillis(), SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    for (size_t i = 1; i < kTestPeriodicCollectionBufferSize; i++) {
        setupNextCollection(&expectedCollectionInfo, &expectedResourceStats, /*multiplier=*/2);

        ASSERT_RESULT_OK(mCollector->onPeriodicCollection(getNowMillis(), SystemState::NORMAL_MODE,
                                                          mMockUidStatsCollector,
                                                          mMockProcStatCollector,
                                                          &actualResourceStats));

        ASSERT_EQ(actualResourceStats, expectedResourceStats)
                << "Resource stats don't match for collection " << i
                << "\nExpected: " << expectedResourceStats.toString()
                << "\nActual: " << actualResourceStats.toString();
    }

    auto actualCollectionInfo = mCollectorPeer->getPeriodicCollectionInfo();

    EXPECT_THAT(actualCollectionInfo, CollectionInfoEq(expectedCollectionInfo))
            << "Periodic collection info doesn't match.\nExpected:\n"
            << expectedCollectionInfo.toString() << "\nActual:\n"
            << actualCollectionInfo.toString();

    // Collection beyond kTestPeriodicCollectionBufferSize should evict the first collection data.
    setupNextCollection(&expectedCollectionInfo, &expectedResourceStats, /*multiplier=*/2);
    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(getNowMillis(), SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    expectedCollectionInfo.records.erase(expectedCollectionInfo.records.begin());
    actualCollectionInfo = mCollectorPeer->getPeriodicCollectionInfo();

    EXPECT_THAT(actualCollectionInfo, CollectionInfoEq(expectedCollectionInfo))
            << "Periodic collection info doesn't match after exceeding cache limit.\nExpected:\n"
            << expectedCollectionInfo.toString() << "\nActual:\n"
            << actualCollectionInfo.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Boot-time, wake-up and user-switch collection shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestBoottimeCollectionCacheEvictionAfterTimeout) {
    setupMultipleCollections();

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onBoottimeCollection(getNowMillis(), mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    auto actualCollectionInfo = mCollectorPeer->getBoottimeCollectionInfo();

    EXPECT_THAT(actualCollectionInfo.records.size(), 1)
            << "Boot-time collection info missing after collection";

    advanceTime(/*durationMillis=*/kTestSystemEventDataCacheDurationSec.count() * 1000);

    // Call |onPeriodicCollection| 1 hour past the last boot-time collection event.
    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(getNowMillis(), SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    actualCollectionInfo = mCollectorPeer->getBoottimeCollectionInfo();

    EXPECT_THAT(actualCollectionInfo.records.empty(), true)
            << "Boot-time collection info records are not empty after cache eviction period";
}

TEST_F(PerformanceProfilerTest, TestWakeUpCollectionCacheEvictionAfterTimeout) {
    setupMultipleCollections();

    ASSERT_RESULT_OK(mCollector->onWakeUpCollection(getNowMillis(), mMockUidStatsCollector,
                                                    mMockProcStatCollector));

    auto actualCollectionInfo = mCollectorPeer->getWakeUpCollectionInfo();

    EXPECT_THAT(actualCollectionInfo.records.size(), 1)
            << "Wake-up collection info missing after collection";

    advanceTime(/*durationMillis=*/kTestSystemEventDataCacheDurationSec.count() * 1000);
    ResourceStats actualResourceStats = {};

    // Call |onPeriodicCollection| 1 hour past the last wake-up collection event.
    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(getNowMillis(), SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    actualCollectionInfo = mCollectorPeer->getWakeUpCollectionInfo();

    EXPECT_THAT(actualCollectionInfo.records.empty(), true)
            << "Wake-up collection info records are not empty after cache eviction period";
}

TEST_F(PerformanceProfilerTest, TestUserSwitchCollectionCacheEvictionAfterTimeout) {
    userid_t userIdToTriggerEviction = kTestBaseUserId + kTestMaxUserSwitchEvents;
    for (userid_t userId = kTestBaseUserId; userId < userIdToTriggerEviction; ++userId) {
        ASSERT_RESULT_OK(mCollector->onUserSwitchCollection(getNowMillis(), userId, userId + 1,
                                                            mMockUidStatsCollector,
                                                            mMockProcStatCollector));
        advanceTime(/*advanceUptimeMillis=*/kTestSystemEventDataCacheDurationSec.count() * 1000);
    }

    const auto& actual = mCollectorPeer->getUserSwitchCollectionInfos();

    EXPECT_THAT(actual.size(), kTestMaxUserSwitchEvents);

    ResourceStats resourceStats = {};
    for (int i = 1; i <= kTestMaxUserSwitchEvents; ++i) {
        ASSERT_RESULT_OK(mCollector->onPeriodicCollection(getNowMillis(), SystemState::NORMAL_MODE,
                                                          mMockUidStatsCollector,
                                                          mMockProcStatCollector, &resourceStats));

        const auto& actual = mCollectorPeer->getUserSwitchCollectionInfos();

        EXPECT_THAT(actual.size(), kTestMaxUserSwitchEvents - i)
                << "Expired user switch collection infos are still retained after " << i
                << "iterations";

        advanceTime(/*durationMillis=*/kTestSystemEventDataCacheDurationSec.count() * 1000);
    }
}

TEST_F(PerformanceProfilerTest, TestOnDumpProto) {
    auto statsInfo = getSampleStatsInfo();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillRepeatedly(Return(statsInfo.uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats())
            .WillRepeatedly(Return(statsInfo.procStatInfo));

    DataProcessorInterface::CollectionIntervals collectionIntervals =
            {.mBoottimeIntervalMillis = std::chrono::milliseconds(1),
             .mPeriodicIntervalMillis = std::chrono::milliseconds(10),
             .mUserSwitchIntervalMillis = std::chrono::milliseconds(100),
             .mWakeUpIntervalMillis = std::chrono::milliseconds(1000),
             .mCustomIntervalMillis = std::chrono::milliseconds(10000)};

    ResourceStats actualResourceStats = {};

    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(getNowMillis(), SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    ASSERT_RESULT_OK(mCollector->onBoottimeCollection(getNowMillis(), mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    ASSERT_RESULT_OK(mCollector->onWakeUpCollection(getNowMillis(), mMockUidStatsCollector,
                                                    mMockProcStatCollector));

    ASSERT_RESULT_OK(mCollector->onCustomCollection(getNowMillis(), SystemState::NORMAL_MODE, {},
                                                    mMockUidStatsCollector, mMockProcStatCollector,
                                                    &actualResourceStats));

    ASSERT_RESULT_OK(mCollector->onUserSwitchCollection(getNowMillis(), kTestBaseUserId,
                                                        kTestBaseUserId + 1, mMockUidStatsCollector,
                                                        mMockProcStatCollector));

    util::ProtoOutputStream proto;
    mCollector->onDumpProto(collectionIntervals, proto);

    PerformanceProfilerDump performanceProfilerDump;
    ASSERT_TRUE(performanceProfilerDump.ParseFromString(toString(&proto)));

    PerformanceStats performanceStats = performanceProfilerDump.performance_stats();
    auto bootTimeStats = performanceStats.boot_time_stats();
    EXPECT_EQ(bootTimeStats.collection_interval_millis(), 1);
    for (auto& record : bootTimeStats.records()) {
        EXPECT_THAT(record,
                    StatsRecordProtoEq(statsInfo.userPackageSummaryStats,
                                       statsInfo.systemSummaryStats, getNowMillis()));
    }

    for (const auto& userSwitchStat : performanceStats.user_switch_stats()) {
        EXPECT_EQ(userSwitchStat.to_user_id(), kTestBaseUserId + 1);
        EXPECT_EQ(userSwitchStat.from_user_id(), kTestBaseUserId);
        auto userSwitchCollection = userSwitchStat.user_switch_collection();
        EXPECT_EQ(userSwitchCollection.collection_interval_millis(), 100);
        for (const auto& record : userSwitchCollection.records()) {
            EXPECT_THAT(record,
                        StatsRecordProtoEq(statsInfo.userPackageSummaryStats,
                                           statsInfo.systemSummaryStats, getNowMillis()));
        }
    }

    auto wakeUpStats = performanceStats.wake_up_stats();
    EXPECT_EQ(wakeUpStats.collection_interval_millis(), 1000);
    for (auto& record : wakeUpStats.records()) {
        EXPECT_THAT(record,
                    StatsRecordProtoEq(statsInfo.userPackageSummaryStats,
                                       statsInfo.systemSummaryStats, getNowMillis()));
    }

    auto lastNMinutesStats = performanceStats.last_n_minutes_stats();
    EXPECT_EQ(lastNMinutesStats.collection_interval_millis(), 10);
    for (auto& record : lastNMinutesStats.records()) {
        EXPECT_THAT(record,
                    StatsRecordProtoEq(statsInfo.userPackageSummaryStats,
                                       statsInfo.systemSummaryStats, getNowMillis()));
    }

    auto customCollectionStats = performanceStats.custom_collection_stats();
    EXPECT_EQ(customCollectionStats.collection_interval_millis(), 10000);
    for (auto& record : customCollectionStats.records()) {
        EXPECT_THAT(record,
                    StatsRecordProtoEq(statsInfo.userPackageSummaryStats,
                                       statsInfo.systemSummaryStats, getNowMillis()));
    }
}

TEST_F(PerformanceProfilerTest, TestOnPeriodicCollectionWithSmapsRollupSupportInverted) {
    mCollectorPeer->setSmapsRollupSupportedEnabled(!kTestIsSmapsRollupSupported);
    auto [expectedCollectionInfo, expectedResourceStats] =
            setupFirstCollection(kTestPeriodicCollectionBufferSize, !kTestIsSmapsRollupSupported);

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(getNowMillis(), SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    const auto actualCollectionInfo = mCollectorPeer->getPeriodicCollectionInfo();

    EXPECT_THAT(actualCollectionInfo, CollectionInfoEq(expectedCollectionInfo))
            << "When smaps rollup is not supported, periodic collection info doesn't match."
            << "\nExpected:\n"
            << expectedCollectionInfo.toString() << "\nActual:\n"
            << actualCollectionInfo.toString();

    ASSERT_EQ(actualResourceStats, expectedResourceStats)
            << "Expected: " << expectedResourceStats.toString()
            << "\nActual: " << actualResourceStats.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Boot-time, wake-up and user-switch collections shouldn't be reported";
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
