package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.onboarding.OnboardingFlow
import org.prairieserver.prairie.model.onboarding.OnboardingProgressRequest
import org.prairieserver.prairie.model.onboarding.OnboardingState
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.OnboardingApi

/**
 * First-run tour state and manifest. Completion is per profile and lives on
 * the server, so finishing on any device silences every other one.
 */
class OnboardingRepository(private val api: OnboardingApi) {

    suspend fun getFlow(surface: String, scope: AuthScopeSnapshot): ApiResult<OnboardingFlow> = api.getFlow(surface, scope)

    suspend fun getState(scope: AuthScopeSnapshot): ApiResult<OnboardingState> = api.getState(scope)

    suspend fun recordStep(tourId: String, stepId: String, scope: AuthScopeSnapshot): ApiResult<Unit> =
        api.putProgress(OnboardingProgressRequest(tourId = tourId, lastStep = stepId), scope)

    suspend fun complete(tourId: String, lastStep: String?, scope: AuthScopeSnapshot): ApiResult<Unit> =
        api.putProgress(
            OnboardingProgressRequest(tourId = tourId, lastStep = lastStep, completed = true), scope,
        )

    suspend fun skip(tourId: String, lastStep: String?, scope: AuthScopeSnapshot): ApiResult<Unit> =
        api.putProgress(
            OnboardingProgressRequest(tourId = tourId, lastStep = lastStep, skipped = true), scope,
        )
}
