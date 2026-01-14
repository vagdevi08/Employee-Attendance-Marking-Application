# Python Face Recognition Service

This service provides better face recognition accuracy using Python's `face_recognition` library (based on dlib).

## Setup

### 1. Install Python Dependencies

```bash
pip install -r requirements.txt
```

**Note:** Installing `face_recognition` requires:
- Windows: Install CMake and Visual Studio Build Tools
- Linux: `sudo apt-get install cmake build-essential`
- Mac: `brew install cmake`

### 2. Start the Service

```bash
python face_recognition_service.py
```

The service will start on `http://localhost:5000`

### 3. Configure Android App

Update the Android app to point to your Python service:

- If testing on emulator: Use `http://10.0.2.2:5000`
- If testing on real device: Use your computer's IP address `http://YOUR_IP:5000`

## API Endpoints

### Health Check
```
GET /health
```

### Enroll a Face
```
POST /enroll
Content-Type: application/json

{
  "employee_id": "EMP001",
  "name": "John Doe",
  "image": "base64_encoded_image"
}
```

### Identify a Face
```
POST /identify
Content-Type: application/json

{
  "image": "base64_encoded_image",
  "threshold": 0.6
}
```

### List Enrolled Faces
```
GET /list
```

### Delete a Face
```
DELETE /delete/EMP001
```

### Clear All Faces
```
POST /clear
```

## Advantages

- **Better Accuracy**: Uses dlib's state-of-the-art face recognition
- **Industry Standard**: Based on proven technology
- **Robust**: Handles various lighting conditions and angles
- **Adjustable Threshold**: Fine-tune recognition sensitivity

## Integration with Android App

The Android app has been modified to communicate with this Python service instead of using ML Kit locally.
