package com.example.videohapticapp

import android.net.Uri
import android.os.*
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.LinkedHashSet


data class HapticEvent(
    val timestamp: Long,
    val duration: Long,
    val pattern: LongArray,
    val amplitudes: IntArray,
    val repeat: Int
)

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var vibrator: Vibrator
    private val handler = Handler(Looper.getMainLooper())
    private val hapticEvents = mutableListOf<HapticEvent>()
    private val triggeredTimestamps = LinkedHashSet<Long>()

    private var lastCheckedPosition: Long = 0L

    private val updateRunnable = object : Runnable {
        override fun run() {
            val currentPosition = player.currentPosition

            for (event in hapticEvents) {
                if (!triggeredTimestamps.contains(event.timestamp) &&
                    event.timestamp in lastCheckedPosition..currentPosition) {
                    triggerVibration(event)
                    triggeredTimestamps.add(event.timestamp)
                }
            }

            lastCheckedPosition = currentPosition
            handler.postDelayed(this, 20)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        enableImmersiveMode()

        val playerView = findViewById<PlayerView>(R.id.player_view)
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        playerView.keepScreenOn = true

        val uri = Uri.parse("android.resource://$packageName/raw/trailer0")
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        loadHapticsFromJson()
        handler.post(updateRunnable)
    }

    private fun enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun loadHapticsFromJson() {
        val inputStream = assets.open("haptics.json")
        val reader = BufferedReader(InputStreamReader(inputStream))
        val jsonString = reader.readText()
        reader.close()

        val jsonObject = JSONObject(jsonString)
        val hapticArray = jsonObject.getJSONArray("haptics")

        for (i in 0 until hapticArray.length()) {
            val obj = hapticArray.getJSONObject(i)
            val timestamp = obj.getLong("timestamp")
            val duration = obj.getLong("duration")
            val repeat = obj.optInt("repeat", -1)
            val patternArray = obj.getJSONArray("pattern")
            val amplitudeArray = obj.getJSONArray("amplitudes")

            val pattern = LongArray(patternArray.length()) { index -> patternArray.getLong(index) }
            val amplitudes = IntArray(amplitudeArray.length()) { index -> amplitudeArray.getInt(index) }

            hapticEvents.add(HapticEvent(timestamp, duration, pattern, amplitudes, repeat))
        }
    }

    private fun triggerVibration(event: HapticEvent) {
        vibrator.cancel()

        if (Build.VERSION.SDK_INT >= 26) {
            val effect = VibrationEffect.createWaveform(event.pattern, event.amplitudes, event.repeat)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(event.pattern, event.repeat)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        vibrator.cancel()
        player.release()
    }
}
