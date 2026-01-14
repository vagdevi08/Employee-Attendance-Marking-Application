package com.kbyai.facerecognition

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service to communicate with Python face recognition backend
 */
object PythonFaceService {
    private const val TAG = "PythonFaceService"
    
    // Change this to your Python service URL
    // For emulator: http://10.0.2.2:8000
    // For real device: http://YOUR_COMPUTER_IP:8000
    private var BASE_URL = "http://192.168.0.6:8000"
    
    // API Key from backend .env file
    private const val API_KEY = "sb_secret_faDP584WcLd7x_-CUyS-IA_tSaMla_l"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    fun setBaseUrl(url: String) {
        BASE_URL = url
    }
    
    fun getBaseUrl(): String = BASE_URL
    
    /**
     * Convert bitmap to base64 string
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    /**
     * Check if service is running
     */
    fun checkHealth(callback: (Boolean, String) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/health")
            .addHeader("X-API-Key", API_KEY)
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Health check failed", e)
                callback(false, e.message ?: "Connection failed")
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        Log.d(TAG, "Health check: $body")
                        callback(true, "Service is running")
                    } else {
                        callback(false, "Service returned: ${it.code}")
                    }
                }
            }
        })
    }
    
    /**
     * Enroll a new face
     */
    fun enrollFace(
        employeeId: String,
        name: String,
        bitmap: Bitmap,
        callback: (Boolean, String) -> Unit
    ) {
        val imageBase64 = bitmapToBase64(bitmap)
        
        val json = JSONObject().apply {
            put("user_id", employeeId)
            put("name", name)
            put("image", imageBase64)
        }
        
        val body = json.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url("$BASE_URL/enroll")
            .addHeader("X-API-Key", API_KEY)
            .post(body)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Enroll failed", e)
                callback(false, "Connection failed: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string() ?: ""
                    Log.d(TAG, "Enroll response: $responseBody")
                    
                    if (it.isSuccessful) {
                        val jsonResponse = JSONObject(responseBody)
                        val success = jsonResponse.optBoolean("success", false)
                        val message = jsonResponse.optString("message", "Enrolled successfully")
                        callback(success, message)
                    } else {
                        val jsonResponse = JSONObject(responseBody)
                        val error = jsonResponse.optString("error", "Enrollment failed")
                        callback(false, error)
                    }
                }
            }
        })
    }
    
    /**
     * Identify a face
     */
    fun identifyFace(
        bitmap: Bitmap,
        threshold: Float = 0.6f,
        callback: (IdentifyResult) -> Unit
    ) {
        val imageBase64 = bitmapToBase64(bitmap)
        
        val json = JSONObject().apply {
            put("image", imageBase64)
            put("user_id", "unknown")  // Not used by backend /identify endpoint
        }
        
        val body = json.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url("$BASE_URL/identify")
            .addHeader("X-API-Key", API_KEY)
            .post(body)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Identify failed", e)
                callback(IdentifyResult(
                    identified = false,
                    error = "Connection failed: ${e.message}"
                ))
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string() ?: ""
                    Log.d(TAG, "Identify response: $responseBody")
                    
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        
                        if (it.isSuccessful) {
                            val identified = jsonResponse.optBoolean("identified", false)
                            
                            if (identified) {
                                callback(IdentifyResult(
                                    identified = true,
                                    employeeId = jsonResponse.optString("employee_id", ""),
                                    name = jsonResponse.optString("name", ""),
                                    similarity = jsonResponse.optDouble("similarity", 0.0).toFloat(),
                                    distance = jsonResponse.optDouble("distance", 1.0).toFloat()
                                ))
                            } else {
                                val message = jsonResponse.optString("message", "No match found")
                                callback(IdentifyResult(
                                    identified = false,
                                    message = message
                                ))
                            }
                        } else {
                            val error = jsonResponse.optString("error", "Identification failed")
                            callback(IdentifyResult(
                                identified = false,
                                error = error
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing response", e)
                        callback(IdentifyResult(
                            identified = false,
                            error = "Error parsing response: ${e.message}"
                        ))
                    }
                }
            }
        })
    }
    
    /**
     * List all enrolled faces
     */
    fun listEnrolled(callback: (List<EnrolledPerson>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/list")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "List failed", e)
                callback(emptyList())
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val responseBody = it.body?.string() ?: ""
                        val jsonResponse = JSONObject(responseBody)
                        val enrolledArray = jsonResponse.optJSONArray("enrolled")
                        
                        val list = mutableListOf<EnrolledPerson>()
                        if (enrolledArray != null) {
                            for (i in 0 until enrolledArray.length()) {
                                val item = enrolledArray.getJSONObject(i)
                                list.add(EnrolledPerson(
                                    employeeId = item.optString("employee_id", ""),
                                    name = item.optString("name", ""),
                                    enrolledAt = item.optString("enrolled_at", "")
                                ))
                            }
                        }
                        callback(list)
                    } else {
                        callback(emptyList())
                    }
                }
            }
        })
    }
    
    /**
     * Delete an enrolled face
     */
    fun deleteFace(employeeId: String, callback: (Boolean, String) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/delete/$employeeId")
            .delete()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Delete failed", e)
                callback(false, "Connection failed: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string() ?: ""
                    if (it.isSuccessful) {
                        val jsonResponse = JSONObject(responseBody)
                        val message = jsonResponse.optString("message", "Deleted successfully")
                        callback(true, message)
                    } else {
                        val jsonResponse = JSONObject(responseBody)
                        val error = jsonResponse.optString("error", "Delete failed")
                        callback(false, error)
                    }
                }
            }
        })
    }
    
    data class IdentifyResult(
        val identified: Boolean,
        val employeeId: String = "",
        val name: String = "",
        val similarity: Float = 0f,
        val distance: Float = 1f,
        val message: String = "",
        val error: String = ""
    )
    
    data class EnrolledPerson(
        val employeeId: String,
        val name: String,
        val enrolledAt: String
    )
}
