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

package me.proton.core.account.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.account.data.entity.AccountMetadataEntity
import me.proton.core.account.data.entity.HumanVerificationDetailsEntity
import me.proton.core.account.data.entity.SessionDetailsEntity
import me.proton.core.account.data.extension.toAccountEntity
import me.proton.core.account.data.extension.toSessionEntity
import me.proton.core.account.domain.entity.Account
import me.proton.core.account.domain.entity.AccountDetails
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.account.domain.entity.SessionDetails
import me.proton.core.account.domain.entity.SessionState
import me.proton.core.account.domain.repository.AccountRepository
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.crypto.common.keystore.encryptWith
import me.proton.core.data.db.CommonConverters
import me.proton.core.domain.entity.Product
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.humanverification.HumanVerificationDetails
import me.proton.core.network.domain.session.Session
import me.proton.core.network.domain.session.SessionId
import me.proton.core.util.kotlin.exhaustive

@Suppress("TooManyFunctions")
class AccountRepositoryImpl(
    private val product: Product,
    private val db: AccountDatabase,
    private val keyStoreCrypto: KeyStoreCrypto
) : AccountRepository {

    private val accountDao = db.accountDao()
    private val sessionDao = db.sessionDao()
    private val accountMetadataDao = db.accountMetadataDao()
    private val humanVerificationDetailsDao = db.humanVerificationDetailsDao()
    private val sessionDetailsDao = db.sessionDetailsDao()

    // Accept 10 nested/concurrent state changes -> extraBufferCapacity.
    private val accountStateChanged = MutableSharedFlow<Account>(extraBufferCapacity = 10)
    private val sessionStateChanged = MutableSharedFlow<Account>(extraBufferCapacity = 10)

    private fun tryEmitAccountStateChanged(account: Account) {
        if (!accountStateChanged.tryEmit(account))
            throw IllegalStateException("Too many nested account state changes, extra buffer capacity exceeded.")
    }

    private fun tryEmitSessionStateChanged(account: Account) {
        if (!sessionStateChanged.tryEmit(account))
            throw IllegalStateException("Too many nested session state changes, extra buffer capacity exceeded.")
    }

    private suspend fun tryInsertOrUpdateAccountMetadata(userId: UserId) {
        db.inTransaction {
            accountDao.getByUserId(userId.id)?.let {
                accountMetadataDao.insertOrUpdate(
                    AccountMetadataEntity(
                        userId = userId.id,
                        product = product,
                        primaryAtUtc = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private suspend fun deleteAccountMetadata(userId: UserId) =
        accountMetadataDao.delete(userId.id, product)

    private suspend fun getAccountInfo(entity: AccountEntity): AccountDetails {
        val sessionId = entity.sessionId?.let { SessionId(it) }
        return AccountDetails(
            session = sessionId?.let { getSessionDetails(it) },
            humanVerification = sessionId?.let { getHumanVerificationDetails(it) }
        )
    }

    override fun getAccount(userId: UserId): Flow<Account?> =
        accountDao.findByUserId(userId.id)
            .map { account -> account?.toAccount(getAccountInfo(account)) }
            .distinctUntilChanged()

    override fun getAccount(sessionId: SessionId): Flow<Account?> =
        accountDao.findBySessionId(sessionId.id)
            .map { account -> account?.toAccount(getAccountInfo(account)) }
            .distinctUntilChanged()

    override fun getAccounts(): Flow<List<Account>> =
        accountDao.findAll()
            .map { it.map { account -> account.toAccount(getAccountInfo(account)) } }
            .distinctUntilChanged()

    override suspend fun getAccountOrNull(userId: UserId): Account? =
        accountDao.getByUserId(userId.id)?.let { account -> account.toAccount(getAccountInfo(account)) }

    override suspend fun getAccountOrNull(sessionId: SessionId): Account? =
        accountDao.getBySessionId(sessionId.id)?.let { account -> account.toAccount(getAccountInfo(account)) }

    override fun getSessions(): Flow<List<Session>> =
        sessionDao.findAll(product).map { list ->
            list.map { it.toSession(keyStoreCrypto) }
        }.distinctUntilChanged()

    override fun getSession(sessionId: SessionId): Flow<Session?> =
        sessionDao.findBySessionId(sessionId.id)
            .map { it?.toSession(keyStoreCrypto) }
            .distinctUntilChanged()

    override suspend fun getSessionOrNull(sessionId: SessionId): Session? =
        sessionDao.get(sessionId.id)?.toSession(keyStoreCrypto)

    override suspend fun getSessionIdOrNull(userId: UserId): SessionId? =
        sessionDao.getSessionId(userId.id)?.let { SessionId(it) }

    override suspend fun createOrUpdateAccountSession(account: Account, session: Session) {
        require(session.isValid()) {
            "Session is not valid: $session\n.At least sessionId.id, accessToken and refreshToken must be valid."
        }
        // Update/raise Initializing state without session.
        db.inTransaction {
            accountDao.insertOrUpdate(
                account.copy(
                    state = AccountState.NotReady,
                    sessionId = null,
                    sessionState = null
                ).toAccountEntity()
            )
        }
        // Update/raise provided state with session.
        db.inTransaction {
            val sessionState = account.sessionState ?: SessionState.Authenticated
            sessionDao.insertOrUpdate(session.toSessionEntity(account.userId, product, keyStoreCrypto))
            accountDao.addSession(account.userId.id, session.sessionId.id)
            account.details.session?.let { setSessionDetails(session.sessionId, it) }
            updateAccountState(account.userId, account.state)
            updateSessionState(session.sessionId, sessionState)
        }
    }

    override suspend fun deleteAccount(userId: UserId) =
        accountDao.delete(userId.id)

    override suspend fun deleteSession(sessionId: SessionId) {
        db.inTransaction {
            accountDao.removeSession(sessionId.id)
            sessionDao.delete(sessionId.id)
        }
    }

    override fun onAccountStateChanged(initialState: Boolean): Flow<Account> =
        accountStateChanged.asSharedFlow()
            .onSubscription { if (initialState) getAccounts().first().forEach { emit(it) } }
            .distinctUntilChanged()

    override fun onSessionStateChanged(initialState: Boolean): Flow<Account> =
        sessionStateChanged.asSharedFlow()
            .onSubscription { if (initialState) getAccounts().first().forEach { emit(it) } }
            .distinctUntilChanged()

    override suspend fun updateAccountState(userId: UserId, state: AccountState) {
        db.inTransaction {
            when (state) {
                AccountState.Ready -> tryInsertOrUpdateAccountMetadata(userId)
                AccountState.Disabled,
                AccountState.Removed,
                AccountState.NotReady,
                AccountState.TwoPassModeNeeded,
                AccountState.TwoPassModeSuccess,
                AccountState.TwoPassModeFailed,
                AccountState.CreateAddressNeeded,
                AccountState.CreateAddressSuccess,
                AccountState.CreateAddressFailed,
                AccountState.UnlockFailed -> deleteAccountMetadata(userId)
            }.exhaustive
            accountDao.updateAccountState(userId.id, state)
            getAccountOrNull(userId)?.let { tryEmitAccountStateChanged(it) }
        }
    }

    override suspend fun updateAccountState(sessionId: SessionId, state: AccountState) {
        getAccountOrNull(sessionId)?.let { updateAccountState(it.userId, state) }
    }

    override suspend fun updateSessionState(sessionId: SessionId, state: SessionState) {
        db.inTransaction {
            accountDao.updateSessionState(sessionId.id, state)
            getAccountOrNull(sessionId)?.let { tryEmitSessionStateChanged(it) }
        }
    }

    override suspend fun updateSessionScopes(sessionId: SessionId, scopes: List<String>) =
        sessionDao.updateScopes(sessionId.id, CommonConverters.fromListOfStringToString(scopes).orEmpty())

    override suspend fun updateSessionHeaders(sessionId: SessionId, tokenType: String?, tokenCode: String?) =
        sessionDao.updateHeaders(
            sessionId.id,
            tokenType?.encryptWith(keyStoreCrypto),
            tokenCode?.encryptWith(keyStoreCrypto)
        )

    override suspend fun updateSessionToken(sessionId: SessionId, accessToken: String, refreshToken: String) =
        sessionDao.updateToken(
            sessionId.id,
            accessToken.encryptWith(keyStoreCrypto),
            refreshToken.encryptWith(keyStoreCrypto)
        )

    override fun getPrimaryUserId(): Flow<UserId?> =
        accountMetadataDao.observeLatestPrimary(product).map { it?.let { UserId(it.userId) } }
            .distinctUntilChanged()

    override suspend fun setAsPrimary(userId: UserId) {
        db.inTransaction {
            val state = accountDao.getByUserId(userId.id)?.state
            check(state == AccountState.Ready) {
                "Account is not ${AccountState.Ready}, it cannot be set as primary."
            }
            tryInsertOrUpdateAccountMetadata(userId)
        }
    }

    override suspend fun getHumanVerificationDetails(sessionId: SessionId): HumanVerificationDetails? =
        humanVerificationDetailsDao.getBySessionId(sessionId.id)?.toHumanVerificationDetails()

    override suspend fun setHumanVerificationDetails(sessionId: SessionId, details: HumanVerificationDetails) =
        humanVerificationDetailsDao.insertOrUpdate(
            HumanVerificationDetailsEntity(
                sessionId = sessionId.id,
                verificationMethods = details.verificationMethods.map { method -> method.value },
                captchaVerificationToken = details.captchaVerificationToken
            )
        )

    override suspend fun updateHumanVerificationCompleted(sessionId: SessionId) =
        humanVerificationDetailsDao.delete(sessionId = sessionId.id)

    override suspend fun getSessionDetails(sessionId: SessionId): SessionDetails? =
        sessionDetailsDao.getBySessionId(sessionId.id)?.toSessionDetails()

    override suspend fun setSessionDetails(sessionId: SessionId, details: SessionDetails) =
        sessionDetailsDao.insertOrUpdate(
            SessionDetailsEntity(
                sessionId = sessionId.id,
                initialEventId = details.initialEventId,
                requiredAccountType = details.requiredAccountType,
                secondFactorEnabled = details.secondFactorEnabled,
                twoPassModeEnabled = details.twoPassModeEnabled,
                password = details.password
            )
        )

    override suspend fun clearSessionDetails(sessionId: SessionId) =
        sessionDetailsDao.clearPassword(sessionId = sessionId.id)
}
