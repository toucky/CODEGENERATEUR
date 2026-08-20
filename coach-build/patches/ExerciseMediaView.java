package com.appmada.coachmuscu;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Exercise visual: realistic offline frame animation when available,
 * with the original lightweight vector animation as fallback.
 */
public class ExerciseMediaView extends FrameLayout {
    private final ImageView photo;
    private final ExerciseAnimationView fallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int[] frames = null;
    private int frameIndex = 0;
    private boolean paused = false;
    private long frameDelayMs = 650;

    private final Runnable frameLoop = new Runnable() {
        @Override public void run() {
            if (!paused && frames != null && frames.length > 0) {
                frameIndex = (frameIndex + 1) % frames.length;
                photo.setImageResource(frames[frameIndex]);
            }
            handler.postDelayed(this, frameDelayMs);
        }
    };

    public ExerciseMediaView(Context c) {
        super(c);
        setBackgroundColor(Ui.SURFACE);
        fallback = new ExerciseAnimationView(c);
        addView(fallback, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        photo = new ImageView(c);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setAdjustViewBounds(true);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        p.gravity = Gravity.CENTER;
        addView(photo, p);
        photo.setVisibility(GONE);
        handler.postDelayed(frameLoop, frameDelayMs);
    }

    public void setExercise(Exercise e) {
        if (e != null && "march".equals(e.id)) {
            frames = new int[]{
                    R.drawable.march_real_left,
                    R.drawable.march_real_center,
                    R.drawable.march_real_right,
                    R.drawable.march_real_center
            };
            frameDelayMs = 360;
            showRealFrames();
        } else if (e != null && "pushup".equals(e.id)) {
            frames = new int[]{R.drawable.pushup_real_top, R.drawable.pushup_real_bottom, R.drawable.pushup_real_top};
            frameDelayMs = 650;
            showRealFrames();
        } else {
            frames = null;
            photo.setVisibility(GONE);
            fallback.setVisibility(VISIBLE);
            fallback.setExercise(e);
        }
    }

    private void showRealFrames() {
        frameIndex = 0;
        photo.setImageResource(frames[0]);
        photo.setVisibility(VISIBLE);
        fallback.setVisibility(GONE);
    }

    public void setPaused(boolean v) {
        paused = v;
        fallback.setPaused(v);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(frameLoop);
        super.onDetachedFromWindow();
    }
}
