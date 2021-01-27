/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
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

package me.proton.core.auth.domain.usecase

import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.crypto.common.srp.SrpCrypto
import me.proton.core.key.domain.extension.primary
import me.proton.core.network.domain.session.SessionId
import me.proton.core.network.domain.session.SessionProvider
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.user.domain.entity.UserAddressKey
import me.proton.core.user.domain.entity.UserKey
import me.proton.core.user.domain.entity.firstOrDefault
import me.proton.core.user.domain.repository.DomainRepository
import javax.inject.Inject

/**
 * Setup a new primary [UserKey], [UserAddress], and [UserAddressKey].
 */
class SetupPrimaryKeys @Inject constructor(
    private val userManager: UserManager,
    private val authRepository: AuthRepository,
    private val domainRepository: DomainRepository,
    private val sessionProvider: SessionProvider,
    private val srpCrypto: SrpCrypto
) {
    suspend operator fun invoke(
        sessionId: SessionId,
        password: ByteArray
    ) {
        val userId = sessionProvider.getUserId(sessionId)
        checkNotNull(userId) { "Cannot get userId from sessionId = $sessionId" }

        val user = userManager.getUser(userId)
        val username = checkNotNull(user.name) { "Username is needed to setup primary keys." }

        val availableDomains = domainRepository.getAvailableDomains()
        val domain = availableDomains.firstOrDefault()

        if (user.keys.primary() == null) {
            val modulus = authRepository.randomModulus()
            val auth = srpCrypto.calculatePasswordVerifier(
                username = username,
                password = password,
                modulusId = modulus.modulusId,
                modulus = modulus.modulus
            )
            userManager.setupPrimaryKeys(userId, username, domain, auth, password)
        }
    }
}
