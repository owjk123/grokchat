package com.grokchat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private var saveJob: Job? = null
    private var loaded = false

    fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _messages.value = MessageStore.load(getApplication())
        }
    }

    fun addMessage(msg: Message) {
        val list = _messages.value.toMutableList()
        list.add(msg)
        // Cap history to prevent unbounded growth
        while (list.size > MessageStore.MAX_MESSAGES) list.removeAt(0)
        _messages.value = list
        scheduleSave()
    }

    fun clear() {
        _messages.value = emptyList()
        saveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            MessageStore.clearAll(getApplication())
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            MessageStore.save(getApplication(), _messages.value)
        }
    }

    /**
     * Flush pending save synchronously; safe to call from onPause.
     * Blocks briefly on IO but ensures data hits disk before process death.
     */
    fun flushSync() {
        saveJob?.cancel()
        runBlocking(Dispatchers.IO) {
            MessageStore.save(getApplication(), _messages.value)
        }
    }
}
