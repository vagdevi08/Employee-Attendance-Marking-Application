package com.kbyai.facerecognition;


import static androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import husaynhakeem.io.facedetector.FaceBounds;
import husaynhakeem.io.facedetector.FaceBoundsOverlay;
import husaynhakeem.io.facedetector.FaceDetector;
import husaynhakeem.io.facedetector.Frame;
import husaynhakeem.io.facedetector.LensFacing;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    static String TAG = CameraActivity.class.getSimpleName();
    static int PREVIEW_WIDTH = 720;
    static int PREVIEW_HEIGHT = 1280;

    private ExecutorService cameraExecutorService;
    private PreviewView viewFinder;
    private Preview preview        = null;
    private ImageAnalysis imageAnalyzer  = null;
    private Camera camera         = null;
    private CameraSelector        cameraSelector = null;
    private ProcessCameraProvider cameraProvider = null;

    private FaceBoundsOverlay faceBoundsOverlay;
    private FaceDetector faceDetector;

    private volatile List<FaceBounds> latestFaceBounds = java.util.Collections.emptyList();

    private Context context;

    private Boolean recognized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        context = this;

        viewFinder = findViewById(R.id.preview);
        faceBoundsOverlay = findViewById(R.id.faceBoundsOverlay);
        faceDetector = new FaceDetector(faceBoundsOverlay);
        faceDetector.setonFaceDetectionFailureListener(new FaceDetector.OnFaceDetectionResultListener() {
            @Override
            public void onSuccess(List<FaceBounds> faceBounds) {
                latestFaceBounds = faceBounds;
            }

            @Override
            public void onFailure(Exception exception) {
                // Silently fail, will use ML Kit fallback
            }
        });
        cameraExecutorService = Executors.newFixedThreadPool(1);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        } else {
            viewFinder.post(() ->
            {
                setUpCamera();
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        recognized = false;
    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == 1) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {

                viewFinder.post(() ->
                {
                    setUpCamera();
                });
            }
        }
    }

    private void setUpCamera()
    {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(CameraActivity.this);
        cameraProviderFuture.addListener(() -> {

            // CameraProvider
            try {
                cameraProvider = cameraProviderFuture.get();
            } catch (ExecutionException e) {
            } catch (InterruptedException e) {
            }

            // Build and bind the camera use cases
            bindCameraUseCases();

        }, ContextCompat.getMainExecutor(CameraActivity.this));
    }

    @SuppressLint({"RestrictedApi", "UnsafeExperimentalUsageError"})
    private void bindCameraUseCases()
    {
        int rotation = viewFinder.getDisplay().getRotation();

        cameraSelector = new CameraSelector.Builder().requireLensFacing(SettingsActivity.getCameraLens(this)).build();

        preview = new Preview.Builder()
                .setTargetResolution(new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT))
                .setTargetRotation(rotation)
                .build();

        imageAnalyzer = new ImageAnalysis.Builder()
                .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT))
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                .setTargetRotation(rotation)
                .build();

        imageAnalyzer.setAnalyzer(cameraExecutorService, new FaceAnalyzer());

        cameraProvider.unbindAll();

        try {
            camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer);

            preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
        } catch (Exception exc) {
        }
    }

    class FaceAnalyzer implements ImageAnalysis.Analyzer
    {
        @SuppressLint("UnsafeExperimentalUsageError")
        @Override
        public void analyze(@NonNull ImageProxy imageProxy)
        {
            analyzeImage(imageProxy);
        }
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private void analyzeImage(ImageProxy imageProxy)
    {
        if(recognized == true) {
            imageProxy.close();
            return;
        }

        try
        {
            Image image = imageProxy.getImage();
            if (image == null) {
                imageProxy.close();
                return;
            }

                // Prepare NV21 data for the detector overlay
                byte[] nv21 = toNv21(image);

                // Push frame to android-face-detector for drawing bounds
                faceDetector.process(
                    new Frame(
                        nv21,
                        imageProxy.getImageInfo().getRotationDegrees(),
                        new Size(image.getWidth(), image.getHeight()),
                        image.getFormat(),
                        SettingsActivity.getCameraLens(this) == CameraSelector.LENS_FACING_BACK ?
                            LensFacing.BACK : LensFacing.FRONT
                    )
                );

                // Convert NV21 frame to Bitmap for recognition flow
                final Bitmap bitmap = nv21ToBitmap(nv21, image.getWidth(), image.getHeight());

            // Prefer android-face-detector results (fallback to ML Kit)
            final FaceRecognitionManager.DetectedFace detectedFace;

            if (latestFaceBounds != null && latestFaceBounds.size() > 0) {
                detectedFace = convertFaceBoundsToDetectedFace(latestFaceBounds.get(0));
            } else {
                List<FaceRecognitionManager.DetectedFace> detectedFaces = FaceRecognitionManager.INSTANCE.detectFaces(bitmap);
                if (detectedFaces.size() > 0) {
                    detectedFace = detectedFaces.get(0);
                } else {
                    detectedFace = null;
                }
            }

            if(detectedFace != null) {
                
                // Validate face size (must be >10% of image)
                float faceWidth = detectedFace.getRight() - detectedFace.getLeft();
                float faceHeight = detectedFace.getBottom() - detectedFace.getTop();
                float faceArea = faceWidth * faceHeight;
                float imageArea = bitmap.getWidth() * bitmap.getHeight();
                float faceSizeRatio = faceArea / imageArea;
                
                // Face should occupy at least 10% of the image
                if (faceSizeRatio > 0.1f) {
                    // Extract embeddings for the detected face
                    float[] embeddings = FaceRecognitionManager.INSTANCE.extractEmbeddings(bitmap, detectedFace);

                    float maxSimilarity = 0;
                    Person identifiedPerson = null;
                    
                    for(Person person : DBManager.personList) {
                        // Convert stored byte array back to float array
                        float[] storedEmbeddings = convertByteArrayToFloatArray(person.templates);
                        float similarity = FaceRecognitionManager.INSTANCE.calculateSimilarity(embeddings, storedEmbeddings);
                        
                        if(similarity > maxSimilarity) {
                            maxSimilarity = similarity;
                            identifiedPerson = person;
                        }
                    }

                    if(maxSimilarity > SettingsActivity.getIdentifyThreshold(this)) {
                        recognized = true;
                        final Person foundPerson = identifiedPerson;
                        final float similarity = maxSimilarity;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                markAttendanceAndFinish(foundPerson, similarity);
                            }
                        });
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            imageProxy.close();
        }
    }

    // Convert camera YUV image into NV21 byte array for downstream consumers
    private byte[] toNv21(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    // Convert NV21 byte array to Bitmap for recognition
    private Bitmap nv21ToBitmap(byte[] nv21, int width, int height) {
        android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 100, out);
        byte[] imageBytes = out.toByteArray();
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private float[] convertByteArrayToFloatArray(byte[] byteArray) {
        if (byteArray == null || byteArray.length == 0) {
            return new float[128];
        }
        
        // Create float array from bytes
        float[] floatArray = new float[Math.min(byteArray.length / 4, 128)];
        for (int i = 0; i < floatArray.length; i++) {
            int index = i * 4;
            if (index + 3 < byteArray.length) {
                int intBits = ((byteArray[index] & 0xFF) << 24) |
                              ((byteArray[index + 1] & 0xFF) << 16) |
                              ((byteArray[index + 2] & 0xFF) << 8) |
                              (byteArray[index + 3] & 0xFF);
                floatArray[i] = Float.intBitsToFloat(intBits);
            }
        }
        
        // Pad with zeros if necessary
        if (floatArray.length < 128) {
            float[] padded = new float[128];
            System.arraycopy(floatArray, 0, padded, 0, floatArray.length);
            return padded;
        }
        
        return floatArray;
    }

    private FaceRecognitionManager.DetectedFace convertFaceBoundsToDetectedFace(FaceBounds faceBounds) {
        android.graphics.Rect rect = new android.graphics.Rect(
                (int) faceBounds.getBox().left,
                (int) faceBounds.getBox().top,
                (int) faceBounds.getBox().right,
                (int) faceBounds.getBox().bottom
        );

        return new FaceRecognitionManager.DetectedFace(
                rect,
                0f,
                0f,
                0f,
                (float) rect.left,
                (float) rect.top,
                (float) rect.right,
                (float) rect.bottom,
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList(),
                0.9f,
                0f,
                0f,
                0f
        );
    }

    private void markAttendanceAndFinish(Person person, float similarity) {
        long attendanceTime = System.currentTimeMillis();
        String employeeId = person.employeeId != null ? person.employeeId : "";
        String employeeName = person.name;

        if (!employeeId.isEmpty()) {
            String attendanceType = AttendanceHelper.INSTANCE.determineAttendanceType(this, employeeId, attendanceTime);

            if (!attendanceType.equals("NONE")) {
                DBManager dbManager = new DBManager(this);
                dbManager.insertAttendance(employeeId, employeeName, attendanceTime, attendanceType);

                String typeText = attendanceType.equals("CHECK_IN") ? "Check-in" : "Check-out";
                android.widget.Toast.makeText(
                        this,
                        typeText + " successful for " + employeeName + " (Similarity: " + String.format("%.2f", similarity) + ")",
                        android.widget.Toast.LENGTH_LONG
                ).show();
            } else {
                String message = AttendanceHelper.INSTANCE.getAttendanceTypeMessage(this, employeeId, attendanceTime);
                android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
            }
        } else {
            // Fallback for employees without ID
            DBManager dbManager = new DBManager(this);
            dbManager.insertAttendance("", employeeName, attendanceTime, "CHECK_IN");
            android.widget.Toast.makeText(
                    this,
                    "Attendance marked for " + employeeName + " (Similarity: " + String.format("%.2f", similarity) + ")",
                    android.widget.Toast.LENGTH_LONG
            ).show();
        }

        // Close camera activity after short delay
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 2000);
    }
}