package uk.ac.wlv.blogexpress;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility methods for saving, loading and sharing post images.
 * Images are copied into the app's private storage so they stay available
 * even if the original photo is deleted from the gallery.
 * Uses androidx ExifInterface so rotation works on Android 5.0 (API 21) and above.
 */
public final class ImageUtils {

    private static final int MAX_IMAGE_SIZE = 1280; // max width/height in pixels

    private ImageUtils() { }

    /** Creates an empty temporary file for the camera app to write the photo into. */
    public static File createCameraFile(Context context) throws IOException {
        File dir = new File(context.getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create camera folder");
        return File.createTempFile("CAM_", ".jpg", dir);
    }

    /** Returns a secure content:// URI for a private file (used for camera + sharing). */
    public static Uri getUriForFile(Context context, File file) {
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
    }

    /**
     * Copies an image (from gallery or camera) into private storage, resized and
     * correctly rotated. Returns the absolute path of the saved JPEG file.
     */
    public static String saveImageFromUri(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();

        // 1. Read only the image dimensions (uses no memory)
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open image");
            BitmapFactory.decodeStream(in, null, bounds);
        }

        // 2. Decode a smaller version to avoid OutOfMemory errors
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_SIZE);
        Bitmap bitmap;
        try (InputStream in = resolver.openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) throw new IOException("Unsupported image format");

        // 3. Fix camera rotation and resize to the final size
        bitmap = rotateIfRequired(resolver, uri, bitmap);
        bitmap = scaleDown(bitmap, MAX_IMAGE_SIZE);

        // 4. Save as JPEG into /files/images (private to this app)
        File dir = new File(context.getFilesDir(), "images");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create image folder");
        File out = new File(dir, "IMG_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
        }
        return out.getAbsolutePath();
    }

    /** Loads a saved image at a reduced size (for thumbnails and previews). */
    public static Bitmap decodeSampled(String path, int reqSize) {
        if (path == null) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, reqSize);
        return BitmapFactory.decodeFile(path, options);
    }

    /** Deletes an image file if it exists. */
    public static void deleteImage(String path) {
        if (path == null) return;
        File file = new File(path);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /** Total bytes used by saved photos plus the SQLite database file. */
    public static long getStorageUsed(Context context) {
        long total = 0;
        File[] files = new File(context.getFilesDir(), "images").listFiles();
        if (files != null) {
            for (File f : files) total += f.length();
        }
        File db = context.getDatabasePath("blogexpress.db");
        if (db.exists()) total += db.length();
        return total;
    }

    private static int calculateSampleSize(int width, int height, int target) {
        int sample = 1;
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap scaleDown(Bitmap bitmap, int max) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= max && h <= max) return bitmap;
        float ratio = Math.min((float) max / w, (float) max / h);
        return Bitmap.createScaledBitmap(bitmap, Math.round(w * ratio), Math.round(h * ratio), true);
    }

    /** Reads the EXIF orientation of the photo and rotates the bitmap upright. */
    private static Bitmap rotateIfRequired(ContentResolver resolver, Uri uri, Bitmap bitmap) {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return bitmap;
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            int degrees;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: degrees = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: degrees = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
                default: return bitmap;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (IOException e) {
            return bitmap;
        }
    }
}