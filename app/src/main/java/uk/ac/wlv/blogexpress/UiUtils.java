package uk.ac.wlv.blogexpress;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Shared UI helpers: edge-to-edge display, date/size formatting,
 * progress dialogs and the Android Share intent.
 */
public final class UiUtils {

    private UiUtils() { }

    /**
     * Draws the app behind transparent system bars. Icon colours follow the
     * current light/dark theme. On Android 5.x a dark scrim keeps icons visible.
     */
    public static void enableEdgeToEdge(ComponentActivity activity) {
        int nightMask = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (nightMask == Configuration.UI_MODE_NIGHT_YES) {
            enableEdgeToEdgeDark(activity);
        } else {
            int oldDeviceScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B);
            EdgeToEdge.enable(activity,
                    SystemBarStyle.light(Color.TRANSPARENT, oldDeviceScrim),
                    SystemBarStyle.light(Color.TRANSPARENT, oldDeviceScrim));
        }
    }

    /** Edge-to-edge with light (white) system bar icons, for dark screens. */
    public static void enableEdgeToEdgeDark(ComponentActivity activity) {
        EdgeToEdge.enable(activity,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT));
    }

    /** Adds padding so content is not hidden behind the status bar, nav bar or keyboard. */
    public static void applySystemBarInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    public static String formatDate(long millis) {
        return new SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(new Date(millis));
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.0f KB", bytes / 1024f);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }

    /** Shows a non-cancellable dialog with a spinner and message. */
    public static AlertDialog showProgress(Context context, String message) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null);
        ((TextView) view.findViewById(R.id.txtProgress)).setText(message);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(false)
                .create();
        dialog.show();
        return dialog;
    }

    public static void updateProgress(AlertDialog dialog, String message) {
        TextView txt = dialog.findViewById(R.id.txtProgress);
        if (txt != null) txt.setText(message);
    }

    /** Shares the post text + photo using the standard Android Share sheet (e.g. Gmail). */
    public static void shareMessage(Activity activity, Message message) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_SUBJECT, message.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, message.getTitle() + "\n\n" + message.getContent());

        if (message.hasImage()) {
            // FileProvider gives the email app temporary read access to our private photo
            Uri uri = ImageUtils.getUriForFile(activity, new File(message.getImagePath()));
            intent.setType("image/jpeg");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(ClipData.newRawUri("", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setType("text/plain");
        }
        activity.startActivity(Intent.createChooser(intent, "Share post via"));
    }
}