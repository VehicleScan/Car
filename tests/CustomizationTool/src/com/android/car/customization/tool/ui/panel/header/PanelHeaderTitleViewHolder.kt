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

package com.android.car.customization.tool.ui.panel.header

import android.view.View
import android.widget.TextView
import com.android.car.customization.tool.R
import com.android.car.customization.tool.domain.Action
import com.android.car.customization.tool.domain.panel.PanelHeaderItem

internal class PanelHeaderTitleViewHolder(view: View) : PanelHeaderItemViewHolder(view) {

    private val textView = itemView.requireViewById<TextView>(R.id.panel_header_title)

    override fun bindTo(model: PanelHeaderItem, handleAction: (Action) -> Unit) {
        model as PanelHeaderItem.Title

        textView.text = model.text
    }
}
