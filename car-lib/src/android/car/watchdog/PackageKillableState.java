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
import android.annotation.UserIdInt;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.AnnotationValidations;
import com.android.car.internal.util.DataClass;

/**
 * Killable state for a package.
 *
 * @hide
 */
@SystemApi
@DataClass(genToString = true, genHiddenConstructor = true, genHiddenConstDefs = true)
public final class PackageKillableState implements Parcelable {
    /**
     * A package is killable.
     */
    @KillableState
    public static final int KILLABLE_STATE_YES = 1;

    /**
     * A package is not killable.
     */
    @KillableState
    public static final int KILLABLE_STATE_NO = 2;

    /**
     * A package is never killable i.e. its setting cannot be updated.
     */
    @KillableState
    public static final int KILLABLE_STATE_NEVER = 3;

    /**
     * Name of the package.
     */
    private @NonNull String mPackageName;

    /**
     * Id of the user.
     */
    private @UserIdInt int mUserId;

    /**
     * Killable state of the user's package.
     */
    private @KillableState int mKillableState;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/watchdog/PackageKillableState.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /** @hide */
    @android.annotation.IntDef(prefix = "KILLABLE_STATE_", value = {
        KILLABLE_STATE_YES,
        KILLABLE_STATE_NO,
        KILLABLE_STATE_NEVER
    })
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
    @DataClass.Generated.Member
    public @interface KillableState {}

    /** @hide */
    @DataClass.Generated.Member
    public static String killableStateToString(@KillableState int value) {
        switch (value) {
            case KILLABLE_STATE_YES:
                    return "KILLABLE_STATE_YES";
            case KILLABLE_STATE_NO:
                    return "KILLABLE_STATE_NO";
            case KILLABLE_STATE_NEVER:
                    return "KILLABLE_STATE_NEVER";
            default: return Integer.toHexString(value);
        }
    }

    /**
     * Creates a new PackageKillableState.
     *
     * @param packageName
     *   Name of the package.
     * @param userId
     *   Id of the user.
     * @param killableState
     *   Killable state of the user's package.
     * @hide
     */
    @DataClass.Generated.Member
    public PackageKillableState(
            @NonNull String packageName,
            @UserIdInt int userId,
            @KillableState int killableState) {
        this.mPackageName = packageName;
        AnnotationValidations.validate(
                NonNull.class, null, mPackageName);
        this.mUserId = userId;
        AnnotationValidations.validate(
                UserIdInt.class, null, mUserId);
        this.mKillableState = killableState;

        if (!(mKillableState == KILLABLE_STATE_YES)
                && !(mKillableState == KILLABLE_STATE_NO)
                && !(mKillableState == KILLABLE_STATE_NEVER)) {
            throw new java.lang.IllegalArgumentException(
                    "killableState was " + mKillableState + " but must be one of: "
                            + "KILLABLE_STATE_YES(" + KILLABLE_STATE_YES + "), "
                            + "KILLABLE_STATE_NO(" + KILLABLE_STATE_NO + "), "
                            + "KILLABLE_STATE_NEVER(" + KILLABLE_STATE_NEVER + ")");
        }


        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Name of the package.
     */
    @DataClass.Generated.Member
    public @NonNull String getPackageName() {
        return mPackageName;
    }

    /**
     * Id of the user.
     */
    @DataClass.Generated.Member
    public @UserIdInt int getUserId() {
        return mUserId;
    }

    /**
     * Killable state of the user's package.
     */
    @DataClass.Generated.Member
    public @KillableState int getKillableState() {
        return mKillableState;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "PackageKillableState { " +
                "packageName = " + mPackageName + ", " +
                "userId = " + mUserId + ", " +
                "killableState = " + killableStateToString(mKillableState) +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeString(mPackageName);
        dest.writeInt(mUserId);
        dest.writeInt(mKillableState);
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ PackageKillableState(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        String packageName = in.readString();
        int userId = in.readInt();
        int killableState = in.readInt();

        this.mPackageName = packageName;
        AnnotationValidations.validate(
                NonNull.class, null, mPackageName);
        this.mUserId = userId;
        AnnotationValidations.validate(
                UserIdInt.class, null, mUserId);
        this.mKillableState = killableState;

        if (!(mKillableState == KILLABLE_STATE_YES)
                && !(mKillableState == KILLABLE_STATE_NO)
                && !(mKillableState == KILLABLE_STATE_NEVER)) {
            throw new java.lang.IllegalArgumentException(
                    "killableState was " + mKillableState + " but must be one of: "
                            + "KILLABLE_STATE_YES(" + KILLABLE_STATE_YES + "), "
                            + "KILLABLE_STATE_NO(" + KILLABLE_STATE_NO + "), "
                            + "KILLABLE_STATE_NEVER(" + KILLABLE_STATE_NEVER + ")");
        }


        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<PackageKillableState> CREATOR
            = new Parcelable.Creator<PackageKillableState>() {
        @Override
        public PackageKillableState[] newArray(int size) {
            return new PackageKillableState[size];
        }

        @Override
        public PackageKillableState createFromParcel(@NonNull android.os.Parcel in) {
            return new PackageKillableState(in);
        }
    };

    @DataClass.Generated(
            time = 1721755741237L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android/car/watchdog/PackageKillableState.java",
            inputSignatures = "public static final @android.car.watchdog.PackageKillableState.KillableState int KILLABLE_STATE_YES\npublic static final @android.car.watchdog.PackageKillableState.KillableState int KILLABLE_STATE_NO\npublic static final @android.car.watchdog.PackageKillableState.KillableState int KILLABLE_STATE_NEVER\nprivate @android.annotation.NonNull java.lang.String mPackageName\nprivate @android.annotation.UserIdInt int mUserId\nprivate @android.car.watchdog.PackageKillableState.KillableState int mKillableState\nclass PackageKillableState extends java.lang.Object implements [android.os.Parcelable]\n@com.android.car.internal.util.DataClass(genToString=true, genHiddenConstructor=true, genHiddenConstDefs=true)")
    @Deprecated
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
