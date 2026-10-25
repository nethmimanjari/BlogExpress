package uk.ac.wlv.blogexpress;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Animated splash screen: the logo pops in, the app name and tagline fade up,
 * sample posts are prepared in the background, then the home screen opens.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long MIN_SPLASH_MS = 1800;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean launched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiUtils.enableEdgeToEdgeDark(this);
        setContentView(R.layout.activity_splash);

        View logo = findViewById(R.id.logoBox);
        View name = findViewById(R.id.txtAppName);
        View tagline = findViewById(R.id.txtTagline);
        View loading = findViewById(R.id.loadingGroup);

        // Logo "pop" animation
        logo.setScaleX(0.5f);
        logo.setScaleY(0.5f);
        logo.setAlpha(0f);
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(700)
                .setInterpolator(new OvershootInterpolator(1.8f))
                .start();

        fadeUp(name, 300);
        fadeUp(tagline, 500);
        fadeUp(loading, 750);

        // Prepare sample posts while the animation plays
        final long start = SystemClock.uptimeMillis();
        final Context appContext = getApplicationContext();
        new Thread(() -> {
            SampleData.seedIfNeeded(appContext);
            long elapsed = SystemClock.uptimeMillis() - start;
            handler.postDelayed(this::openHome, Math.max(0, MIN_SPLASH_MS - elapsed));
        }).start();
    }

    private void fadeUp(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(40f);
        view.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay)
                .setDuration(600)
                .start();
    }

    @SuppressWarnings("deprecation")
    private void openHome() {
        if (launched || isFinishing()) return;
        launched = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}