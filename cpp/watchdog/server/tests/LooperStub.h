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

#pragma once

#include <android-base/chrono_utils.h>
#include <android-base/result.h>
#include <time.h>
#include <utils/Looper.h>
#include <utils/Mutex.h>
#include <utils/StrongPointer.h>

#include <functional>
#include <vector>

#include "LooperWrapper.h"

namespace android {
namespace automotive {
namespace watchdog {
namespace testing {

// LooperStub allows polling the underlying looper deterministically.
// NOTE: Current implementation only works for one handler.
class LooperStub final : public LooperWrapper {
public:
    LooperStub() : mHandler(nullptr), mShouldPoll(false), mTimer(0) {}

    nsecs_t now() override { return mTimer.count(); }
    // No-op when mShouldPoll is false. Otherwise, sends messages (in a non-empty CacheEntry from
    // the front of |mCache|) to the underlying looper and polls the looper immediately. This
    // method is called internally by the underlying looper.
    int pollAll(int timeoutMillis) override;

    // Updates the front of |mCache| with the given message so the next pollAll call to the
    // underlying looper will poll this message.
    void sendMessage(const android::sp<MessageHandler>& handler, const Message& message) override;

    // Updates the seconds(uptimeDelay) position in |mCache| with the given message.
    // Thus |uptimeDelay| should be convertible to seconds without any fractions. uptimeDelay is
    // computed from (uptime - now).
    void sendMessageAtTime(nsecs_t uptime, const android::sp<MessageHandler>& handler,
                           const Message& message) override;

    // Removes all the messages from the cache and looper for |mHandler|.
    void removeMessages(const android::sp<MessageHandler>& handler) override;

    // Removes the |what| message from the cache and looper for |mHandler|.
    void removeMessages(const android::sp<MessageHandler>& handler, int what) override;

    // Sets |mShouldPoll| so that the subsequent |pollAll| call processes the next non-empty
    // CacheEntry in |mCache|. Before returning, waits for the pollAll call sent to the underlying
    // looper to complete. Thus the caller can be certain this message was processed.
    android::base::Result<void> pollCache();

    // Number of seconds elapsed since the last pollAll call to the underlying looper.
    nsecs_t numSecondsElapsed() {
        return std::chrono::duration_cast<std::chrono::seconds>(mElapsedTime).count();
    }

    // Increment time by a specific duration.
    void incrementTime(std::chrono::nanoseconds duration) { mTimer += duration; }

private:
    Mutex mMutex;
    android::sp<MessageHandler> mHandler;
    using CacheEntry = std::vector<Message>;  // Messages to process on a given second.
    std::vector<CacheEntry> mCache;           // Messages pending to be processed.
    bool mShouldPoll;
    std::chrono::nanoseconds mTimer;
    std::chrono::nanoseconds mElapsedTime;
};

}  // namespace testing
}  // namespace watchdog
}  // namespace automotive
}  // namespace android
