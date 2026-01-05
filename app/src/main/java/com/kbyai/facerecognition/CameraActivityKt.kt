package com.kbyai.facerecognition

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kbyai.facerecognition.SettingsActivity.Companion.getIdentifyThreshold
import com.kbyai.facerecognition.SettingsActivity.Companion.getLivenessLevel
import com.kbyai.facerecognition.SettingsActivity.Companion.getLivenessThreshold
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
    private lateinit var faceView: FaceView
    private lateinit var fotoapparat: Fotoapparat
    private lateinit var context: Context

    private var recognized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_kt)

        context = this
        cameraView = findViewById(R.id.preview)
        faceView = findViewById(R.id.faceView)

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
        faceView.setFaceBoxes(null)
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

                // Detect faces using ML Kit
                val detectedFaces = FaceRecognitionManager.detectFaces(bitmap)

                runOnUiThread {
                    faceView.setFrameSize(Size(bitmap.width, bitmap.height))
                    faceView.setDetectedFaces(detectedFaces)
                }

                if(detectedFaces.isNotEmpty()) {
                    val detectedFace = detectedFaces[0]
                    
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
                                val faceImage = Utils.cropFaceML(bitmap, detectedFace)
                                val intent = Intent(context, ResultActivity::class.java)
                                intent.putExtra("identified_face", faceImage)
                                intent.putExtra("enrolled_face", foundPerson!!.face)
                                intent.putExtra("identified_name", foundPerson!!.name)
                                intent.putExtra("employee_id", foundPerson!!.employeeId ?: "")
                                intent.putExtra("similarity", similarity)
                                intent.putExtra("liveness", 0.9f)
                                intent.putExtra("yaw", detectedFace.eulerAngleY)
                                intent.putExtra("roll", detectedFace.eulerAngleZ)
                                intent.putExtra("pitch", detectedFace.eulerAngleX)
                                startActivity(intent)
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
}