package uk.ac.wlv.blogexpress;

import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Screen for creating a new post or editing an existing one (works fully offline).
 * Posts can be saved as a draft or published. Photos can be attached from the
 * gallery (Photo Picker / system picker - no storage permission needed) or
 * taken with the camera (via FileProvider).
 */
public class EditMessageActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "message_id";
    private static final String STATE_IMAGE = "state_image";
    private static final String STATE_CAMERA = "state_camera";

    private DatabaseHelper db;
    private Message message; // null when creating a new post

    private TextInputLayout layoutTitle, layoutContent;
    private TextInputEditText edtTitle, edtContent;
    private TextView txtCharCount;
    private MaterialCardView cardImage;
    private ImageView imgPreview;

    private String originalImagePath; // photo saved in DB before editing
    private String currentImagePath;  // photo currently shown on screen
    private File cameraFile;          // temp file the camera writes into
    private boolean saved = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Gallery: Photo Picker (falls back to the system document picker on older devices)
    private final ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) processImage(uri, null);
            });

    // Camera: the camera app saves the full-size photo into our temp file
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (cameraFile == null) return;
                if (Boolean.TRUE.equals(success)) {
                    processImage(Uri.fromFile(cameraFile), cameraFile);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    cameraFile.delete();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiUtils.enableEdgeToEdge(this);
        setContentView(R.layout.activity_edit_message);
        UiUtils.applySystemBarInsets(findViewById(R.id.main));

        db = DatabaseHelper.getInstance(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        layoutTitle = findViewById(R.id.layoutTitle);
        layoutContent = findViewById(R.id.layoutContent);
        edtTitle = findViewById(R.id.edtTitle);
        edtContent = findViewById(R.id.edtContent);
        txtCharCount = findViewById(R.id.txtCharCount);
        cardImage = findViewById(R.id.cardImage);
        imgPreview = findViewById(R.id.imgPreview);
        MaterialButton btnGallery = findViewById(R.id.btnGallery);
        MaterialButton btnCamera = findViewById(R.id.btnCamera);
        MaterialButton btnRemove = findViewById(R.id.btnRemoveImage);
        MaterialButton btnSaveDraft = findViewById(R.id.btnSaveDraft);
        MaterialButton btnPublish = findViewById(R.id.btnPublish);

        // Edit mode if an id was passed in, otherwise create mode
        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        if (id != -1) {
            message = db.getMessage(id);
            if (message == null) { finish(); return; }
            toolbar.setTitle("Edit Post");
            edtTitle.setText(message.getTitle());
            edtContent.setText(message.getContent());
            originalImagePath = message.getImagePath();
            currentImagePath = originalImagePath;
            btnPublish.setText(message.isDraft() ? "Publish" : "Update Post");
        } else {
            toolbar.setTitle("New Post");
            btnPublish.setText("Publish");
        }

        // Restore state after screen rotation
        if (savedInstanceState != null) {
            currentImagePath = savedInstanceState.getString(STATE_IMAGE);
            String cam = savedInstanceState.getString(STATE_CAMERA);
            if (cam != null) cameraFile = new File(cam);
        }
        showImage();

        // Live character counter
        edtContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { updateCharCount(); }
        });
        updateCharCount();

        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        btnGallery.setOnClickListener(v -> galleryLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        btnCamera.setOnClickListener(v -> openCamera());
        btnRemove.setOnClickListener(v -> removeImage());
        btnSaveDraft.setOnClickListener(v -> saveMessage(true));
        btnPublish.setOnClickListener(v -> saveMessage(false));

        // Ask before throwing away unsaved changes
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    new MaterialAlertDialogBuilder(EditMessageActivity.this)
                            .setTitle("Discard changes?")
                            .setMessage("Your changes will not be saved.")
                            .setNegativeButton("Keep editing", null)
                            .setPositiveButton("Discard", (d, w) -> finish())
                            .show();
                } else {
                    finish();
                }
            }
        });
    }

    private void updateCharCount() {
        int count = edtContent.getText() == null ? 0 : edtContent.getText().length();
        txtCharCount.setText(count + (count == 1 ? " char" : " chars"));
    }

    private void openCamera() {
        try {
            cameraFile = ImageUtils.createCameraFile(this);
            cameraLauncher.launch(ImageUtils.getUriForFile(this, cameraFile));
        } catch (IOException | ActivityNotFoundException e) {
            Toast.makeText(this, "Camera is not available", Toast.LENGTH_SHORT).show();
        }
    }

    /** Copies/resizes the chosen photo on a background thread, then shows it. */
    private void processImage(Uri uri, File tempFileToDelete) {
        executor.execute(() -> {
            try {
                String path = ImageUtils.saveImageFromUri(this, uri);
                mainHandler.post(() -> {
                    discardNewImage();
                    currentImagePath = path;
                    showImage();
                });
            } catch (IOException e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "Could not load the photo", Toast.LENGTH_SHORT).show());
            } finally {
                if (tempFileToDelete != null) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFileToDelete.delete();
                }
            }
        });
    }

    /** Deletes the current photo only if it was added in this session (not the saved one). */
    private void discardNewImage() {
        if (currentImagePath != null && !currentImagePath.equals(originalImagePath)) {
            ImageUtils.deleteImage(currentImagePath);
        }
    }

    private void removeImage() {
        discardNewImage();
        currentImagePath = null;
        showImage();
    }

    private void showImage() {
        if (currentImagePath != null && new File(currentImagePath).exists()) {
            imgPreview.setImageBitmap(ImageUtils.decodeSampled(currentImagePath, 800));
            cardImage.setVisibility(View.VISIBLE);
        } else {
            imgPreview.setImageDrawable(null);
            cardImage.setVisibility(View.GONE);
        }
    }

    private static String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private boolean hasUnsavedChanges() {
        String t = text(edtTitle);
        String c = text(edtContent);
        if (message == null) return !t.isEmpty() || !c.isEmpty() || currentImagePath != null;
        return !t.equals(message.getTitle()) || !c.equals(message.getContent())
                || !Objects.equals(currentImagePath, originalImagePath);
    }

    /** Validates input and saves the post to SQLite as a draft or a normal post. */
    private void saveMessage(boolean asDraft) {
        String title = text(edtTitle);
        String content = text(edtContent);
        layoutTitle.setError(null);
        layoutContent.setError(null);

        if (title.isEmpty()) {
            layoutTitle.setError("Please enter a title");
            edtTitle.requestFocus();
            return;
        }
        if (!asDraft && content.isEmpty()) {
            layoutContent.setError("Please write something before publishing");
            edtContent.requestFocus();
            return;
        }

        long now = System.currentTimeMillis();
        if (message == null) {
            Message m = new Message();
            m.setTitle(title);
            m.setContent(content);
            m.setImagePath(currentImagePath);
            m.setDraft(asDraft);
            m.setCreatedAt(now);
            m.setUpdatedAt(now);
            db.insertMessage(m);
        } else {
            message.setTitle(title);
            message.setContent(content);
            message.setImagePath(currentImagePath);
            message.setDraft(asDraft);
            message.setUpdatedAt(now);
            db.updateMessage(message);
            // Old photo was replaced or removed -> delete the old file
            if (originalImagePath != null && !originalImagePath.equals(currentImagePath)) {
                ImageUtils.deleteImage(originalImagePath);
            }
        }

        String toast = asDraft ? "Draft saved ✓" : (message == null ? "Post saved ✓" : "Post updated ✓");
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
        saved = true;
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_IMAGE, currentImagePath);
        if (cameraFile != null) outState.putString(STATE_CAMERA, cameraFile.getAbsolutePath());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up a newly added photo if the user left without saving
        if (isFinishing() && !saved) discardNewImage();
        executor.shutdown();
    }
}