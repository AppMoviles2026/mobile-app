package com.example.collabpro.features.campaign.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.collabpro.core.application.security.ExpectedAccount
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.dashboard.LoadOwnActivityTotal
import com.example.collabpro.features.identity.application.auth.*
import com.example.collabpro.features.identity.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class ActivityDashboardUiState(val ownerId: UUID? = null, val expiresAt: Instant? = null,
    val type: AccountType? = null, val total: Long? = null, val loading: Boolean = false, val failure: ApiFailure? = null)

@HiltViewModel
class ActivityDashboardViewModel @Inject constructor(private val totals: LoadOwnActivityTotal,
    private val authentication: AuthenticationSession, private val clock: Clock) : ViewModel() {
    private val mutableUi = MutableStateFlow(ActivityDashboardUiState())
    val ui = mutableUi.asStateFlow()
    private var owner: Account? = null
    private var expiry: Instant? = null
    private var revision = 0L
    private var request: Job? = null
    private var visible = false
    init { viewModelScope.launch { authentication.state.collect { state ->
        val session = (state as? SessionState.Authenticated)?.takeIf { it.account.status == AccountStatus.ACTIVE }
        if (owner?.accountId != session?.account?.accountId || expiry != session?.expiresAt) {
            revision++; request?.cancel(); owner = session?.account; expiry = session?.expiresAt; visible = false
            mutableUi.value = ActivityDashboardUiState(owner?.accountId, expiry, owner?.accountType)
        } else owner = session?.account
    } } }
    private fun active(): Boolean {
        val session = authentication.state.value as? SessionState.Authenticated ?: return false
        return owner?.accountId == session.account.accountId && expiry == session.expiresAt && session.account.status == AccountStatus.ACTIVE && session.expiresAt.isAfter(clock.instant())
    }
    fun onShown(shown: Boolean) {
        visible = shown
        if (shown) refresh() else { revision++; request?.cancel(); mutableUi.update { it.copy(loading = false) } }
    }
    fun onResume() { if (visible) refresh() }
    fun refresh() {
        if (!active()) return
        request?.cancel(); val expected = ++revision; val account = owner!!
        mutableUi.update { it.copy(total = null, loading = true, failure = null) }
        request = viewModelScope.launch(ExpectedAccount(account.accountId, expiry!!)) {
            val result = if (account.accountType == AccountType.BRAND) totals.campaigns(account.profileId) else totals.applications(account.profileId)
            if (!active() || expected != revision) return@launch
            mutableUi.update { it.copy(loading = false, total = (result as? ApiResult.Success)?.value,
                failure = (result as? ApiResult.Failure)?.error) }
        }
    }
}
