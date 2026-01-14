# API Request Examples

## Base URL
```
http://your-ec2-public-ip:8000
```

## Authentication
All requests require `X-API-Key` header:
```
X-API-Key: your-secret-api-key-here
```

---

## 1. Health Check

**Endpoint:** `GET /health`

**Request:**
```bash
curl -X GET "http://your-server:8000/health"
```

**Response:**
```json
{
  "status": "healthy",
  "model_loaded": true,
  "database_connected": true
}
```

---

## 2. Enroll Face

**Endpoint:** `POST /enroll`

**Request:**
```bash
curl -X POST "http://your-server:8000/enroll" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-api-key-here" \
  -d '{
    "user_id": "EMP001",
    "name": "John Doe",
    "image": "base64_encoded_image_here"
  }'
```

**Request Body (JSON):**
```json
{
  "user_id": "EMP001",
  "name": "John Doe",
  "image": "/9j/4AAQSkZJRgABAQAAAQABAAD..."
}
```

**Success Response:**
```json
{
  "success": true,
  "message": "Successfully enrolled John Doe",
  "user_id": "EMP001"
}
```

**Error Response:**
```json
{
  "success": false,
  "message": "No face detected in image. Please ensure face is clearly visible."
}
```

---

## 3. Recognize Face and Mark Attendance

**Endpoint:** `POST /recognize`

**Request:**
```bash
curl -X POST "http://your-server:8000/recognize" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-api-key-here" \
  -d '{
    "user_id": "EMP001",
    "image": "base64_encoded_image_here"
  }'
```

**Request Body (JSON):**
```json
{
  "user_id": "EMP001",
  "image": "/9j/4AAQSkZJRgABAQAAAQABAAD..."
}
```

**Success Response (Attendance Marked):**
```json
{
  "matched": true,
  "confidence": 0.92,
  "message": "Attendance marked successfully for John Doe",
  "attendance_id": "550e8400-e29b-41d4-a716-446655440000",
  "attendance_count_today": 1
}
```

**Response (Face Not Matched):**
```json
{
  "matched": false,
  "confidence": 0.65,
  "message": "Face does not match. Confidence: 0.65"
}
```

**Response (Already Marked Attendance):**
```json
{
  "matched": true,
  "confidence": 0.91,
  "message": "Attendance already marked 2 times today. Maximum allowed: 2",
  "attendance_count_today": 2
}
```

**Response (User Not Enrolled):**
```json
{
  "matched": false,
  "message": "User EMP001 not enrolled. Please enroll first."
}
```

---

## Python Example (from Android)

```python
import requests
import base64

# Read and encode image
with open("face.jpg", "rb") as f:
    image_base64 = base64.b64encode(f.read()).decode()

# Enroll
response = requests.post(
    "http://your-server:8000/enroll",
    headers={"X-API-Key": "your-secret-api-key-here"},
    json={
        "user_id": "EMP001",
        "name": "John Doe",
        "image": image_base64
    }
)
print(response.json())

# Recognize
response = requests.post(
    "http://your-server:8000/recognize",
    headers={"X-API-Key": "your-secret-api-key-here"},
    json={
        "user_id": "EMP001",
        "image": image_base64
    }
)
print(response.json())
```

---

## Kotlin/Android Example

```kotlin
import okhttp3.*
import org.json.JSONObject
import android.util.Base64

val client = OkHttpClient()
val baseUrl = "http://your-ec2-ip:8000"
val apiKey = "your-secret-api-key-here"

// Convert bitmap to base64
fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}

// Enroll face
fun enrollFace(userId: String, name: String, bitmap: Bitmap) {
    val json = JSONObject().apply {
        put("user_id", userId)
        put("name", name)
        put("image", bitmapToBase64(bitmap))
    }
    
    val body = RequestBody.create(
        "application/json".toMediaType(),
        json.toString()
    )
    
    val request = Request.Builder()
        .url("$baseUrl/enroll")
        .header("X-API-Key", apiKey)
        .post(body)
        .build()
    
    client.newCall(request).execute()
}

// Recognize face
fun recognizeFace(userId: String, bitmap: Bitmap) {
    val json = JSONObject().apply {
        put("user_id", userId)
        put("image", bitmapToBase64(bitmap))
    }
    
    val body = RequestBody.create(
        "application/json".toMediaType(),
        json.toString()
    )
    
    val request = Request.Builder()
        .url("$baseUrl/recognize")
        .header("X-API-Key", apiKey)
        .post(body)
        .build()
    
    client.newCall(request).execute()
}
```
