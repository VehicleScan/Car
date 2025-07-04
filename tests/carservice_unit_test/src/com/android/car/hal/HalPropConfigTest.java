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

package com.android.car.hal;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.VehicleAreaType;
import android.car.feature.FeatureFlagsImpl;
import android.car.feature.Flags;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.AreaIdConfig;
import android.car.hardware.property.VehicleOilLevel;
import android.hardware.automotive.vehicle.HasSupportedValueInfo;
import android.hardware.automotive.vehicle.VehicleArea;
import android.hardware.automotive.vehicle.VehicleAreaConfig;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.hardware.automotive.vehicle.VehiclePropertyType;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.car.hal.property.PropertyHalServiceConfigs;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@EnableFlags({Flags.FLAG_ANDROID_VIC_VEHICLE_PROPERTIES, Flags.FLAG_AREA_ID_CONFIG_ACCESS})
@RunWith(MockitoJUnitRunner.class)
public final class HalPropConfigTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    public FeatureFlagsImpl mMockFeatureFlags;

    private PropertyHalServiceConfigs mPropertyHalServiceConfigs;

    private static final int GLOBAL_INTEGER_PROP_ID =
            1 | VehicleArea.GLOBAL | VehiclePropertyType.INT32;
    private static final int GLOBAL_LONG_PROP_ID =
            1 | VehicleArea.GLOBAL | VehiclePropertyType.INT64;
    private static final int GLOBAL_FLOAT_PROP_ID =
            1 | VehicleArea.GLOBAL | VehiclePropertyType.FLOAT;
    private static final int GLOBAL_INTEGER_VEC_PROP_ID =
            1 | VehicleArea.GLOBAL | VehiclePropertyType.INT32_VEC;
    private static final int VENDOR_INTEGER_VEC_PROP_ID =
            1 | VehicleArea.VENDOR | VehiclePropertyType.INT32_VEC;
    private static final int TEST_AREA_ID = 2;
    private static final int TEST_ALTERNATE_AREA_ID = 3;
    private static final int TEST_ACCESS = VehiclePropertyAccess.READ_WRITE;
    private static final int TEST_CHANGE_MODE = VehiclePropertyChangeMode.ON_CHANGE;
    private static final int[] TEST_CONFIG_ARRAY = new int[]{1, 2, 3};
    private static final ArrayList<Integer> TEST_CONFIG_ARRAY_LIST = new ArrayList<Integer>(
            Arrays.asList(1, 2, 3));
    private static final String TEST_CONFIG_STRING = "test_config";
    private static final float MIN_SAMPLE_RATE = 1.0f;
    private static final float MAX_SAMPLE_RATE = 10.0f;
    private static final int MIN_INT32_VALUE = 11;
    private static final int MAX_INT32_VALUE = 20;
    private static final long MIN_INT64_VALUE = 21;
    private static final long MAX_INT64_VALUE = 30;
    private static final float MIN_FLOAT_VALUE = 31.0f;
    private static final float MAX_FLOAT_VALUE = 40.0f;
    private static final long[] SUPPORTED_ENUM_VALUES = new long[]{99, 100};
    private static final Set<Integer> CONFIG_ARRAY_DEFINES_SUPPORTED_ENUM_VALUES =
            Set.of(
                    VehicleProperty.GEAR_SELECTION,
                    VehicleProperty.CURRENT_GEAR,
                    VehicleProperty.DISTANCE_DISPLAY_UNITS,
                    VehicleProperty.EV_BATTERY_DISPLAY_UNITS,
                    VehicleProperty.TIRE_PRESSURE_DISPLAY_UNITS,
                    VehicleProperty.FUEL_VOLUME_DISPLAY_UNITS,
                    VehicleProperty.HVAC_TEMPERATURE_DISPLAY_UNITS,
                    VehicleProperty.VEHICLE_SPEED_DISPLAY_UNITS);

    private static android.hardware.automotive.vehicle.V2_0.VehiclePropConfig
            getTestHidlPropConfig() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropConfig hidlConfig =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropConfig();
        hidlConfig.prop = GLOBAL_INTEGER_PROP_ID;
        hidlConfig.access = TEST_ACCESS;
        hidlConfig.changeMode = TEST_CHANGE_MODE;
        hidlConfig.configArray = TEST_CONFIG_ARRAY_LIST;
        hidlConfig.configString = TEST_CONFIG_STRING;
        hidlConfig.minSampleRate = MIN_SAMPLE_RATE;
        hidlConfig.maxSampleRate = MAX_SAMPLE_RATE;
        return hidlConfig;
    }

    private static VehiclePropConfig getTestVendorAidlPropConfig() {
        VehiclePropConfig aidlConfig = new VehiclePropConfig();
        aidlConfig.prop = VENDOR_INTEGER_VEC_PROP_ID;
        aidlConfig.access = TEST_ACCESS;
        aidlConfig.changeMode = TEST_CHANGE_MODE;
        aidlConfig.configArray = TEST_CONFIG_ARRAY;
        aidlConfig.configString = TEST_CONFIG_STRING;
        aidlConfig.minSampleRate = MIN_SAMPLE_RATE;
        aidlConfig.maxSampleRate = MAX_SAMPLE_RATE;
        aidlConfig.areaConfigs = new VehicleAreaConfig[0];
        return aidlConfig;
    }

    private static VehiclePropConfig getTestAidlPropConfig() {
        VehiclePropConfig aidlConfig = new VehiclePropConfig();
        aidlConfig.prop = GLOBAL_INTEGER_PROP_ID;
        aidlConfig.access = TEST_ACCESS;
        aidlConfig.changeMode = TEST_CHANGE_MODE;
        aidlConfig.configArray = TEST_CONFIG_ARRAY;
        aidlConfig.configString = TEST_CONFIG_STRING;
        aidlConfig.minSampleRate = MIN_SAMPLE_RATE;
        aidlConfig.maxSampleRate = MAX_SAMPLE_RATE;
        aidlConfig.areaConfigs = new VehicleAreaConfig[0];
        return aidlConfig;
    }

    private static android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig
            getTestHidlAreaConfig() {
        android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig hidlAreaConfig =
                new android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig();
        hidlAreaConfig.areaId = TEST_AREA_ID;
        hidlAreaConfig.minInt32Value = MIN_INT32_VALUE;
        hidlAreaConfig.maxInt32Value = MAX_INT32_VALUE;
        hidlAreaConfig.minInt64Value = MIN_INT64_VALUE;
        hidlAreaConfig.maxInt64Value = MAX_INT64_VALUE;
        hidlAreaConfig.minFloatValue = MIN_FLOAT_VALUE;
        hidlAreaConfig.maxFloatValue = MAX_FLOAT_VALUE;
        return hidlAreaConfig;
    }

    private static VehicleAreaConfig getTestAidlAreaConfig() {
        VehicleAreaConfig aidlAreaConfig = new VehicleAreaConfig();
        aidlAreaConfig.access = TEST_ACCESS;
        aidlAreaConfig.areaId = TEST_AREA_ID;
        aidlAreaConfig.minInt32Value = MIN_INT32_VALUE;
        aidlAreaConfig.maxInt32Value = MAX_INT32_VALUE;
        aidlAreaConfig.minInt64Value = MIN_INT64_VALUE;
        aidlAreaConfig.maxInt64Value = MAX_INT64_VALUE;
        aidlAreaConfig.minFloatValue = MIN_FLOAT_VALUE;
        aidlAreaConfig.maxFloatValue = MAX_FLOAT_VALUE;
        aidlAreaConfig.supportedEnumValues = SUPPORTED_ENUM_VALUES;
        aidlAreaConfig.supportVariableUpdateRate = true;
        return aidlAreaConfig;
    }

    private static VehicleAreaConfig getTestInvalidAidlAreaConfig() {
        VehicleAreaConfig aidlAreaConfig = new VehicleAreaConfig();
        aidlAreaConfig.access = VehiclePropertyAccess.NONE;
        aidlAreaConfig.areaId = TEST_ALTERNATE_AREA_ID;
        aidlAreaConfig.minInt32Value = MIN_INT32_VALUE;
        aidlAreaConfig.maxInt32Value = MAX_INT32_VALUE;
        aidlAreaConfig.minInt64Value = MIN_INT64_VALUE;
        aidlAreaConfig.maxInt64Value = MAX_INT64_VALUE;
        aidlAreaConfig.minFloatValue = MIN_FLOAT_VALUE;
        aidlAreaConfig.maxFloatValue = MAX_FLOAT_VALUE;
        aidlAreaConfig.supportedEnumValues = SUPPORTED_ENUM_VALUES;
        aidlAreaConfig.supportVariableUpdateRate = true;
        return aidlAreaConfig;
    }

    @Before
    public void setUp() {
        when(mMockFeatureFlags.androidVicVehicleProperties()).thenReturn(true);
        mPropertyHalServiceConfigs = new PropertyHalServiceConfigs(mMockFeatureFlags);
    }

    @Test
    public void testAidlHalPropConfigWithNoArea() {
        VehiclePropConfig aidlConfig = getTestAidlPropConfig();
        AidlHalPropConfig halPropConfig = new AidlHalPropConfig(aidlConfig);

        assertThat(halPropConfig.getPropId()).isEqualTo(GLOBAL_INTEGER_PROP_ID);
        assertThat(halPropConfig.getAccess()).isEqualTo(TEST_ACCESS);
        assertThat(halPropConfig.getChangeMode()).isEqualTo(TEST_CHANGE_MODE);
        assertThat(halPropConfig.getAreaConfigs().length).isEqualTo(0);
        assertThat(halPropConfig.getConfigArray()).isEqualTo(TEST_CONFIG_ARRAY);
        assertThat(halPropConfig.getConfigString()).isEqualTo(TEST_CONFIG_STRING);
        assertThat(halPropConfig.getMinSampleRate()).isEqualTo(MIN_SAMPLE_RATE);
        assertThat(halPropConfig.getMaxSampleRate()).isEqualTo(MAX_SAMPLE_RATE);
    }

    @Test
    public void testAidlHalPropConfigWithArea() {
        VehiclePropConfig aidlConfig = getTestAidlPropConfig();
        aidlConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        AidlHalPropConfig halPropConfig = new AidlHalPropConfig(aidlConfig);

        assertThat(halPropConfig.getAreaConfigs().length).isEqualTo(1);

        HalAreaConfig halAreaConfig = halPropConfig.getAreaConfigs()[0];
        assertThat(halAreaConfig.getAccess()).isEqualTo(TEST_ACCESS);
        assertThat(halAreaConfig.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(halAreaConfig.getMinInt32Value()).isEqualTo(MIN_INT32_VALUE);
        assertThat(halAreaConfig.getMaxInt32Value()).isEqualTo(MAX_INT32_VALUE);
        assertThat(halAreaConfig.getMinInt64Value()).isEqualTo(MIN_INT64_VALUE);
        assertThat(halAreaConfig.getMaxInt64Value()).isEqualTo(MAX_INT64_VALUE);
        assertThat(halAreaConfig.getMinFloatValue()).isEqualTo(MIN_FLOAT_VALUE);
        assertThat(halAreaConfig.getMaxFloatValue()).isEqualTo(MAX_FLOAT_VALUE);
    }

    @Test
    public void testToVehiclePropConfig_forAidlConfig() {
        VehiclePropConfig aidlConfig = getTestAidlPropConfig();
        AidlHalPropConfig halPropConfig = new AidlHalPropConfig(aidlConfig);

        assertThat((VehiclePropConfig) halPropConfig.toVehiclePropConfig()).isEqualTo(aidlConfig);
    }

    @Test
    public void testHidlHalPropConfigWithNoArea() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropConfig hidlConfig =
                getTestHidlPropConfig();
        HidlHalPropConfig halPropConfig = new HidlHalPropConfig(hidlConfig);

        assertThat(halPropConfig.getPropId()).isEqualTo(GLOBAL_INTEGER_PROP_ID);
        assertThat(halPropConfig.getAccess()).isEqualTo(TEST_ACCESS);
        assertThat(halPropConfig.getChangeMode()).isEqualTo(TEST_CHANGE_MODE);
        assertThat(halPropConfig.getAreaConfigs().length).isEqualTo(0);
        assertThat(halPropConfig.getConfigArray()).isEqualTo(TEST_CONFIG_ARRAY);
        assertThat(halPropConfig.getConfigString()).isEqualTo(TEST_CONFIG_STRING);
        assertThat(halPropConfig.getMinSampleRate()).isEqualTo(MIN_SAMPLE_RATE);
        assertThat(halPropConfig.getMaxSampleRate()).isEqualTo(MAX_SAMPLE_RATE);
    }

    @Test
    public void testHidlHalPropConfigWithArea() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropConfig hidlConfig =
                getTestHidlPropConfig();
        hidlConfig.areaConfigs =
                new ArrayList<android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig>(
                        Arrays.asList(getTestHidlAreaConfig()));
        HidlHalPropConfig halPropConfig = new HidlHalPropConfig(hidlConfig);

        assertThat(halPropConfig.getAreaConfigs().length).isEqualTo(1);

        HalAreaConfig halAreaConfig = halPropConfig.getAreaConfigs()[0];
        assertThat(halAreaConfig.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(halAreaConfig.getMinInt32Value()).isEqualTo(MIN_INT32_VALUE);
        assertThat(halAreaConfig.getMaxInt32Value()).isEqualTo(MAX_INT32_VALUE);
        assertThat(halAreaConfig.getMinInt64Value()).isEqualTo(MIN_INT64_VALUE);
        assertThat(halAreaConfig.getMaxInt64Value()).isEqualTo(MAX_INT64_VALUE);
        assertThat(halAreaConfig.getMinFloatValue()).isEqualTo(MIN_FLOAT_VALUE);
        assertThat(halAreaConfig.getMaxFloatValue()).isEqualTo(MAX_FLOAT_VALUE);
    }

    @Test
    public void testToVehiclePropConfig_forHidlConfig() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropConfig hidlConfig =
                getTestHidlPropConfig();
        HidlHalPropConfig halPropConfig = new HidlHalPropConfig(hidlConfig);

        assertThat((android.hardware.automotive.vehicle.V2_0.VehiclePropConfig)
                halPropConfig.toVehiclePropConfig()).isEqualTo(hidlConfig);
    }

    @Test
    public void testToCarPropertyConfig_populatesGlobalAreaId() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        CarPropertyConfig<?> carPropertyConfig = halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs);
        assertThat(carPropertyConfig.getPropertyId()).isEqualTo(GLOBAL_INTEGER_PROP_ID);
        assertThat(carPropertyConfig.getAreaIdConfigs()).hasSize(1);

        AreaIdConfig<?> areaIdConfig = carPropertyConfig.getAreaIdConfig(/*areaId=*/0);
        assertThat(areaIdConfig).isNotNull();
        assertThat(areaIdConfig.getAreaId()).isEqualTo(0);
        assertThat(areaIdConfig.getMinValue()).isNull();
        assertThat(areaIdConfig.getMaxValue()).isNull();
        assertThat(areaIdConfig.getSupportedEnumValues()).isEmpty();

        if (Flags.areaIdConfigAccess()) {
            assertThat(areaIdConfig.getAccess()).isEqualTo(TEST_ACCESS);
        }
    }

    @Test
    public void testToCarPropertyConfig_convertsIntegerMinMax() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        AreaIdConfig<?> areaIdConfig = halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig).isNotNull();
        assertThat(areaIdConfig.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(areaIdConfig.getMinValue()).isEqualTo(MIN_INT32_VALUE);
        assertThat(areaIdConfig.getMaxValue()).isEqualTo(MAX_INT32_VALUE);

        if (Flags.areaIdConfigAccess()) {
            assertThat(areaIdConfig.getAccess()).isEqualTo(TEST_ACCESS);
        }
    }

    @Test
    public void testToCarPropertyConfig_doesNotConvertIntegerMinMaxIfBothZero() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        aidlVehiclePropConfig.areaConfigs[0].minInt32Value = 0;
        aidlVehiclePropConfig.areaConfigs[0].maxInt32Value = 0;
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        AreaIdConfig<?> areaIdConfig = halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig).isNotNull();
        assertThat(areaIdConfig.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(areaIdConfig.getMinValue()).isNull();
        assertThat(areaIdConfig.getMaxValue()).isNull();

        if (Flags.areaIdConfigAccess()) {
            assertThat(areaIdConfig.getAccess()).isEqualTo(TEST_ACCESS);
        }
    }

    @Test
    public void testToCarPropertyConfig_convertsLongMinMax() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.prop = GLOBAL_LONG_PROP_ID;
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        AreaIdConfig<?> areaIdConfig = halPropConfig.toCarPropertyConfig(
                GLOBAL_LONG_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig).isNotNull();
        assertThat(areaIdConfig.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(areaIdConfig.getMinValue()).isEqualTo(MIN_INT64_VALUE);
        assertThat(areaIdConfig.getMaxValue()).isEqualTo(MAX_INT64_VALUE);

        if (Flags.areaIdConfigAccess()) {
            assertThat(areaIdConfig.getAccess()).isEqualTo(TEST_ACCESS);
        }
    }

    @Test
    public void testToCarPropertyConfig_doesNotConvertLongMinMaxIfBothZero() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.prop = GLOBAL_LONG_PROP_ID;
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        aidlVehiclePropConfig.areaConfigs[0].minInt64Value = 0;
        aidlVehiclePropConfig.areaConfigs[0].maxInt64Value = 0;
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        AreaIdConfig<?> areaIdConfig = halPropConfig.toCarPropertyConfig(
                GLOBAL_LONG_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig).isNotNull();
        assertThat(areaIdConfig.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(areaIdConfig.getMinValue()).isNull();
        assertThat(areaIdConfig.getMaxValue()).isNull();

        if (Flags.areaIdConfigAccess()) {
            assertThat(areaIdConfig.getAccess()).isEqualTo(TEST_ACCESS);
        }
    }

    @Test
    public void testToCarPropertyConfig_convertsFloatMinMax() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.prop = GLOBAL_FLOAT_PROP_ID;
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        AreaIdConfig<?> areaIdConfig = halPropConfig.toCarPropertyConfig(
                GLOBAL_FLOAT_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig).isNotNull();
        assertThat(areaIdConfig.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(areaIdConfig.getMinValue()).isEqualTo(MIN_FLOAT_VALUE);
        assertThat(areaIdConfig.getMaxValue()).isEqualTo(MAX_FLOAT_VALUE);

        if (Flags.areaIdConfigAccess()) {
            assertThat(areaIdConfig.getAccess()).isEqualTo(TEST_ACCESS);
        }
    }

    @Test
    public void testToCarPropertyConfig_doesNotConvertFloatMinMaxIfBothZero() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.prop = GLOBAL_FLOAT_PROP_ID;
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        aidlVehiclePropConfig.areaConfigs[0].minFloatValue = 0;
        aidlVehiclePropConfig.areaConfigs[0].maxFloatValue = 0;
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        AreaIdConfig<?> areaIdConfig = halPropConfig.toCarPropertyConfig(
                GLOBAL_FLOAT_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig).isNotNull();
        assertThat(areaIdConfig.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(areaIdConfig.getMinValue()).isNull();
        assertThat(areaIdConfig.getMaxValue()).isNull();

        if (Flags.areaIdConfigAccess()) {
            assertThat(areaIdConfig.getAccess()).isEqualTo(TEST_ACCESS);
        }
    }

    @Test
    public void testToCarPropertyConfig_doesNotPopulateMinMaxForUnsupportedType() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.prop = GLOBAL_INTEGER_VEC_PROP_ID;
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        AreaIdConfig<?> areaIdConfig = halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_VEC_PROP_ID, mPropertyHalServiceConfigs)
                .getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig).isNotNull();
        assertThat(areaIdConfig.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(areaIdConfig.getMinValue()).isNull();
        assertThat(areaIdConfig.getMaxValue()).isNull();

        if (Flags.areaIdConfigAccess()) {
            assertThat(areaIdConfig.getAccess()).isEqualTo(TEST_ACCESS);
        }
    }

    @Test
    public void testToCarPropertyConfig_aidlHandlesNullSupportedEnumsValues() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        aidlVehiclePropConfig.areaConfigs[0].supportedEnumValues = null;
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        assertThat(halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(
                TEST_AREA_ID).getSupportedEnumValues()).isEmpty();
    }

    @Test
    public void testToCarPropertyConfig_aidlHandlesSupportedEnumsValues() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        assertThat(halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(
                TEST_AREA_ID).getSupportedEnumValues()).containsExactly(99, 100);
    }

    @Test
    public void testToCarPropertyConfig_aidlSkipsSupportedEnumValuesIfNonOnChangeProperty() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.changeMode = VehiclePropertyChangeMode.STATIC;
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        assertThat(halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(
                TEST_AREA_ID).getSupportedEnumValues()).isEmpty();
    }

    @Test
    public void testToCarPropertyConfig_aidlAutoPopulatesSupportedEnumValuesIfEmpty() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.prop = VehicleProperty.ENGINE_OIL_LEVEL;
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        aidlVehiclePropConfig.areaConfigs[0].supportedEnumValues = null;
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        assertThat(halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(
                TEST_AREA_ID).getSupportedEnumValues()).containsExactlyElementsIn(
                        List.of(
                                VehicleOilLevel.LEVEL_CRITICALLY_LOW,
                                VehicleOilLevel.LEVEL_LOW,
                                VehicleOilLevel.LEVEL_NORMAL,
                                VehicleOilLevel.LEVEL_HIGH,
                                VehicleOilLevel.LEVEL_ERROR));
    }

    @Test
    public void testToCarPropertyConfig_hidlGetSupportedEnumsValuesReturnsEmpty() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropConfig hidlVehiclePropConfig =
                getTestHidlPropConfig();
        hidlVehiclePropConfig.areaConfigs =
                new ArrayList<android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig>(
                        Arrays.asList(getTestHidlAreaConfig()));
        HidlHalPropConfig halPropConfig = new HidlHalPropConfig(hidlVehiclePropConfig);

        assertThat(halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(
                TEST_AREA_ID).getSupportedEnumValues()).isEmpty();
    }

    @Test
    public void testToCarPropertyConfig_hidlAreaConfigGetAccessReturnsPropConfigAccess() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropConfig hidlVehiclePropConfig =
                getTestHidlPropConfig();
        hidlVehiclePropConfig.areaConfigs =
                new ArrayList<android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig>(
                        Arrays.asList(getTestHidlAreaConfig()));
        HidlHalPropConfig halPropConfig = new HidlHalPropConfig(hidlVehiclePropConfig);

        assertThat(halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(
                TEST_AREA_ID).getAccess()).isEqualTo(halPropConfig.getAccess());
    }

    @Test
    public void testToCarPropertyConfig_aidlAreaConfigWithInvalidAccessPopulatedWithGlobalAccess() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[] {
            getTestAidlAreaConfig(),
            getTestInvalidAidlAreaConfig()
        };
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        CarPropertyConfig<?> carPropertyConfig = halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs);
        assertThat(carPropertyConfig.getAccess()).isEqualTo(TEST_ACCESS);

        AreaIdConfig<?> areaIdConfig1 = carPropertyConfig.getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig1).isNotNull();
        assertThat(areaIdConfig1.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(areaIdConfig1.getAccess()).isEqualTo(TEST_ACCESS);

        AreaIdConfig<?> areaIdConfig2 = carPropertyConfig.getAreaIdConfig(TEST_ALTERNATE_AREA_ID);
        assertThat(areaIdConfig2).isNotNull();
        assertThat(areaIdConfig2.getAreaId()).isEqualTo(TEST_ALTERNATE_AREA_ID);
        assertThat(areaIdConfig2.getAccess()).isEqualTo(TEST_ACCESS);
    }

    @Test
    public void testToCarPropertyConfig_configArrayMatchesSupportedEnumValues() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        for (Integer propId: CONFIG_ARRAY_DEFINES_SUPPORTED_ENUM_VALUES) {
            aidlVehiclePropConfig.prop = propId;
            HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);
            assertThat(halPropConfig.toCarPropertyConfig(
                    GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(
                        TEST_AREA_ID).getSupportedEnumValues())
                        .containsExactlyElementsIn(TEST_CONFIG_ARRAY_LIST);
        }
    }

    @Test
    public void testToCarPropertyConfig_aidlSupportVariableUpdateRate() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        assertThat(halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(
                TEST_AREA_ID).isVariableUpdateRateSupported()).isTrue();
    }

    @Test
    public void testToCarPropertyConfig_hidlSupportVariableUpdateRate() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropConfig hidlConfig =
                getTestHidlPropConfig();
        hidlConfig.areaConfigs =
                new ArrayList<android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig>(
                        Arrays.asList(getTestHidlAreaConfig()));
        HidlHalPropConfig halPropConfig = new HidlHalPropConfig(hidlConfig);

        assertThat(halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(
                TEST_AREA_ID).isVariableUpdateRateSupported()).isFalse();
    }

    @Test
    public void testToCarPropertyConfig_populatesVendorAreaType() {
        VehiclePropConfig aidlVehiclePropConfig = getTestVendorAidlPropConfig();
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        CarPropertyConfig<?> carPropertyConfig = halPropConfig.toCarPropertyConfig(
                VENDOR_INTEGER_VEC_PROP_ID, mPropertyHalServiceConfigs);
        assertThat(carPropertyConfig.getPropertyId()).isEqualTo(VENDOR_INTEGER_VEC_PROP_ID);
        assertThat(carPropertyConfig.getAreaType())
                .isEqualTo(VehicleAreaType.VEHICLE_AREA_TYPE_VENDOR);
    }

    @Test
    public void testToCarPropertyConfig_hasMinMaxSupportedValue_Int32() {
        var vehiclePropConfig = getTestAidlPropConfig();
        vehiclePropConfig.areaConfigs = new VehicleAreaConfig[] {
            getTestAidlAreaConfig(),
        };

        var carPropertyConfig = new AidlHalPropConfig(vehiclePropConfig)
                .toCarPropertyConfig(GLOBAL_INTEGER_PROP_ID,
                        mPropertyHalServiceConfigs);

        var areaIdConfig = carPropertyConfig.getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig.hasMinSupportedValue()).isTrue();
        assertThat(areaIdConfig.hasMaxSupportedValue()).isTrue();
    }

    @Test
    public void testToCarPropertyConfig_hasMinMaxSupportedValue_Int64() {
        var vehiclePropConfig = getTestAidlPropConfig();
        vehiclePropConfig.prop = GLOBAL_LONG_PROP_ID;
        var areaConfig = getTestAidlAreaConfig();
        areaConfig.minInt64Value = MIN_INT64_VALUE;
        areaConfig.maxInt64Value = MAX_INT64_VALUE;
        vehiclePropConfig.areaConfigs = new VehicleAreaConfig[] {areaConfig};

        var carPropertyConfig = new AidlHalPropConfig(vehiclePropConfig)
                .toCarPropertyConfig(GLOBAL_LONG_PROP_ID,
                        mPropertyHalServiceConfigs);

        var areaIdConfig = carPropertyConfig.getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig.hasMinSupportedValue()).isTrue();
        assertThat(areaIdConfig.hasMaxSupportedValue()).isTrue();
    }

    @Test
    public void testToCarPropertyConfig_hasMinMaxSupportedValue_Float() {
        var vehiclePropConfig = getTestAidlPropConfig();
        vehiclePropConfig.prop = GLOBAL_FLOAT_PROP_ID;
        var areaConfig = getTestAidlAreaConfig();
        areaConfig.minFloatValue = MIN_FLOAT_VALUE;
        areaConfig.maxFloatValue = MAX_FLOAT_VALUE;
        vehiclePropConfig.areaConfigs = new VehicleAreaConfig[] {areaConfig};

        var carPropertyConfig = new AidlHalPropConfig(vehiclePropConfig)
                .toCarPropertyConfig(GLOBAL_FLOAT_PROP_ID,
                        mPropertyHalServiceConfigs);

        var areaIdConfig = carPropertyConfig.getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig.hasMinSupportedValue()).isTrue();
        assertThat(areaIdConfig.hasMaxSupportedValue()).isTrue();
    }

    @Test
    public void testToCarPropertyConfig_noMinMaxSupportedValue() {
        var vehiclePropConfig = getTestAidlPropConfig();
        var areaConfig = getTestAidlAreaConfig();
        areaConfig.minInt32Value = 0;
        areaConfig.maxInt32Value = 0;
        vehiclePropConfig.areaConfigs = new VehicleAreaConfig[] {areaConfig};

        var carPropertyConfig = new AidlHalPropConfig(vehiclePropConfig)
                .toCarPropertyConfig(GLOBAL_INTEGER_PROP_ID,
                        mPropertyHalServiceConfigs);

        var areaIdConfig = carPropertyConfig.getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig.hasMinSupportedValue()).isFalse();
        assertThat(areaIdConfig.hasMaxSupportedValue()).isFalse();
    }

    @Test
    public void testToCarPropertyConfig_hasSupportedValuesList_specifyEnums() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        assertThat(halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(
                TEST_AREA_ID).hasSupportedValuesList()).isTrue();
    }

    @Test
    public void testToCarPropertyConfig_hasSupportedValuesList_autoPopulateEnums() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.prop = VehicleProperty.ENGINE_OIL_LEVEL;
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        aidlVehiclePropConfig.areaConfigs[0].supportedEnumValues = null;
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        assertThat(halPropConfig.toCarPropertyConfig(
                VehicleProperty.ENGINE_OIL_LEVEL, mPropertyHalServiceConfigs).getAreaIdConfig(
                TEST_AREA_ID).hasSupportedValuesList()).isTrue();
    }

    @Test
    public void testToCarPropertyConfig_hasSupportedValuesList_noEnums() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        var areaConfig = getTestAidlAreaConfig();
        areaConfig.supportedEnumValues = null;
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{areaConfig};
        HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

        assertThat(halPropConfig.toCarPropertyConfig(
                GLOBAL_INTEGER_PROP_ID, mPropertyHalServiceConfigs).getAreaIdConfig(
                TEST_AREA_ID).hasSupportedValuesList()).isFalse();
    }

    @Test
    public void testToCarPropertyConfig_hasSupportedValuesList_supportedValuesInConfig() {
        VehiclePropConfig aidlVehiclePropConfig = getTestAidlPropConfig();
        aidlVehiclePropConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        aidlVehiclePropConfig.areaConfigs[0].supportedEnumValues = null;

        for (Integer propId: CONFIG_ARRAY_DEFINES_SUPPORTED_ENUM_VALUES) {
            aidlVehiclePropConfig.prop = propId;
            HalPropConfig halPropConfig = new AidlHalPropConfig(aidlVehiclePropConfig);

            assertThat(halPropConfig.toCarPropertyConfig(propId, mPropertyHalServiceConfigs)
                    .getAreaIdConfig(TEST_AREA_ID).hasSupportedValuesList()).isTrue();
        }
    }

    @Test
    public void testToCarPropertyConfig_parseHasSupportedValueInfo() {
        var vehiclePropConfig = getTestAidlPropConfig();
        vehiclePropConfig.prop = GLOBAL_LONG_PROP_ID;
        VehicleAreaConfig areaConfig = new VehicleAreaConfig();
        areaConfig.access = TEST_ACCESS;
        areaConfig.areaId = TEST_AREA_ID;
        areaConfig.hasSupportedValueInfo = new HasSupportedValueInfo();
        areaConfig.hasSupportedValueInfo.hasMinSupportedValue = true;
        areaConfig.hasSupportedValueInfo.hasMaxSupportedValue = true;
        areaConfig.hasSupportedValueInfo.hasSupportedValuesList = true;
        vehiclePropConfig.areaConfigs = new VehicleAreaConfig[] {areaConfig};

        var carPropertyConfig = new AidlHalPropConfig(vehiclePropConfig)
                .toCarPropertyConfig(GLOBAL_LONG_PROP_ID,
                        mPropertyHalServiceConfigs);

        var areaIdConfig = carPropertyConfig.getAreaIdConfig(TEST_AREA_ID);
        assertThat(areaIdConfig.hasMinSupportedValue()).isTrue();
        assertThat(areaIdConfig.hasMaxSupportedValue()).isTrue();
        assertThat(areaIdConfig.hasSupportedValuesList()).isTrue();
    }
}
