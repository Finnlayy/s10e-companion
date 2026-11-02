package dev.s10e.companion.speech

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

class PiperSpeaker(private val assetManager: AssetManager?, private val modelPath: String?) : Speaker {
    private var tts: OfflineTts? = null
    private var sampleRate: Int = 22050

    init {
        if (assetManager != null && modelPath != null) {
            val vits = OfflineTtsVitsModelConfig(
                model = "$modelPath/de_DE-thorsten-medium.onnx",
                tokens = "$modelPath/tokens.txt",
                dataDir = "$modelPath/espeak-ng-data"
            )
            val modelConfig = OfflineTtsModelConfig(
                vits = vits,
                numThreads = 1,
                debug = false,
                provider = "cpu"
            )
            val config = OfflineTtsConfig(
                model = modelConfig,
                maxNumSentences = 1
            )
            tts = OfflineTts(assetManager, config)
            sampleRate = tts?.sampleRate() ?: 22050
        }
    }

    override suspend fun speak(text: String) {
        val currentTts = tts ?: return

        val audio = currentTts.generate(text, 0, 1.0f)
        val samples = audio.samples

        val shortSamples = ShortArray(samples.size) { i ->
            (samples[i] * 32767).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val bufferSize = shortSamples.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bufferSize)
            .build()

        track.write(shortSamples, 0, shortSamples.size)
        track.play()

        // Wait for it to finish playing
        val durationMs = (samples.size.toLong() * 1000) / sampleRate
        kotlinx.coroutines.delay(durationMs + 100)

        track.stop()
        track.release()
    }
}
