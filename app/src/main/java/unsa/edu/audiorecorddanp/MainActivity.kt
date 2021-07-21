package unsa.edu.audiorecorddanp

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private val SAMPLING_RATE_IN_HZ = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    /**
     * Factor by that the minimum buffer size is multiplied. The bigger the factor is the less
     * likely it is that samples will be dropped, but more memory will be used. The minimum buffer
     * size is determined by [AudioRecord.getMinBufferSize] and depends on the
     * recording settings.
     */
    private val BUFFER_SIZE_FACTOR = 2

    /**
     * Size of the buffer where the audio data is stored by Android
     */
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
        SAMPLING_RATE_IN_HZ,
        CHANNEL_CONFIG, AUDIO_FORMAT
    ) * BUFFER_SIZE_FACTOR

    /**
     * Signals whether a recording is in progress (true) or not (false).
     */
    private val recordingInProgress = AtomicBoolean(false)

    /**
     * Audio Recording Thread
     */
    private lateinit var recordingThread: Thread

    private lateinit var recorder: AudioRecord

    // Permission Request
    private lateinit var startActivityResultLauncher: ActivityResultLauncher<String>

    // Elements of view main_activity.xml
    private lateinit var btnStartRecord: TextView
    private lateinit var btnStopRecord: TextView
    private lateinit var tvStatus: TextView


    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Initialize graphic components
        this.btnStartRecord = findViewById(R.id.btnStartRecord)
        this.btnStopRecord = findViewById(R.id.btnStopRecord)
        this.tvStatus = findViewById(R.id.tvStatus)
        // Check permission for this app
        this.startActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    //Start with active buttons
                    Toast.makeText(
                        this,
                        "Permiso Otorgado",
                        Toast.LENGTH_SHORT
                    ).show()
                    initComponents()
                } else {
                    // Disable button
                    this.btnStartRecord.isEnabled = false
                    this.btnStopRecord.isEnabled = false
                    this.tvStatus.text = "Estado: No disponible"
                    Toast.makeText(
                        this,
                        "Permiso Denegado",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        this.startActivityResultLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun initComponents() {
        this.btnStartRecord.isEnabled = true
        this.btnStopRecord.isEnabled = false
        this.btnStartRecord.setOnClickListener {
            startRecord()
            this.btnStartRecord.isEnabled = false
            this.btnStopRecord.isEnabled = true
        }
        this.btnStopRecord.setOnClickListener {
            stopRecord()
            this.btnStartRecord.isEnabled = true
            this.btnStopRecord.isEnabled = false
        }
    }

    @SuppressLint("SetTextI18n")
    private fun stopRecord() {
        if (!this::recorder.isInitialized) {
            return
        }
        recordingInProgress.set(false)
        this.tvStatus.text = "Estado: Detenido"
        recorder.stop()
        recorder.release()
    }

    @SuppressLint("SetTextI18n")
    private fun startRecord() {
        recorder = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        )
        recorder.startRecording()
        recordingInProgress.set(true)
        this.tvStatus.text = "Estado: Grabando"
        recordingThread = Thread {
            val file = File(this@MainActivity.filesDir, "recording.pcm")
            Log.e("File save: ", file.absolutePath.toString())
            val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
            try {
                FileOutputStream(file).use { outStream ->
                    while (recordingInProgress.get()) {
                        val result = recorder.read(buffer, BUFFER_SIZE)
                        if (result < 0) {
                            throw RuntimeException(
                                "Reading of audio buffer failed: " +
                                        getBufferReadFailureReason(result)
                            )
                        }
                        outStream.write(buffer.array(), 0, BUFFER_SIZE)
                        buffer.clear()
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException("Writing of recorded audio failed", e)
            }
        }
        recordingThread.start()
    }

    private fun getBufferReadFailureReason(errorCode: Int): String {
        return when (errorCode) {
            AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
            AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
            AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
            AudioRecord.ERROR -> "ERROR"
            else -> "Unknown ($errorCode)"
        }

    }
}
