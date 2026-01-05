package com.kbyai.facerecognition;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Size;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.List;

public class FaceView extends View {

    private Context context;
    private Paint realPaint;
    private Paint spoofPaint;

    private Size frameSize;

    private List<FaceRecognitionManager.DetectedFace> detectedFaces;

    public FaceView(Context context) {
        this(context, null);

        this.context = context;
        init();
    }

    public FaceView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        init();
    }

    public void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        realPaint = new Paint();
        realPaint.setStyle(Paint.Style.STROKE);
        realPaint.setStrokeWidth(3);
        realPaint.setColor(Color.GREEN);
        realPaint.setAntiAlias(true);
        realPaint.setTextSize(50);

        spoofPaint = new Paint();
        spoofPaint.setStyle(Paint.Style.STROKE);
        spoofPaint.setStrokeWidth(3);
        spoofPaint.setColor(Color.RED);
        spoofPaint.setAntiAlias(true);
        spoofPaint.setTextSize(50);
    }

    public void setFrameSize(Size frameSize)
    {
        this.frameSize = frameSize;
    }

    public void setDetectedFaces(List<FaceRecognitionManager.DetectedFace> detectedFaces)
    {
        this.detectedFaces = detectedFaces;
        invalidate();
    }

    /**
     * Backward compatibility method for FaceBox
     */
    public void setFaceBoxes(List<?> faceBoxes)
    {
        // This method is kept for backward compatibility but won't do anything
        // Use setDetectedFaces instead
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (frameSize != null && detectedFaces != null && detectedFaces.size() > 0) {
            float x_scale = this.frameSize.getWidth() / (float)canvas.getWidth();
            float y_scale = this.frameSize.getHeight() / (float)canvas.getHeight();

            for (int i = 0; i < detectedFaces.size(); i++) {
                FaceRecognitionManager.DetectedFace face = detectedFaces.get(i);

                int left = (int)(face.getLeft() / x_scale);
                int top = (int)(face.getTop() / y_scale);
                int right = (int)(face.getRight() / x_scale);
                int bottom = (int)(face.getBottom() / y_scale);

                // ML Kit doesn't provide liveness detection, so we'll just show all faces as REAL
                realPaint.setStrokeWidth(3);
                realPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                canvas.drawText("FACE DETECTED", left + 10, top - 30, realPaint);

                realPaint.setStyle(Paint.Style.STROKE);
                realPaint.setStrokeWidth(5);
                canvas.drawRect(new Rect(left, top, right, bottom), realPaint);
            }
        }
    }
}

