package uk.ac.wlv.blogexpress;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Home screen with bottom navigation:
 *  - Home: all posts
 *  - Search: live search through titles and content
 *  - Upload: posts not uploaded yet + "Upload all"
 *  - Settings: opens the settings screen
 * Also supports multi-select (select all, group delete, group upload)
 * and a ⋮ menu on every card.
 */
public class MainActivity extends AppCompatActivity implements MessageAdapter.Listener {

    private static final String STATE_TAB = "state_tab";
    private static final int TAB_HOME = 0;
    private static final int TAB_SEARCH = 1;
    private static final int TAB_UPLOAD = 2;

    private DatabaseHelper db;
    private MessageAdapter adapter;
    private int currentTab = TAB_HOME;

    private View normalHeader, emptyView;
    private MaterialToolbar selectionToolbar;
    private TextInputLayout searchLayout;
    private TextInputEditText edtSearch;
    private TextView txtHeaderCount, txtSectionTitle, txtEmptyTitle, txtEmptyMessage, txtUploadBanner;
    private ImageView imgEmpty;
    private MaterialCardView uploadBanner;
    private FloatingActionButton fabAdd;
    private ImageView navHomeIcon, navSearchIcon, navUploadIcon;
    private TextView navHomeLabel, navSearchLabel, navUploadLabel;
    private OnBackPressedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiUtils.enableEdgeToEdge(this);
        setContentView(R.layout.activity_main);
        UiUtils.applySystemBarInsets(findViewById(R.id.main));

        db = DatabaseHelper.getInstance(this);
        normalHeader = findViewById(R.id.normalHeader);
        selectionToolbar = findViewById(R.id.selectionToolbar);
        searchLayout = findViewById(R.id.searchLayout);
        edtSearch = findViewById(R.id.edtSearch);
        txtHeaderCount = findViewById(R.id.txtHeaderCount);
        txtSectionTitle = findViewById(R.id.txtSectionTitle);
        uploadBanner = findViewById(R.id.uploadBanner);
        txtUploadBanner = findViewById(R.id.txtUploadBanner);
        emptyView = findViewById(R.id.emptyView);
        imgEmpty = findViewById(R.id.imgEmpty);
        txtEmptyTitle = findViewById(R.id.txtEmptyTitle);
        txtEmptyMessage = findViewById(R.id.txtEmptyMessage);
        fabAdd = findViewById(R.id.fabAdd);
        navHomeIcon = findViewById(R.id.navHomeIcon);
        navHomeLabel = findViewById(R.id.navHomeLabel);
        navSearchIcon = findViewById(R.id.navSearchIcon);
        navSearchLabel = findViewById(R.id.navSearchLabel);
        navUploadIcon = findViewById(R.id.navUploadIcon);
        navUploadLabel = findViewById(R.id.navUploadLabel);

        // Post list
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter(this);
        recyclerView.setAdapter(adapter);

        // Header "Select" button
        findViewById(R.id.btnSelect).setOnClickListener(v -> {
            if (adapter.getItemCount() == 0) {
                Toast.makeText(this, "There are no posts to select", Toast.LENGTH_SHORT).show();
            } else {
                adapter.enterSelectionMode();
            }
        });

        // Selection toolbar actions
        selectionToolbar.setNavigationOnClickListener(v -> exitSelectionMode());
        selectionToolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_select_all) {
                adapter.selectAll();
                return true;
            }
            List<Message> selected = adapter.getSelectedMessages();
            if (selected.isEmpty()) {
                Toast.makeText(this, "Select at least one post", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (id == R.id.action_delete_selected) {
                confirmDeleteSelected(selected);
                return true;
            } else if (id == R.id.action_upload_selected) {
                WordPressUploader.runUpload(this, selected, () -> {
                    exitSelectionMode();
                    loadMessages();
                });
                return true;
            }
            return false;
        });

        // New post
        fabAdd.setOnClickListener(v -> startActivity(new Intent(this, EditMessageActivity.class)));

        // Bottom navigation
        findViewById(R.id.navHome).setOnClickListener(v -> switchTab(TAB_HOME));
        findViewById(R.id.navSearch).setOnClickListener(v -> switchTab(TAB_SEARCH));
        findViewById(R.id.navUpload).setOnClickListener(v -> switchTab(TAB_UPLOAD));
        findViewById(R.id.navSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Upload tab "Upload all" button
        findViewById(R.id.btnUploadAll).setOnClickListener(v ->
                WordPressUploader.runUpload(this, db.getPendingMessages(), this::loadMessages));

        // Live search
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { loadMessages(); }
        });
        edtSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });

        // Back: leave selection mode first, then go back to the Home tab
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (adapter.isSelectionMode()) exitSelectionMode();
                else switchTab(TAB_HOME);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);

        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt(STATE_TAB, TAB_HOME);
        }
        switchTab(currentTab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMessages(); // refresh after creating/editing/deleting/uploading
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    // ---------------- Tabs ----------------

    private void switchTab(int tab) {
        if (adapter.isSelectionMode()) exitSelectionMode();
        currentTab = tab;

        searchLayout.setVisibility(tab == TAB_SEARCH ? View.VISIBLE : View.GONE);
        if (tab == TAB_SEARCH) {
            edtSearch.requestFocus();
            edtSearch.post(this::showKeyboard);
        } else {
            hideKeyboard();
        }

        styleNav(navHomeIcon, navHomeLabel, tab == TAB_HOME);
        styleNav(navSearchIcon, navSearchLabel, tab == TAB_SEARCH);
        styleNav(navUploadIcon, navUploadLabel, tab == TAB_UPLOAD);

        loadMessages();
        updateBackCallback();
    }

    private void styleNav(ImageView icon, TextView label, boolean selected) {
        int color = ContextCompat.getColor(this, selected ? R.color.primary : R.color.text_secondary);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));
        label.setTextColor(color);
        label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    /** Loads the posts for the current tab and updates all texts. */
    private void loadMessages() {
        String query = edtSearch.getText() == null ? "" : edtSearch.getText().toString().trim();
        List<Message> list;

        if (currentTab == TAB_SEARCH) {
            list = query.isEmpty() ? new ArrayList<>() : db.searchMessages(query);
            txtSectionTitle.setText(query.isEmpty()
                    ? "Search your posts"
                    : list.size() + " result(s) for “" + query + "”");
            imgEmpty.setImageResource(R.drawable.ic_search);
            txtEmptyTitle.setText(query.isEmpty() ? "Find a post" : "No matching posts");
            txtEmptyMessage.setText(query.isEmpty()
                    ? "Type a word to search titles and content."
                    : "Try searching with a different word.");
        } else if (currentTab == TAB_UPLOAD) {
            list = db.getPendingMessages();
            txtSectionTitle.setText("Ready to upload");
            imgEmpty.setImageResource(R.drawable.ic_upload);
            txtEmptyTitle.setText("All caught up!");
            txtEmptyMessage.setText("Every post has been uploaded to your blog.");
        } else {
            list = db.getAllMessages();
            txtSectionTitle.setText("Recent posts");
            imgEmpty.setImageResource(R.drawable.ic_edit);
            txtEmptyTitle.setText("No posts yet");
            txtEmptyMessage.setText("Tap + to write your first post.");
        }

        adapter.setMessages(list);
        emptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        boolean showBanner = currentTab == TAB_UPLOAD && !list.isEmpty();
        uploadBanner.setVisibility(showBanner ? View.VISIBLE : View.GONE);
        if (showBanner) {
            txtUploadBanner.setText(list.size() + " post(s) saved offline and ready to publish.");
        }

        long total = db.getCount();
        txtHeaderCount.setText(total + (total == 1 ? " post" : " posts") + " saved offline");
    }

    // ---------------- Selection mode ----------------

    @Override
    public void onSelectionChanged(int selectedCount) {
        normalHeader.setVisibility(View.GONE);
        selectionToolbar.setVisibility(View.VISIBLE);
        selectionToolbar.setTitle(selectedCount == 0 ? "Select posts" : selectedCount + " selected");
        fabAdd.hide();
        updateBackCallback();
    }

    private void exitSelectionMode() {
        adapter.clearSelection();
        selectionToolbar.setVisibility(View.GONE);
        normalHeader.setVisibility(View.VISIBLE);
        fabAdd.show();
        updateBackCallback();
    }

    private void updateBackCallback() {
        backCallback.setEnabled(adapter.isSelectionMode() || currentTab != TAB_HOME);
    }

    // ---------------- Card actions ----------------

    @Override
    public void onMessageClick(Message message) {
        Intent intent = new Intent(this, ViewMessageActivity.class);
        intent.putExtra(ViewMessageActivity.EXTRA_ID, message.getId());
        startActivity(intent);
    }

    @Override
    public void onMessageMenu(Message message, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.menu_card);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_card_edit) {
                Intent intent = new Intent(this, EditMessageActivity.class);
                intent.putExtra(EditMessageActivity.EXTRA_ID, message.getId());
                startActivity(intent);
            } else if (id == R.id.action_card_share) {
                UiUtils.shareMessage(this, message);
            } else if (id == R.id.action_card_upload) {
                WordPressUploader.runUpload(this, Collections.singletonList(message), this::loadMessages);
            } else if (id == R.id.action_card_delete) {
                confirmDelete(message);
            }
            return true;
        });
        popup.show();
    }

    private void confirmDelete(Message message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this post?")
                .setMessage("“" + message.getTitle() + "” and its photo will be permanently deleted from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    db.deleteMessage(message.getId());
                    Toast.makeText(this, "Post deleted", Toast.LENGTH_SHORT).show();
                    loadMessages();
                })
                .show();
    }

    private void confirmDeleteSelected(List<Message> selected) {
        List<Long> ids = new ArrayList<>();
        for (Message m : selected) ids.add(m.getId());
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + ids.size() + " post(s)?")
                .setMessage("The selected posts and their photos will be permanently deleted from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    db.deleteMessages(ids);
                    Toast.makeText(this, ids.size() + " post(s) deleted", Toast.LENGTH_SHORT).show();
                    exitSelectionMode();
                    loadMessages();
                })
                .show();
    }

    // ---------------- Keyboard ----------------

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(edtSearch, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
        edtSearch.clearFocus();
    }
}