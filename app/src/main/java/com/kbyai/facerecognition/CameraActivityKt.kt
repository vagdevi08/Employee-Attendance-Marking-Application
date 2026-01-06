package com.kbyai.facerecognition

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.os.Bundle
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kbyai.facerecognition.SettingsActivity.Companion.getIdentifyThreshold
import com.kbyai.facerecognition.SettingsActivity.Companion.getLivenessLevel
import com.kbyai.facerecognition.SettingsActivity.Companion.getLivenessThreshold
import husaynhakeem.io.facedetector.FaceBounds
import husaynhakeem.io.facedetector.FaceBoundsOverlay
import husaynhakeem.io.facedetector.FaceDetector
import husaynhakeem.io.facedetector.LensFacing
import husaynhakeem.io.facedetector.Frame as DetectorFrame
import io.fotoapparat.Fotoapparat
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.preview.Frame
import io.fotoapparat.preview.FrameProcessor
import io.fotoapparat.selector.front
import io.fotoapparat.selector.back
import io.fotoapparat.view.CameraView
import kotlin.math.min

class CameraActivityKt : AppCompatActivity() {

    val TAG = CameraActivity::class.java.simpleName
    val PREVIEW_WIDTH = 720
    val PREVIEW_HEIGHT = 1280

    private lateinit var cameraView: CameraView
    private lateinit var faceBoundsOverlay: FaceBoundsOverlay
    private lateinit var faceDetector: FaceDetector
    private lateinit var fotoapparat: Fotoapparat
    private lateinit var context: Context

    @Volatile
    private var latestFaceBounds: List<FaceBounds> = emptyList()

    private var recognized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_kt)

        context = this
        cameraView = findViewById(R.id.preview)
        faceBoundsOverlay = findViewById(R.id.faceBoundsOverlay)
        faceDetector = FaceDetector(faceBoundsOverlay).apply {
            setonFaceDetectionFailureListener(object : husaynhakeem.io.facedetector.FaceDetector.OnFaceDetectionResultListener {
                override fun onSuccess(faceBounds: List<FaceBounds>) {
                    latestFaceBounds = faceBounds
                }

                override fun onFailure(exception: Exception) {
                    // Silently fail, will use ML Kit fallback
                }
            })
        }

        if (SettingsActivity.getCameraLens(context) == CameraSelector.LENS_FACING_BACK) {
            fotoapparat = Fotoapparat.with(this)
                .into(cameraView)
                .lensPosition(back())
                .frameProcessor(FaceFrameProcessor())
                .previewResolution { Resolution(PREVIEW_HEIGHT,PREVIEW_WIDTH) }
                .build()
        } else  {
            fotoapparat = Fotoapparat.with(this)
                .into(cameraView)
                .lensPosition(front())
                .frameProcessor(FaceFrameProcessor())
                .previewResolution { Resolution(PREVIEW_HEIGHT,PREVIEW_WIDTH) }
                .build()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            fotoapparat.start()
        }
    }

    override fun onResume() {
        super.onResume()
        recognized = false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fotoapparat.start()
        }
    }

    override fun onPause() {
        super.onPause()
        fotoapparat.stop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                fotoapparat.start()
            }
        }
    }

    inner class FaceFrameProcessor : FrameProcessor {

        override fun process(frame: Frame) {

            if(recognized == true) {
                return
            }

            try {
                // Convert frame to bitmap using ML Kit compatible method
                // Fotoapparat provides NV21 bytes, so use nv21ToBitmap
                val bitmap = ImageUtils.nv21ToBitmap(frame.image, frame.size.width, frame.size.height)

                // Feed frame to library face detector for overlay rendering
                val lensFacing = if (SettingsActivity.getCameraLens(context) == CameraSelector.LENS_FACING_BACK) {
                    LensFacing.BACK
                } else {
                    LensFacing.FRONT
                }
                faceDetector.process(
                    DetectorFrame(
                        data = frame.image,
                        rotation = frame.rotation,
                        size = Size(frame.size.width, frame.size.height),
                        format = ImageFormat.NV21,
                        lensFacing = lensFacing
                    )
                )

                // Prefer android-face-detector results for recognition (fallback to ML Kit)
                val detectedFace = latestFaceBounds.firstOrNull()?.let { fb ->
                    convertFaceBoundsToDetectedFace(fb)
                } ?: FaceRecognitionManager.detectFaces(bitmap).firstOrNull()

                if(detectedFace != null) {
                    
                    // Validate face size (must be >10% of image)
                    val faceWidth = detectedFace.right - detectedFace.left
                    val faceHeight = detectedFace.bottom - detectedFace.top
                    val faceArea = faceWidth * faceHeight
                    val imageArea = bitmap.width * bitmap.height
                    val faceSizeRatio = faceArea / imageArea
                    
                    if (faceSizeRatio > 0.1f) {
                        // Extract embeddings
                        val embeddings = FaceRecognitionManager.extractEmbeddings(bitmap, detectedFace)

                        var maxSimilarity = 0f
                        var identifiedPerson: Person? = null
                        
                        for (person in DBManager.personList) {
                            val storedEmbeddings = convertByteArrayToFloatArray(person.templates)
                            val similarity = FaceRecognitionManager.calculateSimilarity(embeddings, storedEmbeddings)
                            
                            if (similarity > maxSimilarity) {
                                maxSimilarity = similarity
                                identifiedPerson = person
                            }
                        }
                        
                        if (maxSimilarity > SettingsActivity.getIdentifyThreshold(context)) {
                            recognized = true
                            val foundPerson = identifiedPerson
                            val similarity = maxSimilarity

                            runOnUiThread {
                                markAttendanceAndFinish(foundPerson!!, similarity)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun convertByteArrayToFloatArray(byteArray: ByteArray): FloatArray {
        if (byteArray.isEmpty()) {
            return FloatArray(128)
        }
        
        val floatArray = FloatArray(min(byteArray.size / 4, 128))
        for (i in floatArray.indices) {
            val index = i * 4
            if (index + 3 < byteArray.size) {
                val intBits = ((byteArray[index].toInt() and 0xFF) shl 24) or
                              ((byteArray[index + 1].toInt() and 0xFF) shl 16) or
                              ((byteArray[index + 2].toInt() and 0xFF) shl 8) or
                              (byteArray[index + 3].toInt() and 0xFF)
                floatArray[i] = Float.fromBits(intBits)
            }
        }
        
        return if (floatArray.size < 128) {
            FloatArray(128).also { padded ->
                floatArray.copyInto(padded)
            }
        } else {
            floatArray
        }
    }

    private fun convertFaceBoundsToDetectedFace(faceBounds: FaceBounds): FaceRecognitionManager.DetectedFace {
        val rect = android.graphics.Rect(
            faceBounds.box.left.toInt(),
            faceBounds.box.top.toInt(),
            faceBounds.box.right.toInt(),
            faceBounds.box.bottom.toInt()
        )
        return FaceRecognitionManager.DetectedFace(
            boundingBox = rect,
            eulerAngleX = 0f,
            eulerAngleY = 0f,
            eulerAngleZ = 0f,
            left = rect.left.toFloat(),
            top = rect.top.toFloat(),
            right = rect.right.toFloat(),
            bottom = rect.bottom.toFloat(),
            landmarks = emptyList(),
            contours = emptyList(),
            liveness = 0.9f,
            yaw = 0f,
            roll = 0f,
            pitch = 0f
        )
    }

    private fun markAttendanceAndFinish(person: Person, similarity: Float) {
        val attendanceTime = System.currentTimeMillis()
        val employeeId = person.employeeId ?: ""
        val employeeName = person.name

        if (employeeId.isNotEmpty()) {
            val attendanceType = AttendanceHelper.determineAttendanceType(this, employeeId, attendanceTime)

            if (attendanceType != "NONE") {
                val dbManager = DBManager(this)
                dbManager.insertAttendance(employeeId, employeeName, attendanceTime, attendanceType)

                val typeText = if (attendanceType == "CHECK_IN") "Check-in" else "Check-out"
                android.widget.Toast.makeText(
                    this,
                    "$typeText successful for $employeeName (Similarity: ${String.format("%.2f", similarity)})",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                val message = AttendanceHelper.getAttendanceTypeMessage(this, employeeId, attendanceTime)
                android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            // Fallback for employees without ID
            val dbManager = DBManager(this)
            dbManager.insertAttendance("", employeeName, attendanceTime, "CHECK_IN")
            android.widget.Toast.makeText(
                this,
                "Attendance marked for $employeeName (Similarity: ${String.format("%.2f", similarity)})",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        // Close camera activity after short delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            finish()
        }, 2000)
    }
}