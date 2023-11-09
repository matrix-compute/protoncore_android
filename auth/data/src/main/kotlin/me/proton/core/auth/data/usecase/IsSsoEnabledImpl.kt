/*
 * Copyright (c) 2023 Proton AG
 * This file is part of Proton AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.auth.data.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import me.proton.core.auth.data.R
import me.proton.core.auth.domain.usecase.IsSsoEnabled
import me.proton.core.featureflag.domain.ExperimentalProtonFeatureFlag
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureId
import javax.inject.Inject

class IsSsoEnabledImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val featureFlagManager: FeatureFlagManager
) : IsSsoEnabled {

    override fun invoke(): Boolean {
        return isLocalEnabled() && isRemoteEnabled()
    }

    private fun isLocalEnabled(): Boolean {
        return context.resources.getBoolean(R.bool.core_feature_auth_sso_enabled)
    }

    @OptIn(ExperimentalProtonFeatureFlag::class)
    private fun isRemoteEnabled(): Boolean {
        return featureFlagManager.getValue(userId = null, featureId)
    }

    internal companion object {
        val featureId = FeatureId("ExternalSSO")
    }
}
