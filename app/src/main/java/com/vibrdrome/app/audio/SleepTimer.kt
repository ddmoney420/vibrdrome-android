package com.vibrdrome.app.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SleepTimer {

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _fadeFactor = MutableStateFlow(1.0f)
    val fadeFactor: StateFlow<Float> = _fadeFactor.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val options = listOf(15, 30, 45, 60, 120)

    fun start(minutes: Int) {
        cancel()
        _remainingSeconds.value = minutes * 60
        _isActive.value = true
        _fadeFactor.value = 1.0f

        timerJob = scope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1000)
                _remainingSeconds.value -= 1
                val remaining = _remainingSeconds.value
                if (remaining <= 10) {
                    _fadeFactor.value = remaining / 10f
                }
            }
            _fadeFactor.value = 0f
            _isActive.value = false
        }
    }

    fun cancel() {
        timerJob?.cancel()
        _isActive.value = false
        _remainingSeconds.value = 0
        _fadeFactor.value = 1.0f
    }

    fun release() {
        timerJob?.cancel()
    }

    fun formattedTime(): String {
        val total = _remainingSeconds.value
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
