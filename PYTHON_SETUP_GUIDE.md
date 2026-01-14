# Quick Setup Guide - Python Face Recognition

## Step 1: Install Python Dependencies

Open PowerShell and navigate to the project folder:

```powershell
cd "c:\Users\sruja\OneDrive\Documents\GitHub\Employee-Attendance-Marking-Application"

# Install Python packages
pip install flask face-recognition opencv-python numpy Pillow
```

**Note:** If `face-recognition` installation fails, you may need:
- Install Visual Studio Build Tools: https://visualstudio.microsoft.com/downloads/
- Or use: `pip install face-recognition-models dlib`

## Step 2: Start Python Service

```powershell
python face_recognition_service.py
```

You should see:
```
Starting Face Recognition Service...
Enrolled faces: 0
 * Running on http://0.0.0.0:5000
```

## Step 3: Configure Android App

1. **Build the Android app** - The Gradle sync will download OkHttp dependency
2. **Open Settings** in the app
3. **Find "Python Face Recognition Service"** section
4. **Set Service URL**:
   - For Android Emulator: `http://10.0.2.2:5000` (default)
   - For Real Device: `http://YOUR_COMPUTER_IP:5000` (e.g., `http://192.168.1.100:5000`)
5. **Click "Test Connection"** to verify it works

## Step 4: Test Face Recognition

1. **Enroll a face**: Click "Enroll New Employee" → Select photo → Enter details
   - Python service will detect and encode the face
   - More accurate than ML Kit
   
2. **Identify**: Click "Identify" → Show your face to camera
   - Sends frames to Python service
   - Better recognition accuracy
   - Supports various lighting conditions

## Finding Your Computer's IP Address

**Windows:**
```powershell
ipconfig
```
Look for "IPv4 Address" under your active network adapter

**Testing from Device:**
- Make sure your phone and computer are on the same WiFi network
- Disable firewall temporarily or allow port 5000

## Advantages of Python Service

✓ **Better Accuracy** - Uses dlib's state-of-the-art face recognition  
✓ **Industry Standard** - Face_recognition library is proven and widely used  
✓ **Adjustable Threshold** - Fine-tune recognition sensitivity  
✓ **Works Offline** - Once enrolled, data is cached locally  
✓ **No License Required** - Free and open source  

## Troubleshooting

**Connection Refused:**
- Ensure Python service is running
- Check firewall settings
- Verify IP address is correct

**Face Not Detected:**
- Ensure good lighting
- Face should be clearly visible
- Try adjusting "Identify Threshold" in settings

**Slow Recognition:**
- Python service processes frames sequentially
- This is normal for better accuracy
- Consider running service on a more powerful machine
