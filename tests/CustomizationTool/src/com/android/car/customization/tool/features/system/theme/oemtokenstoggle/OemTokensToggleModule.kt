/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.customization.tool.features.system.theme.oemtokenstoggle

import android.content.Context
import android.content.om.OverlayInfo
import android.content.om.OverlayManager
import android.os.UserHandle
import com.android.car.customization.tool.R
import com.android.car.customization.tool.di.MenuActionKey
import com.android.car.customization.tool.di.UIContext
import com.android.car.customization.tool.domain.menu.MenuActionReducer
import com.android.car.customization.tool.domain.menu.MenuItem
import com.android.car.customization.tool.features.common.isValid
import com.android.car.customization.tool.features.system.theme.submenu.SystemThemeMenu
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet

/**
 * OEM Design Tokens toggle.
 *
 * Adds a [MenuItem.Switch] to the menu that toggles the RRO related to OEM Design Tokens.
 * The module also provides the reducer for the item.
 */
@Module
internal class OemTokensToggleModule {

    @Provides
    @SystemThemeMenu
    @IntoSet
    fun provideOemTokenSwitch(
        overlayManager: OverlayManager,
    ): MenuItem {
        val displayText = R.string.menu_system_theme_design_tokens_switch
        val overlaysInfo: List<OverlayInfo> =
            TOKENS_RROS
                .mapNotNull { (rroPackage, userHandle) ->
                    overlayManager.getOverlayInfo(rroPackage, userHandle)
                }.filter { overlayInfo ->
                    overlayInfo.isValid()
                }
        val isEnabled = overlaysInfo.isNotEmpty()
        val isChecked = isEnabled && overlaysInfo.all { it.isEnabled() }

        return MenuItem.Switch(
            displayTextRes = displayText,
            isEnabled = isEnabled,
            isChecked = isChecked,
            action = ToggleOemTokensAction(
                displayText,
                overlaysInfo.map {
                    TokenRro(
                        rroPackage = it.packageName,
                        userHandle = requireNotNull(TOKENS_RROS[it.packageName])
                    )
                },
                !isChecked
            )
        )
    }

    @Provides
    @IntoMap
    @MenuActionKey(ToggleOemTokensAction::class)
    fun provideOemTokensToggleActionReducer(
        @UIContext context: Context,
        overlayManager: OverlayManager,
    ): MenuActionReducer = OemTokensToggleReducer(context, overlayManager)

    private companion object {
        val TOKENS_RROS: Map<String, UserHandle> = mapOf(
            "oem.brand.model.android.rro" to UserHandle.CURRENT,
            "oem.brand.model.plugin.rro" to UserHandle.CURRENT,
            "oem.brand.model.rro" to UserHandle.CURRENT
        )
    }
}
