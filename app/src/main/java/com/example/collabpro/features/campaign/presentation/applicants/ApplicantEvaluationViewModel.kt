package com.example.collabpro.features.campaign.presentation.applicants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.collabpro.core.application.security.ExpectedAccount
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.usecases.EvaluateApplication
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.domain.repositories.ApplicantEvaluationRepository
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.application.auth.SessionState
import com.example.collabpro.features.identity.domain.model.AccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ApplicantEvaluationState(
    val ownerId: UUID? = null, val expiry: Instant? = null, val campaign: CampaignDetails? = null,
    val filter: ApplicationStatus? = null, val page: Page<Applicant>? = null,
    val applicationId: UUID? = null, val candidate: Applicant? = null,
    val busy: Boolean = false, val failure: ApiFailure? = null, val mustRefresh: Boolean = false,
    val decisionDraft: ApplicantDecision? = null, val reasonDraft: String = ""
)

@HiltViewModel
class ApplicantEvaluationViewModel @Inject constructor(
    private val repository: ApplicantEvaluationRepository,
    private val evaluate: EvaluateApplication,
    private val authentication: AuthenticationSession
) : ViewModel() {
    private val mutableUi = MutableStateFlow(ApplicantEvaluationState())
    val ui = mutableUi.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            authentication.state.collect {
                if (!owns(ui.value)) {
                    job?.cancel()
                    mutableUi.value = ApplicantEvaluationState()
                }
            }
        }
    }

    private fun brandSession() = (authentication.state.value as? SessionState.Authenticated)
        ?.takeIf { it.account.accountType == AccountType.BRAND }

    private fun owns(state: ApplicantEvaluationState): Boolean {
        val session = brandSession() ?: return false
        return session.account.accountId == state.ownerId && session.expiresAt == state.expiry
    }

    fun openCampaign(campaign: CampaignDetails): Boolean {
        val session = brandSession() ?: return false
        if (session.account.profileId != campaign.summary.brandId || campaign.summary.status == CampaignStatus.DRAFT) return false
        if (ui.value.busy && owns(ui.value)) return false
        mutableUi.value = ApplicantEvaluationState(session.account.accountId, session.expiresAt, campaign)
        return true
    }

    fun refreshList() = loadPage(ui.value.page?.page ?: 0)
    fun loadPage(number: Int) = fetchPage(number, ui.value.filter)
    fun filter(status: ApplicationStatus?) = fetchPage(0, status)

    private fun fetchPage(number: Int, filter: ApplicationStatus?) {
        val state = ui.value
        val campaign = state.campaign ?: return
        perform(state) {
            val result = repository.list(campaign.summary.id, PageRequest(number), filter)
            val shown = state.copy(filter = filter, page = state.page.takeIf { state.filter == filter })
            when (result) {
                is ApiResult.Success -> shown.copy(page = result.value, failure = null, busy = false)
                is ApiResult.Failure -> shown.copy(failure = result.error, busy = false)
            }
        }
    }

    fun openApplicant(id: UUID): Boolean {
        val state = ui.value
        if (state.busy || !owns(state) || state.page?.items?.none { it.application.id == id } != false) return false
        mutableUi.value = state.copy(applicationId = id, candidate = null, decisionDraft = null, reasonDraft = "", mustRefresh = false)
        refreshDetail()
        return true
    }

    fun refreshDetail() {
        val state = ui.value
        val campaign = state.campaign ?: return
        val id = state.applicationId ?: return
        perform(state) {
            when (val result = repository.detail(campaign.summary.id, id)) {
                is ApiResult.Success -> state.copy(candidate = result.value, mustRefresh = false, failure = null, busy = false)
                is ApiResult.Failure -> state.copy(failure = result.error, mustRefresh = true, busy = false)
            }
        }
    }

    fun decide(decision: ApplicantDecision, reason: String) {
        val state = ui.value
        val candidate = state.candidate ?: return
        if (state.mustRefresh) return
        perform(state.copy(decisionDraft = decision, reasonDraft = reason)) {
            when (val result = evaluate(candidate, decision, reason)) {
                is ApiResult.Success -> state.copy(candidate = result.value, failure = null, busy = false,
                    decisionDraft = null, reasonDraft = "")
                is ApiResult.Failure -> {
                    if (result.error.kind == FailureKind.VALIDATION) return@perform state.copy(
                        failure = result.error, busy = false, decisionDraft = decision, reasonDraft = reason
                    )
                    val refreshed = repository.detail(candidate.application.campaignId, candidate.application.id)
                    state.copy(candidate = (refreshed as? ApiResult.Success)?.value ?: candidate,
                        failure = result.error, mustRefresh = refreshed !is ApiResult.Success, busy = false,
                        decisionDraft = decision, reasonDraft = reason)
                }
            }
        }
    }

    private fun perform(state: ApplicantEvaluationState, action: suspend () -> ApplicantEvaluationState) {
        if (state.busy || !owns(state)) return
        mutableUi.value = state.copy(busy = true, failure = null)
        job = viewModelScope.launch(ExpectedAccount(state.ownerId!!, state.expiry!!)) {
            val result = action()
            if (owns(state)) mutableUi.value = result
        }
    }
}
