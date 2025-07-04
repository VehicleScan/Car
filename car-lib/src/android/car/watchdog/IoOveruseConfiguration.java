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

package android.car.watchdog;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.AnnotationValidations;
import com.android.car.internal.util.DataClass;

import java.util.List;
import java.util.Map;

/**
 * Disk I/O overuse configuration for a component.
 *
 * @hide
 */
@SystemApi
@DataClass(genToString = true, genBuilder = true, genHiddenConstDefs = true)
public final class IoOveruseConfiguration implements Parcelable {
    /**
     * Component level thresholds.
     *
     * <p>These are applied to packages that are not covered by the package specific thresholds or
     * application category specific thresholds. For third-party component, only component level
     * thresholds must be provided and other thresholds are not applicable.
     */
    private @NonNull PerStateBytes mComponentLevelThresholds;

    /**
     * Package specific thresholds only for system and vendor packages.
     *
     * NOTE: For packages that share a UID, the package name should be the shared package name
     * because the I/O usage is aggregated for all packages under the shared UID. The shared
     * package names should have the prefix 'shared:'.
     *
     * <p>System component must provide package specific thresholds only for system packages.
     * <p>Vendor component must provide package specific thresholds only for vendor packages.
     */
    private @NonNull Map<String, PerStateBytes> mPackageSpecificThresholds;

    /**
     * Application category specific thresholds.
     *
     * <p>The key must be one of the {@link ResourceOveruseConfiguration#ApplicationCategoryType}
     * constants.
     *
     * <p>These are applied when package specific thresholds are not provided for a package and a
     * package is covered by one of the
     * {@link ResourceOveruseConfiguration#ApplicationCategoryType}. These thresholds must be
     * provided only by the vendor component.
     */
    private @NonNull Map<String, PerStateBytes> mAppCategorySpecificThresholds;

    /**
     * List of system-wide thresholds used to detect overall disk I/O overuse.
     *
     * <p>These thresholds must be provided only by the system component.
     */
    private @NonNull List<IoOveruseAlertThreshold> mSystemWideThresholds;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/watchdog/IoOveruseConfiguration.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ IoOveruseConfiguration(
            @NonNull PerStateBytes componentLevelThresholds,
            @NonNull Map<String,PerStateBytes> packageSpecificThresholds,
            @NonNull Map<String,PerStateBytes> appCategorySpecificThresholds,
            @NonNull List<IoOveruseAlertThreshold> systemWideThresholds) {
        this.mComponentLevelThresholds = componentLevelThresholds;
        AnnotationValidations.validate(
                NonNull.class, null, mComponentLevelThresholds);
        this.mPackageSpecificThresholds = packageSpecificThresholds;
        AnnotationValidations.validate(
                NonNull.class, null, mPackageSpecificThresholds);
        this.mAppCategorySpecificThresholds = appCategorySpecificThresholds;
        AnnotationValidations.validate(
                NonNull.class, null, mAppCategorySpecificThresholds);
        this.mSystemWideThresholds = systemWideThresholds;
        AnnotationValidations.validate(
                NonNull.class, null, mSystemWideThresholds);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Component level thresholds.
     *
     * <p>These are applied to packages that are not covered by the package specific thresholds or
     * application category specific thresholds. For third-party component, only component level
     * thresholds must be provided and other thresholds are not applicable.
     */
    @DataClass.Generated.Member
    public @NonNull PerStateBytes getComponentLevelThresholds() {
        return mComponentLevelThresholds;
    }

    /**
     * Package specific thresholds only for system and vendor packages.
     *
     * NOTE: For packages that share a UID, the package name should be the shared package name
     * because the I/O usage is aggregated for all packages under the shared UID. The shared
     * package names should have the prefix 'shared:'.
     *
     * <p>System component must provide package specific thresholds only for system packages.
     * <p>Vendor component must provide package specific thresholds only for vendor packages.
     */
    @DataClass.Generated.Member
    public @NonNull Map<String,PerStateBytes> getPackageSpecificThresholds() {
        return mPackageSpecificThresholds;
    }

    /**
     * Application category specific thresholds.
     *
     * <p>The key must be one of the {@link ResourceOveruseConfiguration#ApplicationCategoryType}
     * constants.
     *
     * <p>These are applied when package specific thresholds are not provided for a package and a
     * package is covered by one of the
     * {@link ResourceOveruseConfiguration#ApplicationCategoryType}. These thresholds must be
     * provided only by the vendor component.
     */
    @DataClass.Generated.Member
    public @NonNull Map<String,PerStateBytes> getAppCategorySpecificThresholds() {
        return mAppCategorySpecificThresholds;
    }

    /**
     * List of system-wide thresholds used to detect overall disk I/O overuse.
     *
     * <p>These thresholds must be provided only by the system component.
     */
    @DataClass.Generated.Member
    public @NonNull List<IoOveruseAlertThreshold> getSystemWideThresholds() {
        return mSystemWideThresholds;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "IoOveruseConfiguration { " +
                "componentLevelThresholds = " + mComponentLevelThresholds + ", " +
                "packageSpecificThresholds = " + mPackageSpecificThresholds + ", " +
                "appCategorySpecificThresholds = " + mAppCategorySpecificThresholds + ", " +
                "systemWideThresholds = " + mSystemWideThresholds +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeTypedObject(mComponentLevelThresholds, flags);
        dest.writeMap(mPackageSpecificThresholds);
        dest.writeMap(mAppCategorySpecificThresholds);
        dest.writeParcelableList(mSystemWideThresholds, flags);
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ IoOveruseConfiguration(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        PerStateBytes componentLevelThresholds = (PerStateBytes) in.readTypedObject(PerStateBytes.CREATOR);
        Map<String,PerStateBytes> packageSpecificThresholds = new java.util.LinkedHashMap<>();
        in.readMap(packageSpecificThresholds, PerStateBytes.class.getClassLoader());
        Map<String,PerStateBytes> appCategorySpecificThresholds = new java.util.LinkedHashMap<>();
        in.readMap(appCategorySpecificThresholds, PerStateBytes.class.getClassLoader());
        List<IoOveruseAlertThreshold> systemWideThresholds = new java.util.ArrayList<>();
        in.readParcelableList(systemWideThresholds, IoOveruseAlertThreshold.class.getClassLoader());

        this.mComponentLevelThresholds = componentLevelThresholds;
        AnnotationValidations.validate(
                NonNull.class, null, mComponentLevelThresholds);
        this.mPackageSpecificThresholds = packageSpecificThresholds;
        AnnotationValidations.validate(
                NonNull.class, null, mPackageSpecificThresholds);
        this.mAppCategorySpecificThresholds = appCategorySpecificThresholds;
        AnnotationValidations.validate(
                NonNull.class, null, mAppCategorySpecificThresholds);
        this.mSystemWideThresholds = systemWideThresholds;
        AnnotationValidations.validate(
                NonNull.class, null, mSystemWideThresholds);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<IoOveruseConfiguration> CREATOR
            = new Parcelable.Creator<IoOveruseConfiguration>() {
        @Override
        public IoOveruseConfiguration[] newArray(int size) {
            return new IoOveruseConfiguration[size];
        }

        @Override
        public IoOveruseConfiguration createFromParcel(@NonNull android.os.Parcel in) {
            return new IoOveruseConfiguration(in);
        }
    };

    /**
     * A builder for {@link IoOveruseConfiguration}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @NonNull PerStateBytes mComponentLevelThresholds;
        private @NonNull Map<String,PerStateBytes> mPackageSpecificThresholds;
        private @NonNull Map<String,PerStateBytes> mAppCategorySpecificThresholds;
        private @NonNull List<IoOveruseAlertThreshold> mSystemWideThresholds;

        private long mBuilderFieldsSet = 0L;

        /**
         * Creates a new Builder.
         *
         * @param componentLevelThresholds
         *   Component level thresholds.
         *
         *   <p>These are applied to packages that are not covered by the package specific thresholds or
         *   application category specific thresholds. For third-party component, only component level
         *   thresholds must be provided and other thresholds are not applicable.
         * @param packageSpecificThresholds
         *   Package specific thresholds only for system and vendor packages.
         *
         *   NOTE: For packages that share a UID, the package name should be the shared package name
         *   because the I/O usage is aggregated for all packages under the shared UID. The shared
         *   package names should have the prefix 'shared:'.
         *
         *   <p>System component must provide package specific thresholds only for system packages.
         *   <p>Vendor component must provide package specific thresholds only for vendor packages.
         * @param appCategorySpecificThresholds
         *   Application category specific thresholds.
         *
         *   <p>The key must be one of the {@link ResourceOveruseConfiguration#ApplicationCategoryType}
         *   constants.
         *
         *   <p>These are applied when package specific thresholds are not provided for a package and a
         *   package is covered by one of the
         *   {@link ResourceOveruseConfiguration#ApplicationCategoryType}. These thresholds must be
         *   provided only by the vendor component.
         * @param systemWideThresholds
         *   List of system-wide thresholds used to detect overall disk I/O overuse.
         *
         *   <p>These thresholds must be provided only by the system component.
         */
        public Builder(
                @NonNull PerStateBytes componentLevelThresholds,
                @NonNull Map<String,PerStateBytes> packageSpecificThresholds,
                @NonNull Map<String,PerStateBytes> appCategorySpecificThresholds,
                @NonNull List<IoOveruseAlertThreshold> systemWideThresholds) {
            mComponentLevelThresholds = componentLevelThresholds;
            AnnotationValidations.validate(
                    NonNull.class, null, mComponentLevelThresholds);
            mPackageSpecificThresholds = packageSpecificThresholds;
            AnnotationValidations.validate(
                    NonNull.class, null, mPackageSpecificThresholds);
            mAppCategorySpecificThresholds = appCategorySpecificThresholds;
            AnnotationValidations.validate(
                    NonNull.class, null, mAppCategorySpecificThresholds);
            mSystemWideThresholds = systemWideThresholds;
            AnnotationValidations.validate(
                    NonNull.class, null, mSystemWideThresholds);
        }

        /**
         * Component level thresholds.
         *
         * <p>These are applied to packages that are not covered by the package specific thresholds or
         * application category specific thresholds. For third-party component, only component level
         * thresholds must be provided and other thresholds are not applicable.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setComponentLevelThresholds(@NonNull PerStateBytes value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mComponentLevelThresholds = value;
            return this;
        }

        /**
         * Package specific thresholds only for system and vendor packages.
         *
         * NOTE: For packages that share a UID, the package name should be the shared package name
         * because the I/O usage is aggregated for all packages under the shared UID. The shared
         * package names should have the prefix 'shared:'.
         *
         * <p>System component must provide package specific thresholds only for system packages.
         * <p>Vendor component must provide package specific thresholds only for vendor packages.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setPackageSpecificThresholds(@NonNull Map<String,PerStateBytes> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mPackageSpecificThresholds = value;
            return this;
        }

        /** @see #setPackageSpecificThresholds */
        @DataClass.Generated.Member
        public @NonNull Builder addPackageSpecificThresholds(@NonNull String key, @NonNull PerStateBytes value) {
            // You can refine this method's name by providing item's singular name, e.g.:
            // @DataClass.PluralOf("item")) mItems = ...

            if (mPackageSpecificThresholds == null) setPackageSpecificThresholds(new java.util.LinkedHashMap());
            mPackageSpecificThresholds.put(key, value);
            return this;
        }

        /**
         * Application category specific thresholds.
         *
         * <p>The key must be one of the {@link ResourceOveruseConfiguration#ApplicationCategoryType}
         * constants.
         *
         * <p>These are applied when package specific thresholds are not provided for a package and a
         * package is covered by one of the
         * {@link ResourceOveruseConfiguration#ApplicationCategoryType}. These thresholds must be
         * provided only by the vendor component.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setAppCategorySpecificThresholds(@NonNull Map<String,PerStateBytes> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mAppCategorySpecificThresholds = value;
            return this;
        }

        /** @see #setAppCategorySpecificThresholds */
        @DataClass.Generated.Member
        public @NonNull Builder addAppCategorySpecificThresholds(@NonNull String key, @NonNull PerStateBytes value) {
            // You can refine this method's name by providing item's singular name, e.g.:
            // @DataClass.PluralOf("item")) mItems = ...

            if (mAppCategorySpecificThresholds == null) setAppCategorySpecificThresholds(new java.util.LinkedHashMap());
            mAppCategorySpecificThresholds.put(key, value);
            return this;
        }

        /**
         * List of system-wide thresholds used to detect overall disk I/O overuse.
         *
         * <p>These thresholds must be provided only by the system component.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setSystemWideThresholds(@NonNull List<IoOveruseAlertThreshold> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8;
            mSystemWideThresholds = value;
            return this;
        }

        /** @see #setSystemWideThresholds */
        @DataClass.Generated.Member
        public @NonNull Builder addSystemWideThresholds(@NonNull IoOveruseAlertThreshold value) {
            // You can refine this method's name by providing item's singular name, e.g.:
            // @DataClass.PluralOf("item")) mItems = ...

            if (mSystemWideThresholds == null) setSystemWideThresholds(new java.util.ArrayList<>());
            mSystemWideThresholds.add(value);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull IoOveruseConfiguration build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x10; // Mark builder used

            IoOveruseConfiguration o = new IoOveruseConfiguration(
                    mComponentLevelThresholds,
                    mPackageSpecificThresholds,
                    mAppCategorySpecificThresholds,
                    mSystemWideThresholds);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x10) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1721754505645L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android/car/watchdog/IoOveruseConfiguration.java",
            inputSignatures = "private @android.annotation.NonNull android.car.watchdog.PerStateBytes mComponentLevelThresholds\nprivate @android.annotation.NonNull java.util.Map<java.lang.String,android.car.watchdog.PerStateBytes> mPackageSpecificThresholds\nprivate @android.annotation.NonNull java.util.Map<java.lang.String,android.car.watchdog.PerStateBytes> mAppCategorySpecificThresholds\nprivate @android.annotation.NonNull java.util.List<android.car.watchdog.IoOveruseAlertThreshold> mSystemWideThresholds\nclass IoOveruseConfiguration extends java.lang.Object implements [android.os.Parcelable]\n@com.android.car.internal.util.DataClass(genToString=true, genBuilder=true, genHiddenConstDefs=true)")
    @Deprecated
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
