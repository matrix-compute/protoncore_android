/*
 * Copyright (c) 2022 Proton Technologies AG
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

import studio.forface.easygradle.dsl.*
import studio.forface.easygradle.dsl.android.*

plugins {
    protonAndroidLibrary
    protonDagger
}

publishOption.shouldBePublishedAsLib = true

dependencies {

    implementation(

        project(Module.accountDomain),
        project(Module.accountManagerDomain),
        project(Module.crypto),
        project(Module.cryptoValidatorData),
        project(Module.domain),
        project(Module.kotlinUtil),
        project(Module.presentation),

        // Kotlin
        `coroutines-android`,

        // Android
        `android-ktx`,
        `activity`,
        `hilt-androidx-annotations`,
        `lifecycle-runtime`,
        `lifecycle-viewModel`,
        `material`,
        `startup-runtime`,
    )

    testImplementation(project(Module.androidTest), project(Module.networkDomain))
    androidTestImplementation(project(Module.androidInstrumentedTest))
}