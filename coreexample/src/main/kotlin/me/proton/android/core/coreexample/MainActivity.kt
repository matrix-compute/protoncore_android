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

package me.proton.android.core.coreexample

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.android.core.coreexample.api.CoreExampleRepository
import me.proton.android.core.coreexample.databinding.ActivityMainBinding
import me.proton.android.core.coreexample.ui.CustomViewsActivity
import me.proton.android.core.coreexample.viewmodel.PublicAddressViewModel
import me.proton.android.core.coreexample.viewmodel.UserAddressKeyViewModel
import me.proton.android.core.coreexample.viewmodel.UserKeyViewModel
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.account.domain.entity.SessionState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getPrimaryAccount
import me.proton.core.accountmanager.presentation.observe
import me.proton.core.accountmanager.presentation.onAccountDisabled
import me.proton.core.accountmanager.presentation.onAccountChangePasswordNeeded
import me.proton.core.accountmanager.presentation.onAccountReady
import me.proton.core.accountmanager.presentation.onAccountRemoved
import me.proton.core.accountmanager.presentation.onAccountTwoPassModeFailed
import me.proton.core.accountmanager.presentation.onAccountTwoPassModeNeeded
import me.proton.core.accountmanager.presentation.onAccountCreateAddressNeeded
import me.proton.core.accountmanager.presentation.onSessionHumanVerificationFailed
import me.proton.core.accountmanager.presentation.onSessionHumanVerificationNeeded
import me.proton.core.accountmanager.presentation.onSessionSecondFactorFailed
import me.proton.core.accountmanager.presentation.onSessionSecondFactorNeeded
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.auth.presentation.onHumanVerificationResult
import me.proton.core.auth.presentation.onLoginResult
import me.proton.core.auth.presentation.onScopeResult
import me.proton.core.auth.presentation.ui.showPasswordChangeDialog
import me.proton.core.network.domain.humanverification.HumanVerificationDetails
import me.proton.core.network.domain.humanverification.VerificationMethod
import me.proton.core.presentation.ui.ProtonActivity
import me.proton.core.presentation.utils.onClick
import me.proton.core.presentation.utils.showForceUpdate
import me.proton.core.user.domain.entity.UserType
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ProtonActivity<ActivityMainBinding>() {

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var authOrchestrator: AuthOrchestrator

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var coreExampleRepository: CoreExampleRepository

    private val userKeyViewModel: UserKeyViewModel by viewModels()
    private val userAddressKeyViewModel: UserAddressKeyViewModel by viewModels()
    private val publicAddressViewModel: PublicAddressViewModel by viewModels()

    override fun layoutId(): Int = R.layout.activity_main

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authOrchestrator.register(this)

        authOrchestrator
            .onLoginResult { result ->
                result
            }
            .onScopeResult { result ->
                result
            }
            .onHumanVerificationResult { result ->
                result
            }

        with(binding) {
            humanVerification.onClick {
                authOrchestrator.startHumanVerificationWorkflow(
                    "sessionId",
                    HumanVerificationDetails(
                        listOf(
                            VerificationMethod.CAPTCHA,
                            VerificationMethod.EMAIL,
                            VerificationMethod.PHONE
                        )
                    )
                )
            }
            customViews.onClick { startActivity(Intent(this@MainActivity, CustomViewsActivity::class.java)) }
            login.onClick { authOrchestrator.startLoginWorkflow(UserType.Internal) }
            forceUpdate.onClick {
                supportFragmentManager.showForceUpdate(
                    apiErrorMessage = "Error Message coming from the API."
                )
            }

            triggerHumanVer.onClick {
                lifecycleScope.launch(Dispatchers.IO) {
                    accountManager.getPrimaryUserId().first()?.let {
                        coreExampleRepository.triggerHumanVerification(it)
                    }
                }
            }
        }

        accountManager.getPrimaryAccount().onEach { primary ->
            binding.primaryAccountText.text = "Primary: ${primary?.username}"
        }.launchIn(lifecycleScope)

        accountManager.getAccounts().onEach { accounts ->
            if (accounts.isEmpty()) authOrchestrator.startLoginWorkflow(UserType.Internal)

            binding.accountsLayout.removeAllViews()
            accounts.forEach { account ->
                binding.accountsLayout.addView(
                    Button(this@MainActivity).apply {
                        text = "${account.username} -> ${account.state}/${account.sessionState}"
                        onClick {
                            lifecycleScope.launch {
                                when (account.state) {
                                    AccountState.Ready ->
                                        accountManager.disableAccount(account.userId)
                                    AccountState.NotReady,
                                    AccountState.ChangePasswordNeeded,
                                    AccountState.Disabled ->
                                        accountManager.removeAccount(account.userId)
                                    AccountState.TwoPassModeNeeded,
                                    AccountState.TwoPassModeFailed ->
                                        when (account.sessionState) {
                                            SessionState.SecondFactorNeeded,
                                            SessionState.SecondFactorFailed ->
                                                accountManager.disableAccount(account.userId)
                                            SessionState.Authenticated ->
                                                authOrchestrator.startTwoPassModeWorkflow(
                                                    account.sessionId?.id!!,
                                                    UserType.Username
                                                )
                                            else -> Unit
                                        }
                                    AccountState.CreateAddressNeeded ->
                                        authOrchestrator.startChooseAddressWorkflow(
                                            account.sessionId?.id!!,
                                            account.username
                                        )
                                    else -> Unit
                                }
                            }
                        }
                    }
                )
            }
        }.launchIn(lifecycleScope)

        accountManager.onHumanVerificationNeeded(initialState = true).onEach { (account, details) ->
            authOrchestrator.startHumanVerificationWorkflow(account.sessionId?.id!!, details)
        }.launchIn(lifecycleScope)

        // Used to test session ForceLogout.
        accountManager.observe(lifecycleScope)
            .onAccountDisabled {
                Timber.d("onAccountDisabled -> remove $it")
                accountManager.removeAccount(it.userId)
            }
            .onAccountRemoved {
                Timber.d("onAccountRemoved -> $it")
            }
            .onAccountReady {
                Timber.d("onAccountReady -> $it")
            }
            .onSessionSecondFactorNeeded {
                Timber.d("onSessionSecondFactorNeeded -> $it")
            }
            .onSessionSecondFactorFailed {
                Timber.d("onSessionSecondFactorFailed -> $it")
            }
            .onAccountTwoPassModeNeeded {
                Timber.d("onAccountTwoPassModeNeeded -> $it")
            }
            .onAccountTwoPassModeFailed {
                Timber.d("onAccountTwoPassModeFailed -> $it")
            }
            .onAccountCreateAddressNeeded {
                Timber.d("onAccountCreateAddressNeeded -> $it")
            }
            .onAccountChangePasswordNeeded {
                Timber.d("onAccountChangePasswordNeeded -> $it")
            }
            .onSessionHumanVerificationNeeded {
                Timber.d("onSessionHumanVerificationNeeded -> $it")
            }
            .onSessionHumanVerificationFailed {
                Timber.d("onSessionHumanVerificationFailed -> $it")
            }

        userKeyViewModel.getUserKeyState().onEach { state ->
            binding.primaryAccountKeyState.text = "User Key State: ${state::class.java.simpleName}"
        }.launchIn(lifecycleScope)

        userAddressKeyViewModel.getUserAddressKeyState().onEach { state ->
            binding.primaryAccountAddressKeyState.text = "Address Key State: ${state::class.java.simpleName}"
        }.launchIn(lifecycleScope)

        publicAddressViewModel.getPublicAddressState().onEach { state ->
            binding.primaryAccountPublicAddressState.text = "Public Address State: ${state::class.java.simpleName}"
        }.launchIn(lifecycleScope)
    }
}
