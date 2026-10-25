package uk.ac.wlv.blogexpress;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lets the user connect their personal WordPress.com account.
 * The password is sent once over HTTPS to obtain an OAuth access token;
 * only the token is stored (never the password).
 */
public class AccountActivity extends AppCompatActivity {

    private SettingsManager settings;
    private TextInputEditText edtSite, edtClientId, edtClientSecret, edtUsername, edtPassword;
    private TextView txtStatus;
    private MaterialButton btnDisconnect;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiUtils.enableEdgeToEdge(this);
        setContentView(R.layout.activity_account);
        UiUtils.applySystemBarInsets(findViewById(R.id.main));

        settings = new SettingsManager(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        edtSite = findViewById(R.id.edtSite);
        edtClientId = findViewById(R.id.edtClientId);
        edtClientSecret = findViewById(R.id.edtClientSecret);
        edtUsername = findViewById(R.id.edtUsername);
        edtPassword = findViewById(R.id.edtPassword);
        txtStatus = findViewById(R.id.txtStatus);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        MaterialButton btnConnect = findViewById(R.id.btnConnect);

        toolbar.setNavigationOnClickListener(v -> finish());
        edtSite.setText(settings.getSite());
        edtUsername.setText(settings.getUsername());
        updateStatus();

        btnConnect.setOnClickListener(v -> connect());
        btnDisconnect.setOnClickListener(v -> {
            settings.clearWordPress();
            updateStatus();
            Toast.makeText(this, "Account disconnected", Toast.LENGTH_SHORT).show();
        });
    }

    private static String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private void connect() {
        // Accept "https://myblog.wordpress.com/" as well as "myblog.wordpress.com"
        final String site = text(edtSite)
                .replace("https://", "").replace("http://", "").replaceAll("/+$", "");
        final String clientId = text(edtClientId);
        final String clientSecret = text(edtClientSecret);
        final String username = text(edtUsername);
        final String password = edtPassword.getText() == null ? "" : edtPassword.getText().toString();

        if (site.isEmpty() || clientId.isEmpty() || clientSecret.isEmpty()
                || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog progress = UiUtils.showProgress(this, "Connecting to WordPress…");
        executor.execute(() -> {
            try {
                String token = WordPressUploader.requestToken(clientId, clientSecret, username, password);
                mainHandler.post(() -> {
                    progress.dismiss();
                    settings.saveConnection(site, username, token);
                    edtPassword.setText("");      // forget the password immediately
                    edtClientSecret.setText("");
                    updateStatus();
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Connected 🎉")
                            .setMessage("You can now upload posts to " + site)
                            .setPositiveButton("OK", (d, w) -> finish())
                            .show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progress.dismiss();
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Connection failed")
                            .setMessage(e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private void updateStatus() {
        if (settings.isConnected()) {
            txtStatus.setText("✅ Connected as " + settings.getUsername() + "\n" + settings.getSite());
            btnDisconnect.setVisibility(View.VISIBLE);
        } else {
            txtStatus.setText("Not connected");
            btnDisconnect.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}