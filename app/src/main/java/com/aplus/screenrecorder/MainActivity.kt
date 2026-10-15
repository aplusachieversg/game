package com.aplus.screenrecorder

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var projectionManager: MediaProjectionManager
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var outputUri: Uri? = null
    private var outputDescriptor: ParcelFileDescriptor? = null
    private var isRecording = false
    private var screenWidth = 720
    private var screenHeight = 1280
    private var density = 1
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val captureRequest = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        density = metrics.densityDpi

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 40, 28, 28)
            setBackgroundColor(0xFFF5F7FA.toInt())
        }
        val title = TextView(this).apply {
            text = "Screen Recorder"
            textSize = 28f
            setTextColor(0xFF172033.toInt())
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "本地录屏 · 无需账号或服务器"
            textSize = 16f
            setTextColor(0xFF526078.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 32)
        }
        status = TextView(this).apply {
            text = "准备就绪"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(0xFF172033.toInt())
            setPadding(12, 18, 12, 18)
        }
        startButton = Button(this).apply {
            text = "开始录屏"
            setOnClickListener { requestCapturePermission() }
        }
        stopButton = Button(this).apply {
            text = "停止并保存"
            isEnabled = false
            setOnClickListener { stopRecording() }
        }
        val note = TextView(this).apply {
            text = "当前为首个可构建版本。此版本先验证系统录屏授权与 MP4 本地保存；自由选区裁剪仍需后续实现和真机验证。"
            textSize = 14f
            setTextColor(0xFF657087.toInt())
            setPadding(4, 28, 4, 0)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(startButton, LinearLayout.LayoutParams(-1, -2))
        root.addView(stopButton, LinearLayout.LayoutParams(-1, -2))
        root.addView(note, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun requestCapturePermission() {
        startActivityForResult(projectionManager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Deprecated by Android, retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != captureRequest) return
        if (resultCode != Activity.RESULT_OK || data == null) {
            status.text = "录屏授权已取消"
            return
        }
        try {
            startRecording(resultCode, data)
        } catch (e: Exception) {
            cleanup()
            status.text = "启动失败：${e.localizedMessage ?: "未知错误"}"
            Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
        }
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ScreenRecorder_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ScreenRecorder")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("无法创建视频文件")
        outputDescriptor = contentResolver.openFileDescriptor(outputUri!!, "w")
            ?: throw IllegalStateException("无法打开视频文件")

        val videoRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
        recorder = videoRecorder
        videoRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        videoRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        videoRecorder.setOutputFile(outputDescriptor!!.fileDescriptor)
        videoRecorder.setVideoSize(screenWidth - screenWidth % 2, screenHeight - screenHeight % 2)
        videoRecorder.setVideoFrameRate(30)
        videoRecorder.setVideoEncodingBitRate(5_000_000)
        videoRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        videoRecorder.prepare()

        projection = projectionManager.getMediaProjection(resultCode, data)
        val surface = videoRecorder.surface
        virtualDisplay = projection?.createVirtualDisplay(
            "ScreenRecorderDisplay",
            screenWidth - screenWidth % 2,
            screenHeight - screenHeight % 2,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        ) ?: throw IllegalStateException("无法创建虚拟显示")
        videoRecorder.start()
        isRecording = true
        startButton.isEnabled = false
        stopButton.isEnabled = true
        status.text = "正在录制全屏画面…"
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            Toast.makeText(this, "录制时间过短，视频可能无法保存", Toast.LENGTH_LONG).show()
        }
        isRecording = false
        virtualDisplay?.release()
        virtualDisplay = null
        recorder?.reset()
        recorder?.release()
        recorder = null
        try { outputDescriptor?.close() } catch (_: Exception) {}
        outputDescriptor = null
        projection?.stop()
        projection = null
        outputUri?.let { uri ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(uri, values, null, null)
            }
            status.text = "视频已保存到 Movies/ScreenRecorder"
        }
        startButton.isEnabled = true
        stopButton.isEnabled = false
    }

    private fun cleanup() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { recorder?.reset(); recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { outputDescriptor?.close() } catch (_: Exception) {}
        outputDescriptor = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        isRecording = false
        startButton.isEnabled = true
        stopButton.isEnabled = false
    }

    override fun onDestroy() {
        if (isRecording) stopRecording()
        super.onDestroy()
    }
}
