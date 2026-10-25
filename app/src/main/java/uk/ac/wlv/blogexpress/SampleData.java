package uk.ac.wlv.blogexpress;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Creates demo posts so the app looks complete on first launch.
 * Cover images are drawn in code (gradient sky + hills), so no image
 * files need to be bundled with the app.
 */
public final class SampleData {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private static final String[] TITLES = {
            "My Journey Learning React in 2026",
            "Morning Routines That Changed My Life",
            "Three Days in the Atlas Mountains",
            "Notes on Clean Architecture"
    };

    private static final String[] CONTENTS = {
            "When I first started learning React, I was overwhelmed by the ecosystem. JSX looked strange, "
                    + "state management seemed complex, and the component model took time to click. Six months in, "
                    + "it all became elegant and natural. The key was building small projects and reading the "
                    + "source of libraries I admired.",
            "I used to hit snooze five times every morning. Now I wake up at 6 AM naturally, meditate for "
                    + "10 minutes, journal for 15, and feel genuinely energized before the day begins. Small habits "
                    + "compound into remarkable results over weeks and months.",
            "The air was thin and cold, the sky impossibly clear. We rented a small riad in the valley and "
                    + "each morning hiked higher, finding ancient Berber villages perched on ridge lines overlooking "
                    + "the Sahara. On the second night we ate tagine by firelight.",
            "Clean Architecture taught me that dependencies should point inward. Business logic should never "
                    + "depend on frameworks, databases, or UI. This principle forces clarity about separation of "
                    + "concerns and the true shape of your domain model."
    };

    /** {top colour, bottom colour, hill colour} for each cover image. */
    private static final int[][] PALETTES = {
            {0xFF6750A4, 0xFF9FA8DA, 0xFF2E2A5A},
            {0xFFFFB74D, 0xFFFF8A65, 0xFF6D3B2A},
            {0xFF5C6BC0, 0xFFF8BBD0, 0xFF263238},
            {0xFF26A69A, 0xFFB2DFDB, 0xFF1B4D45}
    };

    private static final int[] DAYS_AGO = {1, 3, 6, 9};
    private static final boolean[] IS_DRAFT = {false, false, false, true};

    private SampleData() { }

    /** Adds sample posts only on the very first launch, if the database is empty. */
    public static void seedIfNeeded(Context context) {
        SettingsManager settings = new SettingsManager(context);
        if (settings.isSeeded()) return;
        if (DatabaseHelper.getInstance(context).getCount() == 0) {
            addSamplePosts(context);
        }
        settings.setSeeded(true);
    }

    /** Adds the sample posts (used by the first launch and by Settings). */
    public static int addSamplePosts(Context context) {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        long now = System.currentTimeMillis();

        for (int i = 0; i < TITLES.length; i++) {
            Message m = new Message();
            m.setTitle(TITLES[i]);
            m.setContent(CONTENTS[i]);
            m.setDraft(IS_DRAFT[i]);
            long time = now - DAYS_AGO[i] * DAY - i * 3_600_000L;
            m.setCreatedAt(time);
            m.setUpdatedAt(time);
            try {
                m.setImagePath(createCover(context, PALETTES[i], i));
            } catch (IOException e) {
                m.setImagePath(null); // post is still added without a photo
            }
            db.insertMessage(m);
        }
        return TITLES.length;
    }

    /** Draws a colourful landscape-style cover image and saves it as a JPEG. */
    private static String createCover(Context context, int[] colors, int seed) throws IOException {
        int w = 1200;
        int h = 720;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Sky gradient
        paint.setShader(new LinearGradient(0, 0, w, h, colors[0], colors[1], Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // Soft glowing circles
        Random random = new Random(seed * 31L + 7);
        paint.setColor(Color.WHITE);
        for (int i = 0; i < 8; i++) {
            paint.setAlpha(25 + random.nextInt(45));
            canvas.drawCircle(random.nextInt(w), random.nextInt(h / 2), 40 + random.nextInt(160), paint);
        }

        // Sun
        paint.setAlpha(210);
        canvas.drawCircle(w * 0.78f, h * 0.30f, 70, paint);

        // Back hills
        Path back = new Path();
        back.moveTo(0, h * 0.72f);
        back.quadTo(w * 0.25f, h * 0.42f, w * 0.5f, h * 0.68f);
        back.quadTo(w * 0.75f, h * 0.48f, w, h * 0.64f);
        back.lineTo(w, h);
        back.lineTo(0, h);
        back.close();
        paint.setColor(colors[2]);
        paint.setAlpha(140);
        canvas.drawPath(back, paint);

        // Front hills
        Path front = new Path();
        front.moveTo(0, h * 0.86f);
        front.quadTo(w * 0.3f, h * 0.66f, w * 0.6f, h * 0.84f);
        front.quadTo(w * 0.85f, h * 0.74f, w, h * 0.82f);
        front.lineTo(w, h);
        front.lineTo(0, h);
        front.close();
        paint.setAlpha(235);
        canvas.drawPath(front, paint);

        File dir = new File(context.getFilesDir(), "images");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create image folder");
        File out = new File(dir, "SAMPLE_" + System.nanoTime() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, fos);
        }
        bitmap.recycle();
        return out.getAbsolutePath();
    }
}