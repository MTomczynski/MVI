package com.tomcz.mvi.internal

import com.tomcz.mvi.EffectsCollector
import com.tomcz.mvi.PartialState
import com.tomcz.mvi.StateEffectProcessor
import com.tomcz.mvi.internal.util.reduceAndSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

internal class FlowStateEffectProcessor<in EV : Any, ST : Any, out PA : PartialState<ST>, EF : Any> constructor(
    private val scope: CoroutineScope,
    initialState: ST,
    prepare: (suspend (EffectsCollector<EF, ST>) -> Flow<PA>)? = null,
    private val mapper: (suspend (EffectsCollector<EF, ST>, EV) -> Flow<PA>)? = null,
) : StateEffectProcessor<EV, ST, EF> {

    override val effect: Flow<EF>
        get() = effectSharedFlow
    private val effectSharedFlow: MutableSharedFlow<EF> = MutableSharedFlow(replay = 0)

    override val state: StateFlow<ST>
        get() = stateFlow
    private val stateFlow: MutableStateFlow<ST> = MutableStateFlow(initialState)

    private val effectsCollector: EffectsCollector<EF, ST> = object : EffectsCollector<EF, ST> {
        override fun send(effect: EF): Flow<PartialState<ST>> {
            scope.launch { effectSharedFlow.emit(effect) }
            return flowOf(PartialState.NoAction())
        }
    }

    init {
        prepare?.let {
            scope.launch {
                it(effectsCollector).collect { stateFlow.reduceAndSet(it) }
            }
        }
    }

    override fun sendEvent(event: EV) {
        mapper?.let {
            scope.launch { it(effectsCollector, event).collect { stateFlow.reduceAndSet(it) } }
        }
    }
}
