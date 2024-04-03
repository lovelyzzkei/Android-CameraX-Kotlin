package com.example.monocon

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.monocon.databinding.ActivityMainBinding
import com.qualcomm.qti.snpe.NeuralNetwork
import com.qualcomm.qti.snpe.SNPE
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture:ImageCapture ?= null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewSurface: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        // The following code demonstrates the usage of Java logging APIs to initialize logging to verbose
//        // level and then set it to info level. Logging is terminated when the app activity is destroyed.
//        SNPE.logger.initializeLogging(
//            applicationContext as Application,
//            NeuralNetwork.LogLevel.LOG_VERBOSE
//        )
//        SNPE.logger.setLogLevel(NeuralNetwork.LogLevel.LOG_INFO)
//
//        if (savedInstanceState == null) {
//            val transaction = fragmentManager.beginTransaction()
//            transaction.add(R.id.main_content, ModelCatalogueFragment.create())
//            transaction.commit()
//        }

        previewSurface = binding.previewSurface
        surfaceHolder = previewSurface.holder

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionGranted()) {
            Toast.makeText(
                this,
                "Permission allowed",
                Toast.LENGTH_LONG).show()
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                Constants.REQUIRED_PERMISSIONS,
                Constants.REQUEST_CODE_PERMISSIONS
            )
        }

        binding.btnTakePhoto.setOnClickListener {
            takePhoto()
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {mFile ->
            File(mFile, resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }

        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(Constants.FILE_NAME_FORMAT,
                Locale.getDefault()).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOption = ImageCapture
            .OutputFileOptions
            .Builder(photoFile)
            .build()

        imageCapture.takePicture(
            outputOption, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo saved"

                    Toast.makeText(
                        this@MainActivity,
                        "$msg $savedUri",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(Constants.TAG,
                        "onError: ${exception.message}",
                        exception)
                }

            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionGranted()) {
                // Our code
                startCamera()
            }
            else {
                Toast.makeText(
                    this,
                    "Permission allowed",
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun printBufferContent(bytes: ByteArray) {
        val stringBuilder = StringBuilder()
        for (byte in bytes) {
            stringBuilder.append(byte.toInt().and(0xFF).toString(16).padStart(2, '0'))
        }
        Log.d("BufferContent", stringBuilder.toString())
    }

    private fun updatePreviewSurface(resizedBytes: ByteArray) {
        val bitmap = BitmapFactory.decodeByteArray(resizedBytes, 0, resizedBytes.size)
        if (bitmap != null) {
            val canvas = surfaceHolder.lockCanvas()
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            surfaceHolder.unlockCanvasAndPost(canvas)
        } else {
            Log.e("PreviewSurface", "Failed to decode bitmap from byte array")
        }

    }

    private fun Bitmap.rotate(degrees: Float): Bitmap =
        Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, true)


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        // 카메라에서 이미지 데이터를 받아와서 이것저것 처리하는 부분은 여기서 하면 될듯
        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // Convert the ByteArray to a Bitmap
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val dstWidth = 1280
            val dstHeight = 384

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
            val imageBytes = out.toByteArray()

            val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            // Resizing bitmap if not null
            if (originalBitmap != null) {

                // originalBitmap = originalBitmap.rotate(90F)
                val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, dstWidth, dstHeight, false)

                // Convert the resized Bitmap back to a ByteArray
                val resizedBytes = BitmapUtils.bitmapToByteArray(resizedBitmap)

                // Update the preview surface with the resized ByteArray
                updatePreviewSurface(resizedBytes)
            }
            else {
                Log.e("ImageAnalysis", "Null originalBitmap")
            }

            imageProxy.close()
        }

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
//            val preview = Preview.Builder()
//                .build()
//                .also { mPreview ->
//                    mPreview.setSurfaceProvider(
//                        binding.viewFinder.surfaceProvider
//                    )
//                }
            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageCapture, imageAnalysis
                )
            } catch (e: Exception) {
                Log.d(Constants.TAG, "startCamera Fail")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionGranted() =
        Constants.REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                baseContext, it
            ) == PackageManager.PERMISSION_GRANTED
        }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}