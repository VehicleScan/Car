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

package com.android.car.audio;

import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioDeviceInfo.TYPE_AUX_LINE;
import static android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST;
import static android.media.AudioDeviceInfo.TYPE_BLE_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
import static android.media.AudioDeviceInfo.TYPE_BUS;
import static android.media.AudioDeviceInfo.TYPE_FM_TUNER;
import static android.media.AudioDeviceInfo.TYPE_HDMI;
import static android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY;
import static android.media.AudioDeviceInfo.TYPE_USB_DEVICE;
import static android.media.AudioDeviceInfo.TYPE_USB_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET;
import static android.media.AudioManager.GET_DEVICES_OUTPUTS;

import static com.android.car.audio.CarAudioDeviceInfoTestUtils.ALARM_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.CALL_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.MEDIA_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.MIRROR_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.NAVIGATION_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.NOTIFICATION_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.OEM_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.PRIMARY_ZONE_FM_TUNER_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.PRIMARY_ZONE_MICROPHONE_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.QUATERNARY_TEST_DEVICE_1;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.RING_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.SECONDARY_TEST_DEVICE_CONFIG_0;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.SECONDARY_TEST_DEVICE_CONFIG_1_0;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.SECONDARY_TEST_DEVICE_CONFIG_1_1;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.SECONDARY_ZONE_BACK_MICROPHONE_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.SYSTEM_BUS_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.TERTIARY_TEST_DEVICE_1;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.TERTIARY_TEST_DEVICE_2;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.TEST_REAR_ROW_3_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.TEST_SPEAKER_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.VOICE_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.generateCarAudioDeviceInfo;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.generateInputAudioDeviceInfo;
import static com.android.car.audio.CarAudioUtils.ACTIVATION_VOLUME_PERCENTAGE_MAX;
import static com.android.car.audio.CarAudioUtils.ACTIVATION_VOLUME_PERCENTAGE_MIN;
import static com.android.car.audio.CarAudioUtils.excludesDynamicDevices;
import static com.android.car.audio.CarAudioUtils.generateAddressToCarAudioDeviceInfoMap;
import static com.android.car.audio.CarAudioUtils.generateAddressToInputAudioDeviceInfoMap;
import static com.android.car.audio.CarAudioUtils.generateCarAudioDeviceInfos;
import static com.android.car.audio.CarAudioUtils.getAudioAttributesForDynamicDevices;
import static com.android.car.audio.CarAudioUtils.getDynamicDevicesInConfig;
import static com.android.car.audio.CarAudioUtils.hasExpired;
import static com.android.car.audio.CarAudioUtils.isInvalidActivationPercentage;
import static com.android.car.audio.CarAudioUtils.isMicrophoneInputDevice;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.car.feature.Flags;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.CarVolumeGroupInfo;
import android.car.test.AbstractExpectableTestCase;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.internal.util.DebugUtils;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarAudioUtilsTest extends AbstractExpectableTestCase {

    public static final String TEST_ADDRESS_1 = "test_address_1";
    public static final String TEST_ADDRESS_2 = "test_address_2";
    public static final String TEST_NOT_AVAILABLE_ADDRESS = "test_not_available_address";
    private static final AudioAttributes TEST_MEDIA_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();
    private static final AudioAttributes TEST_NAV_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build();
    private static final AudioAttributes TEST_ASSISTANT_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_ASSISTANT).build();
    private static final AudioDeviceAttributes TEST_BT_DEVICE =
            getMockDevice(/* address= */ "", TYPE_BLUETOOTH_A2DP);
    private static final AudioDeviceAttributes TEST_BUS_DEVICE_1 =
            getMockDevice(TEST_ADDRESS_1, TYPE_BUS);
    private static final AudioDeviceAttributes TEST_BUS_DEVICE_2 =
            getMockDevice(TEST_ADDRESS_2, TYPE_BUS);

    private static final AudioDeviceInfo TEST_BUS_DEVICE_1_INFO =
            getMockDeviceInfo(TEST_ADDRESS_1, TYPE_BUS);
    private static final AudioDeviceInfo TEST_BUS_DEVICE_2_INFO =
            getMockDeviceInfo(TEST_ADDRESS_2, TYPE_BUS);
    private static final AudioDeviceInfo TEST_BT_DEVICE_INFO =
            getMockDeviceInfo(/* address= */ "", TYPE_BLUETOOTH_A2DP);

    private static AudioDeviceInfo getMockDeviceInfo(String address, int type) {
        AudioDeviceInfo device = Mockito.mock(AudioDeviceInfo.class);
        when(device.getAddress()).thenReturn(address);
        when(device.getType()).thenReturn(type);
        return device;
    }

    private static final String TEST_MEDIA_GROUP_NAME = "media volume group";
    private static final String TEST_BT_GROUP_NAME = "bt volume group";
    private static final String TEST_NAV_GROUP_NAME = "nav volume group";
    private static final int TEST_ZONE_ID = 1;
    private static final int TEST_MEDIA_GROUP_ID = 0;
    private static final int TEST_BT_GROUP_ID = 1;
    private static final int TEST_NAV_GROUP_ID = 2;
    private static final int TEST_MAX_GAIN_INDEX = 100;
    private static final int TEST_MIN_GAIN_INDEX = 0;
    private static final int TEST_MAX_ACTIVATION_GAIN_INDEX = 80;
    private static final int TEST_MIN_ACTIVATION_GAIN_INDEX = 20;
    private static final CarVolumeGroupInfo TEST_MEDIA_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder(TEST_MEDIA_GROUP_NAME, TEST_ZONE_ID, TEST_BT_GROUP_ID)
                    .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX)
                    .setAudioAttributes(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE))
                    .setAudioDeviceAttributes(List.of(TEST_BUS_DEVICE_1)).build();
    private static final CarVolumeGroupInfo TEST_NAV_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder(TEST_NAV_GROUP_NAME, TEST_ZONE_ID, TEST_NAV_GROUP_ID)
                    .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX)
                    .setAudioAttributes(List.of(TEST_NAV_AUDIO_ATTRIBUTE))
                    .setAudioDeviceAttributes(List.of(TEST_BUS_DEVICE_2)).build();
    private static final CarVolumeGroupInfo TEST_BT_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder(TEST_BT_GROUP_NAME, TEST_ZONE_ID, TEST_MEDIA_GROUP_ID)
                    .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX)
                    .setAudioAttributes(List.of(TEST_ASSISTANT_AUDIO_ATTRIBUTE))
                    .setAudioDeviceAttributes(List.of(TEST_BT_DEVICE)).build();
    private static final int TEST_CONFIG_ID = 0;
    private static final boolean TEST_ACTIVE_STATUS = true;
    private static final boolean TEST_SELECTED_STATUS = true;
    private static final boolean TEST_DEFAULT_STATUS = true;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void hasExpired_forCurrentTimeBeforeTimeout() {
        expectWithMessage("Unexpired state").that(hasExpired(/*startTimeMs= */ 0,
                /*currentTimeMs= */ 100, /*timeoutMs= */ 200)).isFalse();
    }

    @Test
    public void hasExpired_forCurrentTimeAfterTimeout() {
        expectWithMessage("Expired state").that(hasExpired(/*startTimeMs= */ 0,
                /*currentTimeMs= */ 300, /*timeoutMs= */ 200)).isTrue();
    }

    @Test
    public void isMicrophoneInputDevice_forMicrophoneDevice() {
        AudioDeviceInfo deviceInfo = Mockito.mock(AudioDeviceInfo.class);
        when(deviceInfo.getType()).thenReturn(TYPE_BUILTIN_MIC);
        expectWithMessage("Microphone device").that(isMicrophoneInputDevice(deviceInfo)).isTrue();
    }

    @Test
    public void isMicrophoneInputDevice_forNonMicrophoneDevice() {
        AudioDeviceInfo deviceInfo = Mockito.mock(AudioDeviceInfo.class);
        when(deviceInfo.getType()).thenReturn(TYPE_FM_TUNER);
        expectWithMessage("Non microphone device")
                .that(isMicrophoneInputDevice(deviceInfo)).isFalse();
    }

    @Test
    public void getAudioDeviceInfo() {
        AudioDeviceInfo info1 = getTestAudioDeviceInfo(TEST_ADDRESS_1);
        AudioDeviceInfo info2 = getTestAudioDeviceInfo(TEST_ADDRESS_2);
        AudioManagerWrapper audioManager = Mockito.mock(AudioManagerWrapper.class);
        when(audioManager.getDevices(anyInt())).thenReturn(new AudioDeviceInfo[]{info2, info1});
        AudioDeviceAttributes attributes =
                new AudioDeviceAttributes(TYPE_BLUETOOTH_A2DP, TEST_ADDRESS_1);

        AudioDeviceInfo info = CarAudioUtils.getAudioDeviceInfo(attributes, audioManager);

        expectWithMessage("Audio device info").that(info).isEqualTo(info1);
    }

    @Test
    public void getAudioDeviceInfo_withDeviceNotAvailable() {
        AudioDeviceInfo info1 = getTestAudioDeviceInfo(TEST_ADDRESS_1);
        AudioDeviceInfo info2 = getTestAudioDeviceInfo(TEST_ADDRESS_2);
        AudioManagerWrapper audioManager = Mockito.mock(AudioManagerWrapper.class);
        when(audioManager.getDevices(anyInt())).thenReturn(new AudioDeviceInfo[]{info2, info1});
        AudioDeviceAttributes attributes =
                new AudioDeviceAttributes(TYPE_BLUETOOTH_A2DP, TEST_NOT_AVAILABLE_ADDRESS);

        AudioDeviceInfo info = CarAudioUtils.getAudioDeviceInfo(attributes, audioManager);

        expectWithMessage("Not available audio device info").that(info).isNull();
    }

    @Test
    public void isDynamicDeviceType_forDynamicDevices() {
        List<Integer> dynamicDevices = List.of(TYPE_WIRED_HEADSET, TYPE_WIRED_HEADPHONES,
                TYPE_BLUETOOTH_A2DP, TYPE_HDMI, TYPE_USB_ACCESSORY, TYPE_USB_DEVICE,
                TYPE_USB_HEADSET, TYPE_AUX_LINE, TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER,
                TYPE_BLE_BROADCAST);

        for (int dynamicDeviceType : dynamicDevices) {
            expectWithMessage("Dynamic Audio device type %s", DebugUtils.constantToString(
                    AudioDeviceInfo.class, /* prefix= */ "TYPE_", dynamicDeviceType))
                    .that(CarAudioUtils.isDynamicDeviceType(dynamicDeviceType)).isTrue();
        }
    }

    @Test
    public void isDynamicDeviceType_forNonDynamicDevice() {
        List<Integer> dynamicDevices = List.of(TYPE_BUILTIN_SPEAKER, TYPE_BUS);

        for (int dynamicDeviceType : dynamicDevices) {
            expectWithMessage("Non dynamic audio device type %s", DebugUtils.constantToString(
                    AudioDeviceInfo.class, /* prefix= */ "TYPE_", dynamicDeviceType))
                    .that(CarAudioUtils.isDynamicDeviceType(dynamicDeviceType)).isFalse();
        }
    }

    @Test
    public void excludesDynamicDevices_withOutDynamicDevices() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo testNoDynamicDevicesConfig = getCarAudioZoneConfigInfo();

        expectWithMessage("Info without dynamic devices")
                .that(excludesDynamicDevices(testNoDynamicDevicesConfig)).isTrue();
    }

    @Test
    public void excludesDynamicDevices_withDynamicDevices() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo testDynamicDevicesConfig = getTestDynamicDevicesConfig();

        expectWithMessage("Info with dynamic devices")
                .that(excludesDynamicDevices(testDynamicDevicesConfig)).isFalse();
    }

    @Test
    public void excludesDynamicDevices_withOutDynamicDevices_withDynamicFlagsDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo testNoDynamicDevicesConfig = getCarAudioZoneConfigInfo();

        expectWithMessage("Info without dynamic devices with dynamic flags disable")
                .that(excludesDynamicDevices(testNoDynamicDevicesConfig)).isTrue();
    }

    @Test
    public void excludesDynamicDevices_withDynamicDevices_withDynamicFlagsDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo testDynamicDevicesConfig = getTestDynamicDevicesConfig();

        expectWithMessage("Info with dynamic devices with dynamic flags disable")
                .that(excludesDynamicDevices(testDynamicDevicesConfig)).isTrue();
    }

    @Test
    public void getDynamicDevicesInConfig_withDynamicDevices() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        AudioManagerWrapper manager = setUpMockAudioManager();
        CarAudioZoneConfigInfo testDynamicDevicesConfig = getTestDynamicDevicesConfig();

        expectWithMessage("Non-dynamic devices")
                .that(getDynamicDevicesInConfig(testDynamicDevicesConfig, manager))
                .containsExactly(TEST_BT_DEVICE_INFO);
    }

    @Test
    public void getDynamicDevicesInConfig_withoutDynamicDevices() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        AudioManagerWrapper manager = setUpMockAudioManager();
        CarAudioZoneConfigInfo testDynamicDevicesConfig = getCarAudioZoneConfigInfo();

        expectWithMessage("Dynamic devices")
                .that(getDynamicDevicesInConfig(testDynamicDevicesConfig, manager)).isEmpty();
    }

    @Test
    public void getDynamicDevicesInConfig_withDynamicDevices_andFlagDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        AudioManagerWrapper manager = setUpMockAudioManager();
        CarAudioZoneConfigInfo testDynamicDevicesConfig = getTestDynamicDevicesConfig();

        expectWithMessage("Dynamic devices with flags disabled")
                .that(getDynamicDevicesInConfig(testDynamicDevicesConfig, manager)).isEmpty();
    }

    @Test
    public void getDynamicDevicesInConfig_withoutDynamicDevices_andFlagDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        AudioManagerWrapper manager = setUpMockAudioManager();
        CarAudioZoneConfigInfo testNonDynamicDevicesConfig = getCarAudioZoneConfigInfo();

        expectWithMessage("Non-dynamic devices with flags disabled")
                .that(getDynamicDevicesInConfig(testNonDynamicDevicesConfig, manager)).isEmpty();
    }

    @Test
    public void getAudioAttributesForDynamicDevices_withoutDynamicDevicesAndFlagDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo testNonDynamicDevicesConfig = getCarAudioZoneConfigInfo();

        expectWithMessage("Audio attributes with flags disabled without dynamic device")
                .that(getAudioAttributesForDynamicDevices(testNonDynamicDevicesConfig)).isEmpty();
    }

    @Test
    public void getAudioAttributesForDynamicDevices_withDynamicDevicesAndFlagDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo testDynamicDevicesConfig = getTestDynamicDevicesConfig();

        expectWithMessage("Audio attributes with flags disabled with dynamic devices")
                .that(getAudioAttributesForDynamicDevices(testDynamicDevicesConfig)).isEmpty();
    }

    @Test
    public void getAudioAttributesForDynamicDevices_withDynamicDevicesAndFlagEnabled() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo testDynamicDevicesConfig = getTestDynamicDevicesConfig();

        expectWithMessage("Audio attributes for volume group with dynamic devices and "
                + "flags enabled")
                .that(getAudioAttributesForDynamicDevices(testDynamicDevicesConfig))
                .containsExactlyElementsIn(TEST_BT_VOLUME_INFO.getAudioAttributes());
    }

    @Test
    public void getAudioAttributesForDynamicDevices_withoutDynamicDevicesAndFlagEnabled() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo testDynamicDevicesConfig = getCarAudioZoneConfigInfo();

        expectWithMessage("Audio attributes for volume group with no dynamic devices and "
                + "flags enabled")
                .that(getAudioAttributesForDynamicDevices(testDynamicDevicesConfig)).isEmpty();
    }

    @Test
    public void isInvalidActivationPercentage_withInvalidLowerRange() {
        expectWithMessage("Activation percentage status with value under lower invalid range")
                .that(isInvalidActivationPercentage(ACTIVATION_VOLUME_PERCENTAGE_MIN - 1))
                .isTrue();
    }

    @Test
    public void isInvalidActivationPercentage_withInvalidUpperRange() {
        expectWithMessage("Activation percentage status with value over upper invalid range")
                .that(isInvalidActivationPercentage(ACTIVATION_VOLUME_PERCENTAGE_MAX + 1))
                .isTrue();
    }

    @Test
    public void isInvalidActivationPercentage_withValidValue() {
        expectWithMessage("Activation percentage status with valid value")
                .that(isInvalidActivationPercentage(TEST_MAX_ACTIVATION_GAIN_INDEX)).isFalse();
    }

    @Test
    public void generateAddressToCarAudioDeviceInfoMap_withNullDevices() {
        var thrown = assertThrows(NullPointerException.class,
                () -> generateAddressToCarAudioDeviceInfoMap(/* carAudioDeviceInfos = */ null));

        expectWithMessage("Exception for generate car audio device info map with null devices")
                .that(thrown).hasMessageThat().contains("Car audio device infos");
    }

    @Test
    public void generateAddressToCarAudioDeviceInfoMap_withValidCarAudioDevices() {
        var map = generateAddressToCarAudioDeviceInfoMap(generateCarDeviceInfos());

        expectWithMessage("Generated car audio device addresses").that(map.keySet())
                .containsExactly(MEDIA_TEST_DEVICE, NAVIGATION_TEST_DEVICE, CALL_TEST_DEVICE,
                        VOICE_TEST_DEVICE, NOTIFICATION_TEST_DEVICE, RING_TEST_DEVICE,
                        ALARM_TEST_DEVICE, SYSTEM_BUS_DEVICE);
    }

    @Test
    public void generateAddressToInputAudioDeviceInfoMap_withNullInputDevices() {
        var thrown = assertThrows(NullPointerException.class,
                () -> generateAddressToInputAudioDeviceInfoMap(/* deviceInfos = */ null));

        expectWithMessage("Exception for generate input audio device info map with null devices")
                .that(thrown).hasMessageThat().contains("Input audio device infos");
    }

    @Test
    public void generateAddressToInputAudioDeviceInfoMap_withValidInputDevices() {
        var inputDevicesMap = generateAddressToInputAudioDeviceInfoMap(generateInputDeviceInfos());

        expectWithMessage("Input devices map").that(inputDevicesMap).hasSize(3);
        expectWithMessage("Input device addresses").that(inputDevicesMap.keySet())
                .containsExactly(PRIMARY_ZONE_MICROPHONE_DEVICE, PRIMARY_ZONE_FM_TUNER_DEVICE,
                        SECONDARY_ZONE_BACK_MICROPHONE_DEVICE);
    }

    @Test
    public void generateCarAudioDeviceInfos_withNullManager() {
        var thrown = assertThrows(NullPointerException.class,
                () -> generateCarAudioDeviceInfos(/* audioManager = */ null));

        expectWithMessage("Exception for generating car audio devices")
                .that(thrown).hasMessageThat().contains("Audio manager");
    }

    @Test
    public void generateCarAudioDeviceInfos_withValidDevicesInManager() {
        var carAudioDeviceInfoTestUtils = new CarAudioDeviceInfoTestUtils();
        var audioManager = Mockito.mock(AudioManagerWrapper.class);
        var outputDevices = carAudioDeviceInfoTestUtils.generateOutputDeviceInfos();
        when(audioManager.getDevices(GET_DEVICES_OUTPUTS)).thenReturn(outputDevices);

        var carAudioDevices = generateCarAudioDeviceInfos(audioManager);

        var addresses = carAudioDevices.stream().map(CarAudioDeviceInfo::getAddress).toList();
        expectWithMessage("Generated car audio devices").that(addresses)
                .containsExactly(MEDIA_TEST_DEVICE, OEM_TEST_DEVICE, MIRROR_TEST_DEVICE,
                        NAVIGATION_TEST_DEVICE, CALL_TEST_DEVICE, NOTIFICATION_TEST_DEVICE,
                        VOICE_TEST_DEVICE, RING_TEST_DEVICE, ALARM_TEST_DEVICE, SYSTEM_BUS_DEVICE,
                        SECONDARY_TEST_DEVICE_CONFIG_0, SECONDARY_TEST_DEVICE_CONFIG_1_0,
                        SECONDARY_TEST_DEVICE_CONFIG_1_1, TERTIARY_TEST_DEVICE_1,
                        TERTIARY_TEST_DEVICE_2, QUATERNARY_TEST_DEVICE_1, TEST_REAR_ROW_3_DEVICE,
                        TEST_SPEAKER_DEVICE);
    }

    private List<CarAudioDeviceInfo> generateCarDeviceInfos() {
        return ImmutableList.of(
                generateCarAudioDeviceInfo(MEDIA_TEST_DEVICE),
                generateCarAudioDeviceInfo(NAVIGATION_TEST_DEVICE),
                generateCarAudioDeviceInfo(CALL_TEST_DEVICE),
                generateCarAudioDeviceInfo(NOTIFICATION_TEST_DEVICE),
                generateCarAudioDeviceInfo(VOICE_TEST_DEVICE),
                generateCarAudioDeviceInfo(RING_TEST_DEVICE),
                generateCarAudioDeviceInfo(ALARM_TEST_DEVICE),
                generateCarAudioDeviceInfo(SYSTEM_BUS_DEVICE),
                generateCarAudioDeviceInfo(/* address= */ ""),
                generateCarAudioDeviceInfo(/* address= */ ""),
                generateCarAudioDeviceInfo(/* address= */ null),
                generateCarAudioDeviceInfo(/* address= */ null));
    }

    private AudioDeviceInfo[] generateInputDeviceInfos() {
        return new AudioDeviceInfo[]{
                generateInputAudioDeviceInfo(PRIMARY_ZONE_MICROPHONE_DEVICE, TYPE_BUILTIN_MIC),
                generateInputAudioDeviceInfo(PRIMARY_ZONE_FM_TUNER_DEVICE, TYPE_FM_TUNER),
                generateInputAudioDeviceInfo(SECONDARY_ZONE_BACK_MICROPHONE_DEVICE, TYPE_BUS),
                generateInputAudioDeviceInfo(/* address= */ "", TYPE_BUS),
                generateInputAudioDeviceInfo(/* address= */ null, TYPE_BUS),
        };
    }

    private static CarAudioZoneConfigInfo getTestDynamicDevicesConfig() {
        return new CarAudioZoneConfigInfo("dynamic-devices-config",
                List.of(TEST_MEDIA_VOLUME_INFO, TEST_NAV_VOLUME_INFO, TEST_BT_VOLUME_INFO),
                TEST_ZONE_ID, TEST_CONFIG_ID, TEST_ACTIVE_STATUS, TEST_SELECTED_STATUS,
                TEST_DEFAULT_STATUS);
    }

    private static @NonNull CarAudioZoneConfigInfo getCarAudioZoneConfigInfo() {
        return new CarAudioZoneConfigInfo("Non-dynamic-devices-config",
                List.of(TEST_MEDIA_VOLUME_INFO, TEST_NAV_VOLUME_INFO), TEST_ZONE_ID, TEST_CONFIG_ID,
                TEST_ACTIVE_STATUS, TEST_SELECTED_STATUS, TEST_DEFAULT_STATUS);
    }

    private AudioManagerWrapper setUpMockAudioManager() {
        AudioManagerWrapper manager = Mockito.mock(AudioManagerWrapper.class);
        when(manager.getDevices(GET_DEVICES_OUTPUTS))
                .thenReturn(getMockOutputDevices());
        return manager;
    }

    private AudioDeviceInfo[] getMockOutputDevices() {
        return new AudioDeviceInfo[] { TEST_BUS_DEVICE_1_INFO, TEST_BUS_DEVICE_2_INFO,
                TEST_BT_DEVICE_INFO};
    }

    private static AudioDeviceAttributes getMockDevice(String address, int type) {
        AudioDeviceAttributes attributeMock = Mockito.mock(AudioDeviceAttributes.class);
        when(attributeMock.getAddress()).thenReturn(address);
        when(attributeMock.getType()).thenReturn(type);
        return attributeMock;
    }

    private static AudioDeviceInfo getTestAudioDeviceInfo(String address) {
        AudioDeviceInfo deviceInfo = Mockito.mock(AudioDeviceInfo.class);
        when(deviceInfo.getAddress()).thenReturn(address);
        return deviceInfo;
    }
}
