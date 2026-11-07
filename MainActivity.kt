package com.example.blackmagicprocam

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.util.Range
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var isoText: TextView
    private lateinit var focusText: TextView
    private lateinit var recordButton: Button
    private lateinit var filterOverlay: View

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var filterOn = false
    private var slowMotionMode = false
    private var hdrMode = false
    private var isoValue = 400
    private var focusValue = 0
    private val mainExecutor: Executor by lazy { ContextCompat.getMainExecutor(this) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val cameraGranted = result[Manifest.permission.CAMERA] == true
            val audioGranted = result[Manifest.permission.RECORD_AUDIO] == true
            if (cameraGranted) startCamera()
            else toast("Camera permission is required.")
            if (!audioGranted) toast("Microphone permission denied: recording will be silent.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        if (hasCameraPermission()) startCamera() else permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildInterface() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        // A subtle preview-only tint. It is not burned into the recorded video.
        filterOverlay = View(this).apply {
            setBackgroundColor(Color.rgb(18, 38, 32))
            alpha = 0f
            isClickable = false
        }
        root.addView(filterOverlay, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 12)
            setBackgroundColor(0xB9000000.toInt())
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "BLACK MAGIC PRO CAM"
            setTextColor(Color.WHITE)
            textSize = 17f
        }
        statusText = TextView(this).apply {
            text = "  4K • 60 FPS requested"
            setTextColor(0xFFBDBDBD.toInt())
            textSize = 12f
        }
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(statusText)
        panel.addView(top)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(makeButton("REC", Color.RED) { toggleRecording() }, weightParams())
        recordButton = row1.getChildAt(0) as Button
        row1.addView(makeButton("FLIP", 0xFF333333.toInt()) { switchCamera() }, weightParams())
        row1.addView(makeButton("FILTER", 0xFF333333.toInt()) { toggleFilter() }, weightParams())
        row1.addView(makeButton("SLOW", 0xFF333333.toInt()) { toggleSlowMotion() }, weightParams())
        row1.addView(makeButton("HDR", 0xFF333333.toInt()) { toggleHdr() }, weightParams())
        panel.addView(row1)

        isoText = TextView(this).apply {
            text = "ISO: $isoValue"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        panel.addView(isoText)
        val isoSeek = SeekBar(this).apply {
            max = 31 // maps to ISO 100..3200
            progress = 3
            setOnSeekBarChangeListener(simpleSeek { p ->
                isoValue = 100 * (1 shl (p.coerceIn(0, 5))) // 100..3200
                isoText.text = "ISO: $isoValue"
                applyManualControls()
            })
        }
        panel.addView(isoSeek)

        focusText = TextView(this).apply {
            text = "FOCUS: AUTO"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        panel.addView(focusText)
        val focusSeek = SeekBar(this).apply {
            max = 100
            progress = 0
            setOnSeekBarChangeListener(simpleSeek { p ->
                focusValue = p
                focusText.text = if (p == 0) "FOCUS: AUTO" else "FOCUS: MANUAL $p%"
                applyManualControls()
            })
        }
        panel.addView(focusSeek)

        val hint = TextView(this).apply {
            text = "Manual controls depend on camera hardware. 4K/60, HDR and slow motion are device-supported features."
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 10f
        }
        panel.addView(hint)

        val params = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        root.addView(panel, params)
        setContentView(root)
    }

    private fun weightParams() =
        LinearLayout.LayoutParams(0, 44, 1f).apply { setMargins(3, 8, 3, 8) }

    private fun makeButton(label: String, color: Int, action: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            setOnClickListener { action() }
        }

    private fun simpleSeek(action: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) action(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindCamera()
            } catch (e: Exception) {
                toast("Camera startup failed: ${e.message}")
            }
        }, mainExecutor)
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            if (!provider.hasCamera(selector)) {
                toast("This camera is not available.")
                lensFacing = CameraSelector.LENS_FACING_BACK
                return
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // UHD is preferred; CameraX falls back to the highest supported quality.
            val qualitySelector = QualitySelector.from(
                Quality.UHD,
                androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)
            )
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            val capture = VideoCapture.withOutput(recorder)

            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, capture)
            videoCapture = capture
            statusText.text = "  UHD preferred • FPS depends on device"
            applyManualControls()
        } catch (e: Exception) {
            toast("Could not open camera: ${e.message}")
        }
    }

    private fun applyManualControls() {
        val activeCamera = camera ?: return
        try {
            val camera2Control = Camera2CameraControl.from(activeCamera.cameraControl)
            val builder = CaptureRequestOptions.Builder()

            // Manual ISO requires AE off. Some CameraX/device combinations may reject this.
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, isoValue.coerceIn(100, 3200))

            // Zero means autofocus. A positive slider value maps to a lens distance in diopters.
            if (focusValue == 0) {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                )
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    (focusValue / 100f) * 8f
                )
            }
            camera2Control.setCaptureRequestOptions(builder.build())
        } catch (_: Exception) {
            // Hardware or vendor camera implementation does not expose the requested manual control.
        }
    }

    private fun toggleRecording() {
        val capture = videoCapture ?: run {
            toast("Camera is not ready.")
            return
        }
        if (recording != null) {
            recording?.stop()
            recording = null
            recordButton.text = "REC"
            recordButton.setBackgroundColor(Color.RED)
            return
        }

        val folder = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: filesDir
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(folder, "BMC_$stamp.mp4")
        val output = FileOutputOptions.Builder(file).build()

        var pending: PendingRecording = capture.output.prepareRecording(this, output)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            pending = pending.withAudioEnabled()
        }

        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    recordButton.text = "STOP"
                    recordButton.setBackgroundColor(0xFF7A0000.toInt())
                    toast("Recording started")
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    recordButton.text = "REC"
                    recordButton.setBackgroundColor(Color.RED)
                    if (!event.hasError()) toast("Saved: ${file.name}")
                    else toast("Recording error: ${event.error}")
                }
            }
        }
    }

    private fun switchCamera() {
        if (recording != null) {
            toast("Stop recording before switching camera.")
            return
        }
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindCamera()
    }

    private fun toggleFilter() {
        filterOn = !filterOn
        filterOverlay.alpha = if (filterOn) 0.18f else 0f
        toast(if (filterOn) "Cinematic preview tint ON (preview only)" else "Filter OFF")
    }

    private fun toggleSlowMotion() {
        slowMotionMode = !slowMotionMode
        toast(
            if (slowMotionMode)
                "Slow motion selected. High-FPS capture needs camera-specific high-speed support; not enabled in this basic build."
            else "Slow motion selection OFF"
        )
    }

    private fun toggleHdr() {
        hdrMode = !hdrMode
        toast(
            if (hdrMode)
                "HDR selected. True HDR recording requires HLG10 support; this basic build remains SDR."
            else "HDR selection OFF"
        )
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        recording?.stop()
        recording = null
        super.onDestroy()
    }
}
