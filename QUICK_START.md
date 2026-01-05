# Quick Start: Building Your ML Kit-Based App

## 1. Verify All Changes Are in Place

```bash
# Navigate to project root
cd /path/to/Employee-Attendance-Marking-Application

# Verify key files exist
ls -la app/src/main/java/com/kbyai/facerecognition/FaceRecognitionManager.kt
ls -la app/src/main/java/com/kbyai/facerecognition/ImageUtils.kt
```

## 2. Clean Build

```bash
# Remove old build artifacts
./gradlew clean

# Build the project
./gradlew build
```

## 3. Run on Device/Emulator

### Option A: Command Line
```bash
./gradlew installDebug
```

### Option B: Android Studio
1. Click **Build** → **Make Project**
2. Click **Run** → **Run 'app'**
3. Select your device/emulator

## 4. Test the App

### First Launch Checklist:
- ✅ App opens without "License expired" error
- ✅ No red error messages in logcat
- ✅ Main screen shows buttons (Enroll, Identify, etc.)

### Enrollment Test:
1. Click **"Enroll Employee"** button
2. Select a photo with a clear face
3. Enter Employee ID and Name
4. Click **"Enroll"**
5. Should see "Employee enrolled successfully!" toast

### Identification Test:
1. Click **"Identify"** button
2. Camera should open with live face detection
3. Point camera at enrolled employee
4. Green rectangle should appear around face
5. Should auto-identify and mark attendance

### Settings Test:
1. Click **"Settings"**
2. Adjust **"Identify Threshold"** slider
   - Lower: Easier to match
   - Higher: Stricter matching
3. Test with different values

## 5. Verify Build Success

```bash
# Check if APK was created
ls -la app/build/outputs/apk/debug/

# Install manually
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Common Issues & Solutions

### Issue: "Cannot find symbol FaceBox"
**Solution**: Run `./gradlew clean` then `./gradlew build`

### Issue: "No face detected on camera"
**Solution**: 
- Ensure good lighting
- Face must be centered and clear
- Closer to camera = better detection

### Issue: "Build fails with dependency error"
**Solution**: 
```bash
./gradlew clean build --refresh-dependencies
```

### Issue: App crashes when clicking "Identify"
**Solution**: Check logcat for detailed error:
```bash
adb logcat | grep -i "exception\|error"
```

## Performance Tips

1. **Faster Detection**: Use front camera (default)
2. **Better Accuracy**: Enroll in good lighting
3. **More Accurate**: Enroll at different angles
4. **Smoother Performance**: Close other apps

## Files Changed Summary

```
app/build.gradle                          ✏️ Updated ML Kit dependency
settings.gradle                           ✏️ Removed libfacesdk
app/src/main/java/...
  ├─ FaceRecognitionManager.kt           ✨ NEW - ML Kit wrapper
  ├─ ImageUtils.kt                       ✨ NEW - Image utilities
  ├─ MainActivity.kt                     ✏️ Updated for ML Kit
  ├─ CameraActivity.java                 ✏️ Complete rewrite
  ├─ CameraActivityKt.kt                 ✏️ Updated for ML Kit
  ├─ FaceView.java                       ✏️ Updated for ML Kit
  └─ Utils.java                          ✏️ Added cropFaceML()
```

## Deployment Checklist

Before releasing to production:

- [ ] APK builds without errors
- [ ] App runs on test device/emulator
- [ ] Enrollment works smoothly
- [ ] Identification accuracy acceptable
- [ ] Settings adjustments work
- [ ] No license errors on startup
- [ ] Attendance records save correctly
- [ ] No crashes in normal usage

## Getting Help

### Build Issues
```bash
# Get detailed error info
./gradlew build --stacktrace --debug
```

### Runtime Issues
```bash
# Check app logs in real-time
adb logcat -v short | grep "facerecognition\|ML"
```

### Test Different Thresholds

Edit `SettingsActivity.kt`:
```kotlin
companion object {
    const val DEFAULT_IDENTIFY_THRESHOLD = "0.8"  // Change to test (0.7, 0.85, etc.)
}
```

## Production Deployment

When ready to release:

```bash
# Build release version
./gradlew assembleRelease

# Find APK
ls app/build/outputs/apk/release/app-release.apk

# Sign and upload to Play Store or distribute as needed
```

## Next Steps

1. ✅ Build and test the app
2. ✅ Adjust settings for your environment
3. ✅ Enroll employee database
4. ✅ Deploy to production
5. ✅ Monitor performance

---

**Need help?** Check `MLKIT_MIGRATION.md` for detailed technical information.

**Questions?** Review the code comments in `FaceRecognitionManager.kt` for implementation details.
