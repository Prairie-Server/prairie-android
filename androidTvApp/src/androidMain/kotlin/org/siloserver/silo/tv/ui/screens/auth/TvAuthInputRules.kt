package org.siloserver.silo.tv.ui.screens.auth

internal fun canSubmitTvCredentialLogin(
    username: String,
    password: String,
    isLoading: Boolean,
): Boolean =
    !isLoading && username.isNotBlank() && password.isNotBlank()

internal fun canSubmitTvServerUrl(value: String, isLoading: Boolean): Boolean =
    !isLoading && value.isNotBlank()
