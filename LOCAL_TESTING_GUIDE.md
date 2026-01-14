# Quick Start: Local Testing with Fixed Network Security

## What Was Fixed

Your Android app was getting: **"Clear text communication to 10.0.2.2 not permitted"**

This is fixed by allowing HTTP traffic to localhost/emulator IPs.

---

## Files Modified

### 1. ✅ Created: `network_security_config.xml`
**Location**: `app/src/main/res/xml/network_security_config.xml`

```xml
<network-security-config>
    <!-- Allow HTTP to localhost for testing -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
</network-security-config>
```

### 2. ✅ Updated: `AndroidManifest.xml`
Added to `<application>` tag:
```xml
android:networkSecurityConfig="@xml/network_security_config"
```

---

## How to Test Now

### Step 1: Ensure Backend is Running

```powershell
# Check if backend is running
curl http://localhost:8000/health

# Should return: {"status":"degraded",...}
```

### Step 2: Rebuild Android App

```powershell
cd c:\Users\sruja\OneDrive\Documents\GitHub\Employee-Attendance-Marking-Application

# Clean build (with network config)
.\gradlew clean build

# Install to emulator
.\gradlew installDebug

# Or in Android Studio: Run → Run 'app'
```

### Step 3: Test Enrollment

1. Open app in emulator/device
2. Click **"Enroll Employee"**
3. Select/take a face photo
4. Enter Employee ID: `EMP001`
5. Enter Name: `Test User`
6. Click **Enroll**

**Expected Result**: ✅ Should enroll successfully (NO SSL ERROR)

### Step 4: Test Identification

1. Click **"Identify"**
2. Camera opens
3. Point at enrolled person's face
4. Face should be detected and identified

**Expected Result**: ✅ Should identify and mark attendance

---

## If It Still Doesn't Work

### Issue 1: "Connection Failed"
```powershell
# Check backend is running
curl http://localhost:8000/health

# If not running, start it:
cd backend
Push-Location C:\Users\sruja\OneDrive\Documents\GitHub\Employee-Attendance-Marking-Application\backend; 
C:/Users/sruja/OneDrive/Documents/GitHub/Employee-Attendance-Marking-Application/.venv312/Scripts/python.exe -m uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### Issue 2: Face Not Detected
- ✅ Good lighting required
- ✅ Face should be centered
- ✅ Not too close, not too far
- ✅ Straight angle (frontal)

### Issue 3: Still Getting SSL Error
1. **Verify** `network_security_config.xml` exists in `app/src/main/res/xml/`
2. **Verify** `AndroidManifest.xml` has `android:networkSecurityConfig="@xml/network_security_config"`
3. **Clean rebuild**: `.\gradlew clean build`
4. **Reinstall**: `.\gradlew installDebug`

---

## Production: AWS Deployment

Once local testing works:

1. **Create AWS account** (free tier): https://aws.amazon.com/
2. **Launch EC2 instance** (t3.micro, free for 12 months)
3. **Follow** `AWS_DEPLOYMENT.md` steps 1-11
4. **Update Android URL** in `PythonFaceService.kt`:
   ```kotlin
   private var BASE_URL = "https://your-aws-domain.com"
   ```
5. **Rebuild and deploy** Android app

---

## Architecture

```
┌──────────────────────┐
│   Android App        │  Uses HTTP (allowed by network config)
│   (Emulator)         │
└──────────┬───────────┘
           │ 10.0.2.2:8000
           ▼
┌──────────────────────┐
│  FastAPI Backend     │
│  (localhost:8000)    │
└──────────┬───────────┘
           │ Supabase API
           ▼
   ┌───────────────┐
   │   Supabase    │  PostgreSQL Database
   │  (Cloud)      │
   └───────────────┘
```

---

## Next Steps

1. ✅ Rebuild Android app with `.\gradlew clean build`
2. ✅ Install with `.\gradlew installDebug`
3. ✅ Test enrollment in app
4. ✅ Test identification in app
5. 🚀 If working: Deploy to AWS using `AWS_DEPLOYMENT.md`

---

## Status Check

| Item | Status |
|------|--------|
| Network config created | ✅ |
| AndroidManifest updated | ✅ |
| Backend running | ✅ |
| Android app rebuilt | 🔄 (In progress) |
| Local testing | 🔄 (Next step) |
| AWS deployment | 📋 (Ready when needed) |

**Current Step**: Waiting for Android build to complete...

Once complete, install and test! 🚀
