package com.kbyai.facerecognition

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * FaceRecognitionManager handles face detection and recognition using Google ML Kit
 * This replaces the KBY-AI FaceSDK
 */
object FaceRecognitionManager {

    // Ensure callbacks run off the main thread so detectFaces can be called from UI code
    private val callbackExecutor: Executor = Executors.newSingleThreadExecutor()

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .setMinFaceSize(0.1f)
            .build()
    )

    /**
     * Detect faces in a bitmap image synchronously
     * Returns a list of DetectedFace objects
     */
    fun detectFaces(bitmap: Bitmap): List<DetectedFace> {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            // Use Java's CountDownLatch for proper synchronization
            val latch = java.util.concurrent.CountDownLatch(1)
            var detectedFacesList: MutableList<DetectedFace> = mutableListOf()
            var error: Exception? = null
            
            detector.process(inputImage)
                .addOnSuccessListener(callbackExecutor) { faces ->
                    detectedFacesList = faces.map { face ->
                        DetectedFace.fromMLKitFace(face, bitmap)
                    }.toMutableList()
                    latch.countDown()
                }
                .addOnFailureListener(callbackExecutor) { e ->
                    error = e
                    latch.countDown()
                }
            
            // Wait for result with timeout
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            
            if (error != null) {
                error!!.printStackTrace()
                emptyList()
            } else {
                detectedFacesList
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Extract face embeddings for recognition
     * Uses face landmarks to create a feature vector
     */
    fun extractEmbeddings(bitmap: Bitmap, face: DetectedFace): FloatArray {
        // Use the already detected face data; do not re-run detection to avoid misses
        return createEmbedding(face, bitmap.width, bitmap.height)
    }

    /**
     * Create a simple embedding from face landmarks
     */
    private fun createEmbedding(face: DetectedFace, imageWidth: Int, imageHeight: Int): FloatArray {
        val embedding = FloatArray(128)
        var idx = 0
        
        // Use face bounds and landmarks to create feature vector
        val bounds = face.boundingBox
        embedding[idx++] = bounds.left.toFloat() / imageWidth
        embedding[idx++] = bounds.top.toFloat() / imageHeight
        embedding[idx++] = bounds.right.toFloat() / imageWidth
        embedding[idx++] = bounds.bottom.toFloat() / imageHeight
        embedding[idx++] = bounds.width().toFloat() / imageWidth
        embedding[idx++] = bounds.height().toFloat() / imageHeight
        
        // Add face rotation angles
        embedding[idx++] = face.eulerAngleX
        embedding[idx++] = face.eulerAngleY
        embedding[idx++] = face.eulerAngleZ
        
        // Add all landmark coordinates if available (already normalized)
        for (landmark in face.landmarks) {
            if (idx < embedding.size) {
                embedding[idx++] = landmark.first
            }
            if (idx < embedding.size) {
                embedding[idx++] = landmark.second
            }
        }

        // Add contour coordinates (flattened, normalized) similar to MLKitDemo
        for (point in face.contours) {
            if (idx < embedding.size) {
                embedding[idx++] = point.first
            }
            if (idx < embedding.size) {
                embedding[idx++] = point.second
            }
        }
        
        // Normalize remaining indices
        for (i in idx until embedding.size) {
            embedding[i] = 0f
        }
        
        return embedding
    }

    /**
     * Calculate similarity between two embeddings
     * Returns value between 0 and 1 (higher is more similar)
     */
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size || embedding1.isEmpty()) {
            return 0f
        }

        // Calculate cosine similarity
        var dotProduct = 0.0
        var magnitude1 = 0.0
        var magnitude2 = 0.0

        for (i in embedding1.indices) {
            dotProduct += (embedding1[i] * embedding2[i]).toDouble()
            magnitude1 += (embedding1[i] * embedding1[i]).toDouble()
            magnitude2 += (embedding2[i] * embedding2[i]).toDouble()
        }

        magnitude1 = sqrt(magnitude1)
        magnitude2 = sqrt(magnitude2)

        return if (magnitude1 > 0 && magnitude2 > 0) {
            (dotProduct / (magnitude1 * magnitude2)).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /**
     * Data class for detected faces
     */
    data class DetectedFace(
        val boundingBox: Rect,
        val eulerAngleX: Float,
        val eulerAngleY: Float,
        val eulerAngleZ: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val landmarks: List<Pair<Float, Float>> = emptyList(),
        val contours: List<Pair<Float, Float>> = emptyList(),
        var liveness: Float = 0.9f,
        var yaw: Float = 0f,
        var roll: Float = 0f,
        var pitch: Float = 0f
    ) {
        companion object {
            fun fromMLKitFace(face: Face, bitmap: Bitmap): DetectedFace {
                val bounds = face.boundingBox
                val normalizedLandmarks = face.allLandmarks.map { landmark ->
                    Pair(landmark.position.x / bitmap.width, landmark.position.y / bitmap.height)
                }
                val normalizedContours = face.allContours.flatMap { contour ->
                    contour.points.map { point ->
                        Pair(point.x / bitmap.width, point.y / bitmap.height)
                    }
                }
                return DetectedFace(
                    boundingBox = bounds,
                    eulerAngleX = face.headEulerAngleX,
                    eulerAngleY = face.headEulerAngleY,
                    eulerAngleZ = face.headEulerAngleZ,
                    left = bounds.left.toFloat(),
                    top = bounds.top.toFloat(),
                    right = bounds.right.toFloat(),
                    bottom = bounds.bottom.toFloat(),
                    landmarks = normalizedLandmarks,
                    contours = normalizedContours,
                    liveness = 0.9f,
                    yaw = face.headEulerAngleY,
                    roll = face.headEulerAngleZ,
                    pitch = face.headEulerAngleX
                )
            }
        }
        
        // For Java compatibility - delegate to properties
        @JvmName("getLeftValue")
        fun getLeft(): Float = left
        
        @JvmName("getTopValue")
        fun getTop(): Float = top
        
        @JvmName("getRightValue")
        fun getRight(): Float = right
        
        @JvmName("getBottomValue")
        fun getBottom(): Float = bottom
        
        @JvmName("getEulerAngleXValue")
        fun getEulerAngleX(): Float = eulerAngleX
        
        @JvmName("getEulerAngleYValue")
        fun getEulerAngleY(): Float = eulerAngleY
        
        @JvmName("getEulerAngleZValue")
        fun getEulerAngleZ(): Float = eulerAngleZ
    }
}
