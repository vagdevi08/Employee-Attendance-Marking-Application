# Backend & Android App Integration Guide

## ✅ What Was Connected

The Android app is now configured to communicate with your FastAPI backend.

### Changes Made:

1. **Android App** ([PythonFaceService.kt](app/src/main/java/com/kbyai/facerecognition/PythonFaceService.kt)):
   - Updated BASE_URL from `http://10.0.2.2:5000` → `http://10.0.2.2:8000`
   - Supports API key authentication without hardcoding secrets
   - Fixed JSON field names to match backend (`user_id` instead of `employee_id`)
   - App can send `X-API-Key` header when configured at runtime

2. **Backend** ([main.py](backend/main.py)):
   - Added `/identify` endpoint for unknown face identification
   - Searches all enrolled faces to find best match
   - Automatically marks attendance when match found
   - Added `get_all_face_embeddings()` method to database

## 🚀 How to Use

### 1. Start the Backend (Required First)

```powershell
cd backend
.\.venv312\Scripts\Activate.ps1
python -m uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Backend will be available at:
- **Local**: http://localhost:8000
- **Emulator**: http://10.0.2.2:8000
- **Real Device**: http://YOUR_COMPUTER_IP:8000

### 2. Build & Run Android App

```powershell
cd c:\Users\sruja\OneDrive\Documents\GitHub\Employee-Attendance-Marking-Application
.\gradlew installDebug
```

Or in Android Studio: **Run → Run 'app'**

## 📡 API Endpoints Used by Android App

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/health` | GET | Check if backend is running |
| `/enroll` | POST | Register a new face |
| `/identify` | POST | Identify face & mark attendance |

All requests require header: `X-API-Key: your-secret-api-key-here`

Set the API key at runtime in the app (do not hardcode in source):

```kotlin
// e.g., load from encrypted storage or remote config
PythonFaceService.setApiKey("your-secret-api-key-here")
```

## 🔧 For Real Device Testing

If testing on a physical device (not emulator):

1. Find your computer's local IP:
   ```powershell
   ipconfig
   # Look for IPv4 Address under your active network
   ```

2. Update Android app:
   ```kotlin
   // In PythonFaceService.kt, line 23:
   private var BASE_URL = "http://YOUR_COMPUTER_IP:8000"
   // Example: "http://192.168.1.100:8000"
   ```

3. Ensure both devices are on **same WiFi network**

## 🗄️ Database Setup (Required)

Before enrolling faces, create the database tables:

1. Go to https://supabase.com and open your project
2. Navigate to **SQL Editor**
3. Copy and paste contents from [backend/supabase_schema.sql](backend/supabase_schema.sql)
4. Click **Run** to create tables

Tables created:
- `face_embeddings` - Stores face data (embeddings only, no images)
- `attendance` - Stores attendance records with timestamps

## 🎯 Testing Flow

### Test 1: Health Check
1. Open Android app
2. App should automatically check backend health on startup
3. Check logcat: `PythonFaceService: Health check: ...`

### Test 2: Face Enrollment
1. Click **"Enroll Employee"** in app
2. Take/select a clear face photo
3. Enter Employee ID (e.g., "EMP001") and Name
4. Click **"Enroll"**
5. Should see success message

### Test 3: Face Identification
1. Click **"Identify"** in app
2. Camera opens with live detection
3. Point at enrolled person's face
4. App auto-identifies and marks attendance
5. Check backend logs for confirmation

## 📊 View Data in Supabase

1. Go to Supabase dashboard
2. Click **Table Editor**
3. View tables:
   - `face_embeddings` - See enrolled users
   - `attendance` - See attendance records

## 🐛 Troubleshooting

### Backend not connecting
- ✅ Check backend is running: http://localhost:8000
- ✅ Check URL in PythonFaceService.kt matches your setup
- ✅ For device: Computer firewall allows port 8000
- ✅ Check logcat: `adb logcat | grep PythonFaceService`

### "Invalid API key" error
- ✅ Ensure backend `API_KEY` is set in [backend/.env](backend/.env)
- ✅ Use the same value in the app via `PythonFaceService.setApiKey(...)` (do not hardcode)
- ✅ In Supabase, never expose the service role key to the client

### "No faces enrolled yet"
- ✅ Run SQL schema first (see Database Setup above)
- ✅ Enroll at least one face using app
- ✅ Verify in Supabase Table Editor

### Face not detecting
- ✅ Ensure good lighting
- ✅ Face should be centered and clear
- ✅ Try adjusting distance from camera

## 📝 Important Notes

- **Emulator URL**: Always use `10.0.2.2` (maps to host's localhost)
- **Security**: Never hardcode secrets in the Android app. Configure the API key at runtime (e.g., EncryptedSharedPreferences, device provisioning, or a short‑lived token from your backend).
- **Database**: Stores face embeddings (mathematical vectors) NOT raw images
- **Privacy**: All processing happens locally; images not sent to cloud storage
