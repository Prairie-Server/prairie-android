package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.onboarding.OnboardingFlow
import org.prairieserver.prairie.model.onboarding.OnboardingProgressRequest
import org.prairieserver.prairie.model.onboarding.OnboardingState
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.OnboardingApi

/**
 * First-run tour state and manifest. Completion is per profile and lives on
 * the server, so finishing on any device silences every other one.
 */
class OnboardingRepository(private val api: OnboardingApi) {

    suspend fun getFlow(surface: String): ApiResult<OnboardingFlow> = api.getFlow(surface)

    suspend fun getState(): ApiResult<OnboardingState> = api.getState()

    suspend fun recordStep(tourId: String, stepId: String): ApiResult<Unit> =
        api.postProgress(OnboardingProgressRequest(tourId = tourId, lastStep = stepId))

    suspend fun complete(tourId: String, lastStep: String?): ApiResult<Unit> =
        api.postProgress(
            OnboardingProgressRequest(tourId = tourId, lastStep = lastStep, completed = true),
        )

    suspend fun skip(tourId: String, lastStep: String?): ApiResult<Unit> =
        api.postProgress(
            OnboardingProgressRequest(tourId = tourId, lastStep = lastStep, skipped = true),
        )
}
