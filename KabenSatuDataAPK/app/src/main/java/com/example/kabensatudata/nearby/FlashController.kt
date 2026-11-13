package com.example.kabensatudata.nearby

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * FlashController — Remote flashlight/torch control singleton.
 *
 * Supports:
 *  - ON       : steady torch on
 *  - OFF      : torch off (cancels any blink pattern)
 *  - BLINK    : fast blink (3x per second)
 *  - SOS      : Morse code SOS pattern (···−−−···)
 *  - PULSE    : slow pulse (1x per second)
 */
object FlashController {

    private const val TAG = "FlashController"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeJob: Job? = null

    // ─── Get camera ID for torch ────────────────────────────────────────────
    private fun getCameraManager(context: Context): Pair<CameraManager, String>? {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            // Find rear camera (usually first one with flash)
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return null
            Pair(cm, cameraId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get camera manager: ${e.message}")
            null
        }
    }

    private fun setTorch(context: Context, on: Boolean) {
        try {
            val (cm, id) = getCameraManager(context) ?: return
            cm.setTorchMode(id, on)
        } catch (e: Exception) {
            Log.e(TAG, "setTorchMode error: ${e.message}")
        }
    }

    // ─── Cancel running pattern ──────────────────────────────────────────────
    private fun cancelActive(context: Context) {
        activeJob?.cancel()
        activeJob = null
        // Ensure torch is off when cancelling
        try { setTorch(context, false) } catch (_: Exception) {}
    }

    // ─── ON — steady torch ───────────────────────────────────────────────────
    fun turnOn(context: Context) {
        cancelActive(context)
        setTorch(context, true)
        Log.i(TAG, "Flash ON")
    }

    // ─── OFF — torch off ─────────────────────────────────────────────────────
    fun turnOff(context: Context) {
        cancelActive(context)
        Log.i(TAG, "Flash OFF")
    }

    // ─── BLINK — fast blink 3x/second ────────────────────────────────────────
    fun startBlink(context: Context) {
        cancelActive(context)
        Log.i(TAG, "Flash BLINK started")
        activeJob = scope.launch {
            while (isActive) {
                setTorch(context, true)
                delay(167)   // ON for ~1/6 second
                setTorch(context, false)
                delay(167)   // OFF for ~1/6 second
            }
        }
    }

    // ─── PULSE — slow pulse 1x/second ────────────────────────────────────────
    fun startPulse(context: Context) {
        cancelActive(context)
        Log.i(TAG, "Flash PULSE started")
        activeJob = scope.launch {
            while (isActive) {
                setTorch(context, true)
                delay(300)
                setTorch(context, false)
                delay(700)
            }
        }
    }

    // ─── SOS — Morse code ···−−−··· ──────────────────────────────────────────
    // Dot = 200ms, Dash = 600ms, gap between = 200ms, gap between letters = 600ms
    fun startSOS(context: Context) {
        cancelActive(context)
        Log.i(TAG, "Flash SOS started")
        val dot = 200L
        val dash = 600L
        val gap = 200L
        val letterGap = 600L
        val repeatGap = 2000L

        // SOS = ··· --- ···
        val pattern = listOf(
            // S = ···
            Pair(dot, gap), Pair(dot, gap), Pair(dot, letterGap),
            // O = ---
            Pair(dash, gap), Pair(dash, gap), Pair(dash, letterGap),
            // S = ···
            Pair(dot, gap), Pair(dot, gap), Pair(dot, repeatGap)
        )

        activeJob = scope.launch {
            while (isActive) {
                for ((onDuration, offDuration) in pattern) {
                    if (!isActive) break
                    setTorch(context, true)
                    delay(onDuration)
                    setTorch(context, false)
                    delay(offDuration)
                }
            }
        }
    }
}
