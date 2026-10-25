package uk.ac.wlv.blogexpress;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collections;

/**
 * Shows one post in full, with Edit, Delete, Share (Android Share intent)
 * and Upload (WordPress.com) actions.
 */
public class ViewMessageActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "message_id";

    private DatabaseHelper db;
    private long messageId;
    private Message message;

    private ImageView imgMessage;
    private View badgeRow;
    private TextView txtTitle, txtDate, txtContent, txtUploaded, txtDraft, txtLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiUtils.enableEdgeToEdge(this);
        setContentView(R.layout.activity_view_message);
        UiUtils.applySystemBarInsets(findViewById(R.id.main));

        db = DatabaseHelper.getInstance(this);
        messageId = getIntent().getLongExtra(EXTRA_ID, -1);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        imgMessage = findViewById(R.id.imgMessage);
        badgeRow = findViewById(R.id.badgeRow);
        txtTitle = findViewById(R.id.txtTitle);
        txtDate = findViewById(R.id.txtDate);
        txtContent = findViewById(R.id.txtContent);
        txtUploaded = findViewById(R.id.txtUploaded);
        txtDraft = findViewById(R.id.txtDraft);
        txtLink = findViewById(R.id.txtLink);
        MaterialButton btnEdit = findViewById(R.id.btnEdit);
        MaterialButton btnShare = findViewById(R.id.btnShare);
        MaterialButton btnUpload = findViewById(R.id.btnUpload);

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit) {
                openEditor();
                return true;
            } else if (id == R.id.action_delete) {
                confirmDelete();
                return true;
            }
            return false;
        });

        btnEdit.setOnClickListener(v -> openEditor());
        btnShare.setOnClickListener(v -> UiUtils.shareMessage(this, message));
        btnUpload.setOnClickListener(v ->
                WordPressUploader.runUpload(this, Collections.singletonList(message), this::loadMessage));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMessage(); // refresh after editing
    }

    private void openEditor() {
        Intent intent = new Intent(this, EditMessageActivity.class);
        intent.putExtra(EditMessageActivity.EXTRA_ID, messageId);
        startActivity(intent);
    }

    private void loadMessage() {
        message = db.getMessage(messageId);
        if (message == null) { finish(); return; }

        txtTitle.setText(message.getTitle());
        txtContent.setText(message.getContent());
        String date = UiUtils.formatDate(message.getCreatedAt());
        if (message.getUpdatedAt() != message.getCreatedAt()) date += "  (edited)";
        txtDate.setText(date);

        if (message.hasImage()) {
            imgMessage.setImageBitmap(ImageUtils.decodeSampled(message.getImagePath(), 1080));
            imgMessage.setVisibility(View.VISIBLE);
        } else {
            imgMessage.setVisibility(View.GONE);
        }

        txtUploaded.setVisibility(message.isUploaded() ? View.VISIBLE : View.GONE);
        txtDraft.setVisibility(message.isDraft() ? View.VISIBLE : View.GONE);
        badgeRow.setVisibility(message.isUploaded() || message.isDraft() ? View.VISIBLE : View.GONE);

        String url = message.getRemoteUrl();
        if (url != null && !url.isEmpty()) {
            txtLink.setText("View online: " + url);
            txtLink.setVisibility(View.VISIBLE);
        } else {
            txtLink.setVisibility(View.GONE);
        }
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this post?")
                .setMessage("This post and its photo will be permanently deleted from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    db.deleteMessage(messageId);
                    Toast.makeText(this, "Post deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }
}