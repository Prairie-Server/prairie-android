package org.siloserver.silo.android.ui.screens.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import org.siloserver.silo.common.pairing.CompanionPairingApproval
import org.siloserver.silo.common.pairing.CompanionPairingCoordinator
import org.siloserver.silo.common.pairing.CompanionPairingNsdBrowser
import org.siloserver.silo.common.pairing.CompanionPairingResult
import org.siloserver.silo.common.pairing.CompanionPairingStatus
import org.siloserver.silo.common.pairing.CompanionPairingTarget

class CompanionPairingViewModel(
    private val browser: CompanionPairingNsdBrowser,
    private val coordinator: CompanionPairingCoordinator,
) : ViewModel() {
    val targets: StateFlow<List<CompanionPairingTarget>> = browser.targets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val status: StateFlow<CompanionPairingStatus> = coordinator.status

    private val _pendingApproval = MutableStateFlow<CompanionPairingApproval?>(null)
    val pendingApproval: StateFlow<CompanionPairingApproval?> = _pendingApproval.asStateFlow()

    private var pendingDecision: CompletableDeferred<Boolean>? = null

    init {
        browser.start()
    }

    fun pair(target: CompanionPairingTarget) {
        if (pendingDecision != null) return
        viewModelScope.launch {
            val result = coordinator.pair(target) { approval ->
                val decision = CompletableDeferred<Boolean>()
                pendingDecision = decision
                _pendingApproval.value = approval
                try {
                    decision.await()
                } finally {
                    _pendingApproval.value = null
                    pendingDecision = null
                }
            }
            if (result is CompanionPairingResult.Completed) {
                browser.stop()
            }
        }
    }

    fun approveMatchCode() {
        pendingDecision?.complete(true)
    }

    fun cancelMatchCode() {
        pendingDecision?.complete(false)
    }

    override fun onCleared() {
        pendingDecision?.cancel()
        browser.stop()
        super.onCleared()
    }
}
