package dev.jvoyatz.newarch.mvipoc.presentation.mvi

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

const val SAVED_UI_STATE_KEY = "SAVED_STATE_KEY"

abstract class BaseViewModelV3<State: Parcelable, PartialState, Event, Effect>(
    private val savedStateHandle: SavedStateHandle,
    initialState: State
): ViewModel(){
    val state = savedStateHandle.getStateFlow(SAVED_UI_STATE_KEY, initialState)

    //using sharedflow, because event is dropped in case there is not any subscriber
    private val _event : MutableSharedFlow<Event> = MutableSharedFlow()

    private val effect: Channel<Effect> = Channel<Effect>()

    init {
        //"1".scan(2) {  myint: Int, myChar: Char ->   myChar.digitToInt() + myint}

        viewModelScope.launch {
            _event.flatMapMerge {//takes a new event
                //after checking what exactly is this event returns a partial state -- transformation
                handleEvent(it)
            }.scan(state.value) { state, newPartialState ->
                //scan is an alias of runningFold
                /* .runningFold(state.value) { state, newPartialState ->
                    reduceUiState(state, newPartialState)
                }*/
                reduceUiState(state, newPartialState).also {
                    println("Current State [$state] --> changed to [$it] because of this partial state [$newPartialState]")
                }
            }
           .catch {
                Timber.d("exception $it")
            }.collect {
                savedStateHandle[SAVED_UI_STATE_KEY] = it //surviving process death
            }
        }
    }

    fun effect(): Flow<Effect> = effect.receiveAsFlow()

    protected fun setEffect(builder: () -> Effect) {
        val effectValue = builder()
        viewModelScope.launch { effect.send(effectValue) }
    }

    fun postEvent(event : Event) {
        val newEvent = event
        viewModelScope.launch {
            _event.emit(newEvent)
        }
    }

    /**
     * Processing events sequentially
     */
    abstract fun handleEvent(event: Event): Flow<PartialState>

    protected abstract fun reduceUiState(
        prevState: State,
        partialState: PartialState
    ): State

    private fun log(state: State) = Timber.tag(BaseViewModel.LOG_TAG).d(BaseViewModel.LOG_MSG_STATE, state)
}