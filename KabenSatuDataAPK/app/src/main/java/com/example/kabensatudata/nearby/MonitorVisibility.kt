package com.example.kabensatudata.nearby

import androidx.compose.runtime.mutableStateOf

/**
 * MonitorVisibility — In-memory singleton that controls whether the Monitor tab is visible.
 *
 * Default: hidden. Toggled via secret triple-tap on the Info icon in "Tentang Aplikasi" dialog.
 * Resets to hidden when the app process is killed/restarted (no persistence).
 */
object MonitorVisibility {
    private val _isVisible = mutableStateOf(false)
    val isVisible: Boolean get() = _isVisible.value

    fun toggle(): Boolean {
        _isVisible.value = !_isVisible.value
        return _isVisible.value
    }
}
