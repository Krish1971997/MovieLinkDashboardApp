package com.example.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Java replacement for the Compose Canvas gauge inside DashboardScreen.kt
 * (the "INDEX SPACE" circular capacity indicator):
 *
 *   drawCircle(track)  +  drawArc(sweepGradient, startAngle = -90f, sweepAngle = progress * 360f)
 *
 * The animated float progress is driven by the Activity through {@link #setProgress(float)}
 * (an equivalent of animateFloatAsState).
 */
public class CircleGaugeView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float progress = 0f;
    private float strokeWidth;

    public CircleGaugeView(Context context) {
        super(context);
        init();
    }

    public CircleGaugeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircleGaugeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokeWidth = dp(8f);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(Color.parseColor("#E2EAF8"));

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(strokeWidth);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Equivalent of the animated `progress` float (0f..1f). */
    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float pad = strokeWidth / 2f;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - pad;

        canvas.drawCircle(cx, cy, radius, trackPaint);

        // Sweep gradient of the Kotlin BubbleGaugeGradient:
        // listOf(Color(0xFF00FFCC), Color(0xFF2879FF), Color(0xFF7F00FF))
        int[] colors = new int[]{
                Color.parseColor("#00FFCC"),
                Color.parseColor("#2879FF"),
                Color.parseColor("#7F00FF"),
                Color.parseColor("#00FFCC")
        };
        arcPaint.setShader(new SweepGradient(cx, cy, colors, null));

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        // startAngle = -90f, sweepAngle = progress * 360f
        canvas.drawArc(arcBounds, -90f, progress * 360f, false, arcPaint);
        arcPaint.setShader(null);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
