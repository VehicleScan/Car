/*
 * Copyright (c) 2021, The Android Open Source Project
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

#pragma once

#include <aidl/android/automotive/watchdog/internal/ResourceOveruseConfiguration.h>
#include <android-base/result.h>
#include <utils/RefBase.h>

namespace android {
namespace automotive {
namespace watchdog {

constexpr int64_t kOneMegaByte = 1024 * 1024;

class OveruseConfigurationXmlHelper final : virtual public android::RefBase {
public:
    static android::base::Result<
            aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>
    parseXmlFile(const char* filePath);

    static android::base::Result<void> writeXmlFile(
            const aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration&
                    configuration,
            const char* filePath);
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
