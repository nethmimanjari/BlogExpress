package uk.ac.wlv.blogexpress;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;

/**
 * Settings: profile, dark mode, WordPress account, local storage,
 * about, privacy & security and sign out.
 */
public class SettingsActivity extends AppCompatActivity {

    private SettingsManager settings;
    private DatabaseHelper db;
    private TextView txtProfileName, txtProfileEmail, txtProfileStats;
    private TextView txtDarkSubtitle, txtWordPressStatus, txtStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiUtils.enableEdgeToEdge(this);
        setContentView(R.layout.activity_settings);
        UiUtils.applySystemBarInsets(findViewById(R.id.main));

        settings = new SettingsManager(this);
        db = DatabaseHelper.getInstance(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        txtProfileName = findViewById(R.id.txtProfileName);
        txtProfileEmail = findViewById(R.id.txtProfileEmail);
        txtProfileStats = findViewById(R.id.txtProfileStats);
        txtDarkSubtitle = findViewById(R.id.txtDarkSubtitle);
        txtWordPressStatus = findViewById(R.id.txtWordPressStatus);
        txtStorage = findViewById(R.id.txtStorage);
        TextView txtVersion = findViewById(R.id.txtVersion);
        MaterialSwitch switchDark = findViewById(R.id.switchDark);

        toolbar.setNavigationOnClickListener(v -> finish());
        txtVersion.setText("Version " + getVersionName());

        // Dark mode: set the switch BEFORE adding the listener
        switchDark.setChecked(settings.isDarkMode());
        switchDark.setOnCheckedChangeListener((button, checked) -> {
            settings.setDarkMode(checked);
            AppCompatDelegate.setDefaultNightMode(checked
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
        });
        findViewById(R.id.rowDarkMode).setOnClickListener(v -> switchDark.toggle());

        findViewById(R.id.cardProfile).setOnClickListener(v -> showProfileDialog());
        findViewById(R.id.rowWordPress).setOnClickListener(v ->
                startActivity(new Intent(this, AccountActivity.class)));
        findViewById(R.id.rowStorage).setOnClickListener(v -> showStorageDialog());
        findViewById(R.id.rowAbout).setOnClickListener(v -> showAbout());
        findViewById(R.id.rowPrivacy).setOnClickListener(v -> showPrivacy());
        findViewById(R.id.btnSignOut).setOnClickListener(v -> signOut());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** Updates every text on the screen from the latest data. */
    private void refresh() {
        long count = db.getCount();
        String posts = count + (count == 1 ? " post" : " posts");

        txtProfileName.setText(settings.getProfileName());
        String email = settings.getProfileEmail();
        txtProfileEmail.setText(email.isEmpty() ? "Tap to add your email" : email);
        Calendar since = Calendar.getInstance();
        since.setTimeInMillis(settings.getFirstRunTime());
        txtProfileStats.setText(posts + " · Member since " + since.get(Calendar.YEAR));

        txtDarkSubtitle.setText(settings.isDarkMode() ? "Currently dark" : "Currently light");
        txtWordPressStatus.setText(settings.isConnected()
                ? "Connected · " + settings.getSite()
                : "Not connected · Tap to sign in");
        txtStorage.setText(posts + " · " + UiUtils.formatSize(ImageUtils.getStorageUsed(this)) + " used");
    }

    private String getVersionName() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name == null ? "1.0" : name;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0";
        }
    }

    private static String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    // ---------------- Profile ----------------

    private void showProfileDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_profile, null);
        TextInputEditText edtName = view.findViewById(R.id.edtName);
        TextInputEditText edtEmail = view.findViewById(R.id.edtEmail);
        edtName.setText(settings.getProfileName());
        edtEmail.setText(settings.getProfileEmail());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit profile")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String name = text(edtName);
                    settings.saveProfile(name.isEmpty() ? "BlogExpress Writer" : name, text(edtEmail));
                    refresh();
                })
                .show();
    }

    // ---------------- Storage ----------------

    private void showStorageDialog() {
        long count = db.getCount();
        String size = UiUtils.formatSize(ImageUtils.getStorageUsed(this));
        new MaterialAlertDialogBuilder(this)
                .setTitle("Local Storage")
                .setMessage(count + " post(s) are stored in the on-device SQLite database.\n"
                        + "Photos and database together use " + size + ".\n\n"
                        + "Nothing is sent online until you choose to upload a post.")
                .setPositiveButton("OK", null)
                .setNegativeButton("Delete all posts", (d, w) -> confirmDeleteAll())
                .show();
    }

    private void confirmDeleteAll() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete all posts?")
                .setMessage("Every post and photo on this device will be permanently deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete all", (d, w) -> {
                    db.deleteAllMessages();
                    refresh();
                    Toast.makeText(this, "All posts deleted", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ---------------- About & privacy ----------------

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("About BlogExpress")
                .setMessage("BlogExpress " + getVersionName() + "\n\n"
                        + "Write blog posts offline, attach photos from your gallery or camera, "
                        + "share them by email and publish them to your WordPress.com blog.\n\n"
                        + "Built with Java in Android Studio for 6CS027 Secure Mobile Application Development.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showPrivacy() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Privacy & Security")
                .setMessage("• Posts are stored in a private SQLite database that other apps cannot read.\n\n"
                        + "• Photos are saved in private app storage and shared only through a secure, "
                        + "temporary FileProvider link.\n\n"
                        + "• Your WordPress password is used once to sign in and is never saved. "
                        + "Only an access token is kept.\n\n"
                        + "• All uploads use encrypted HTTPS connections.\n\n"
                        + "• Database queries are parameterised to block SQL injection.\n\n"
                        + "• App data is excluded from cloud backups.")
                .setPositiveButton("OK", null)
                .show();
    }

    // ---------------- Sign out ----------------

    private void signOut() {
        if (!settings.isConnected()) {
            Toast.makeText(this, "You are not signed in to WordPress", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Sign out of WordPress?")
                .setMessage("Your posts stay safely on this device. You can sign in again any time.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sign out", (d, w) -> {
                    settings.clearWordPress();
                    refresh();
                    Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}