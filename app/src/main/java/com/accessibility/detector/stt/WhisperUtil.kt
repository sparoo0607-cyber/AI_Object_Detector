package com.accessibility.detector.stt

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * LISTEN / Live captions — Whisper support code (mel-spectrogram
 * feature extraction + vocabulary/token decoding), ported to Kotlin
 * from the community whisper_android (whisper_java) reference
 * implementation. Pure JVM math — no native/NDK build, so it carries
 * none of the native-library risk a whisper.cpp JNI port would.
 *
 * This is what makes real, on-device, offline speech-to-text possible
 * for SAHAY: the earlier SttHelper (Android SpeechRecognizer) needs
 * the cloud on most devices; Whisper here runs the whole pipeline —
 * audio -> mel spectrogram -> transcript tokens -> text — locally.
 */
class WhisperUtil {

    companion object {
        const val SAMPLE_RATE = 16000
        const val N_FFT = 400
        const val N_MEL = 80
        const val HOP_LENGTH = 160
        const val CHUNK_SIZE = 30
        const val MEL_LEN = 3000
    }

    private class Vocab {
        var tokenEOT = 50256
        var tokenSOT = 50257
        var tokenPREV = 50360
        var tokenSOLM = 50361
        var tokenNOT = 50362
        var tokenBEG = 50363
        val tokenTRANSLATE = 50358
        val tokenTRANSCRIBE = 50359
        val nVocabEnglish = 51864
        val nVocabMultilingual = 51865
        val tokenToWord = HashMap<Int, String>()
    }

    private class Filter { var nMel = 0; var nFft = 0; var data: FloatArray = FloatArray(0) }
    private class Mel { var nLen = 0; var nMel = 0; var data: FloatArray = FloatArray(0) }

    private val vocab = Vocab()
    private val filters = Filter()
    private val mel = Mel()

    val tokenEOT get() = vocab.tokenEOT
    val tokenTranscribe get() = vocab.tokenTRANSCRIBE
    val tokenTranslate get() = vocab.tokenTRANSLATE

    fun wordForToken(token: Int): String? = vocab.tokenToWord[token]

    /** Reads the community-generated `filters_vocab_multilingual.bin`
     * (mel filterbank + tokenizer vocabulary in one binary blob). */
    fun loadFiltersAndVocab(multilingual: Boolean, vocabPath: String): Boolean {
        val bytes = Files.readAllBytes(Paths.get(vocabPath))
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())

        val magic = buf.int
        if (magic != 0x5553454e) return false

        filters.nMel = buf.int
        filters.nFft = buf.int
        val filterData = ByteArray(filters.nMel * filters.nFft * 4)
        buf.get(filterData)
        val filterBuf = ByteBuffer.wrap(filterData).order(ByteOrder.nativeOrder())
        filters.data = FloatArray(filters.nMel * filters.nFft)
        for (i in filters.data.indices) filters.data[i] = filterBuf.float

        val nVocab = buf.int
        for (i in 0 until nVocab) {
            val len = buf.int
            val wordBytes = ByteArray(len)
            buf.get(wordBytes)
            vocab.tokenToWord[i] = String(wordBytes)
        }

        val nVocabAdditional = if (!multilingual) {
            vocab.nVocabEnglish
        } else {
            vocab.nVocabMultilingual.also {
                vocab.tokenEOT++; vocab.tokenSOT++; vocab.tokenPREV++
                vocab.tokenSOLM++; vocab.tokenNOT++; vocab.tokenBEG++
            }
        }
        for (i in nVocab until nVocabAdditional) {
            vocab.tokenToWord[i] = when {
                i > vocab.tokenBEG -> "[_TT_${i - vocab.tokenBEG}]"
                i == vocab.tokenEOT -> "[_EOT_]"
                i == vocab.tokenSOT -> "[_SOT_]"
                i == vocab.tokenPREV -> "[_PREV_]"
                i == vocab.tokenNOT -> "[_NOT_]"
                i == vocab.tokenBEG -> "[_BEG_]"
                else -> "[_extra_token_$i]"
            }
        }
        return true
    }

    /** samples.size must equal SAMPLE_RATE * CHUNK_SIZE (30s window,
     * zero-padded by the caller if the real recording is shorter). */
    fun melSpectrogram(samples: FloatArray, nSamples: Int): FloatArray {
        val fftSize = N_FFT
        val fftStep = HOP_LENGTH
        mel.nMel = N_MEL
        mel.nLen = nSamples / fftStep
        mel.data = FloatArray(mel.nMel * mel.nLen)

        val hann = FloatArray(fftSize) { (0.5 * (1.0 - cos(2.0 * Math.PI * it / fftSize))).toFloat() }
        val nFft = 1 + fftSize / 2

        val fftIn = FloatArray(fftSize)
        val fftOut = FloatArray(fftSize * 2)

        for (i in 0 until mel.nLen) {
            val offset = i * fftStep
            for (j in 0 until fftSize) {
                fftIn[j] = if (offset + j < nSamples) hann[j] * samples[offset + j] else 0f
            }
            fft(fftIn, fftOut)
            for (j in 0 until fftSize) {
                fftOut[j] = fftOut[2 * j] * fftOut[2 * j] + fftOut[2 * j + 1] * fftOut[2 * j + 1]
            }
            for (j in 1 until fftSize / 2) fftOut[j] += fftOut[fftSize - j]

            for (j in 0 until mel.nMel) {
                var sum = 0.0
                for (k in 0 until nFft) sum += (fftOut[k] * filters.data[j * nFft + k])
                if (sum < 1e-10) sum = 1e-10
                sum = log10(sum)
                mel.data[j * mel.nLen + i] = sum.toFloat()
            }
        }

        var mmax = -1e20
        for (v in mel.data) if (v > mmax) mmax = v.toDouble()
        mmax -= 8.0
        for (i in mel.data.indices) {
            if (mel.data[i] < mmax) mel.data[i] = mmax.toFloat()
            mel.data[i] = ((mel.data[i] + 4.0) / 4.0).toFloat()
        }
        return mel.data
    }

    private fun dft(input: FloatArray, output: FloatArray) {
        val n = input.size
        for (k in 0 until n) {
            var re = 0f; var im = 0f
            for (t in 0 until n) {
                val angle = (2 * Math.PI * k * t / n).toFloat()
                re += input[t] * cos(angle.toDouble()).toFloat()
                im -= input[t] * sin(angle.toDouble()).toFloat()
            }
            output[k * 2] = re
            output[k * 2 + 1] = im
        }
    }

    private fun fft(input: FloatArray, output: FloatArray) {
        val n = input.size
        if (n == 1) { output[0] = input[0]; output[1] = 0f; return }
        if (n % 2 == 1) { dft(input, output); return }

        val even = FloatArray(n / 2)
        val odd = FloatArray(n / 2)
        var ei = 0; var oi = 0
        for (i in 0 until n) { if (i % 2 == 0) even[ei++] = input[i] else odd[oi++] = input[i] }

        val evenFft = FloatArray(n)
        val oddFft = FloatArray(n)
        fft(even, evenFft)
        fft(odd, oddFft)

        for (k in 0 until n / 2) {
            val theta = (2 * Math.PI * k / n).toFloat()
            val re = cos(theta.toDouble()).toFloat()
            val im = -sin(theta.toDouble()).toFloat()
            val reOdd = oddFft[2 * k]
            val imOdd = oddFft[2 * k + 1]
            output[2 * k] = evenFft[2 * k] + re * reOdd - im * imOdd
            output[2 * k + 1] = evenFft[2 * k + 1] + re * imOdd + im * reOdd
            output[2 * (k + n / 2)] = evenFft[2 * k] - re * reOdd + im * imOdd
            output[2 * (k + n / 2) + 1] = evenFft[2 * k + 1] - re * imOdd - im * reOdd
        }
    }
}
