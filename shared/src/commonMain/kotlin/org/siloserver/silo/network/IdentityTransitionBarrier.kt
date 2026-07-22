package org.siloserver.silo.network

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class IdentityTransitionPhase { WILL_CHANGE, DID_CHANGE }

enum class IdentityTransitionKind {
    SIGN_IN,
    SIGN_OUT,
    SERVER_SWITCH,
    SERVER_REMOVE,
    PROFILE_SWITCH,
    TEMPORARY_SCOPE_BEGIN,
    TEMPORARY_SCOPE_END,
}

data class IdentityTransition(
    val phase: IdentityTransitionPhase,
    val kind: IdentityTransitionKind,
    val generation: Long,
)

interface IdentityTransitionBarrier {
    val transitions: SharedFlow<IdentityTransition>
    val generation: StateFlow<Long>

    /** Installs the synchronous privacy gate invoked before any identity mutation. */
    fun installGate(listener: (IdentityTransition) -> Unit)

    suspend fun <T> changing(kind: IdentityTransitionKind, block: suspend () -> T): T
}

class DefaultIdentityTransitionBarrier : IdentityTransitionBarrier {
    private val mutationMutex = Mutex()
    private val _transitions = MutableSharedFlow<IdentityTransition>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _generation = MutableStateFlow(0L)

    @Volatile
    private var gate: ((IdentityTransition) -> Unit)? = null

    @Volatile
    private var testObserver: ((IdentityTransition) -> Unit)? = null

    override val transitions: SharedFlow<IdentityTransition> = _transitions.asSharedFlow()
    override val generation: StateFlow<Long> = _generation.asStateFlow()

    override fun installGate(listener: (IdentityTransition) -> Unit) {
        gate = listener
    }

    override suspend fun <T> changing(kind: IdentityTransitionKind, block: suspend () -> T): T =
        mutationMutex.withLock {
            val nextGeneration = _generation.value + 1
            val willChange = IdentityTransition(
                phase = IdentityTransitionPhase.WILL_CHANGE,
                kind = kind,
                generation = nextGeneration,
            )
            // This callback is the privacy boundary. It runs inline before new identity is visible.
            gate?.invoke(willChange)
            _generation.value = nextGeneration
            publish(willChange)
            try {
                block()
            } finally {
                publish(
                    IdentityTransition(
                        phase = IdentityTransitionPhase.DID_CHANGE,
                        kind = kind,
                        generation = nextGeneration,
                    ),
                )
            }
        }

    internal fun installObserverForTests(observer: (IdentityTransition) -> Unit) {
        testObserver = observer
    }

    private fun publish(transition: IdentityTransition) {
        _transitions.tryEmit(transition)
        testObserver?.invoke(transition)
    }
}
