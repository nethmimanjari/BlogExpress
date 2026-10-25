package uk.ac.wlv.blogexpress;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Publishes messages to the user's personal WordPress.com blog using the
 * WordPress.com REST API (v1.1) over HTTPS. Draft posts are uploaded as
 * WordPress drafts; normal posts are published.
 * Third-party library: OkHttp (Square, Apache 2.0) - used because it makes
 * multipart image uploads and HTTPS requests simple and reliable, and it
 * supports Android 5.0 (API 21) and above.
 */
public class WordPressUploader {

    private static final String TOKEN_URL = "https://public-api.wordpress.com/oauth2/token";
    private static final String API_BASE = "https://public-api.wordpress.com/rest/v1.1/sites/";
    private static final MediaType JPEG = MediaType.get("image/jpeg");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private final SettingsManager settings;

    public WordPressUploader(Context context) {
        settings = new SettingsManager(context);
    }

    /**
     * Logs in once with the OAuth2 "password" grant and returns an access token.
     * Must be called on a background thread.
     */
    public static String requestToken(String clientId, String clientSecret,
                                      String username, String password) throws IOException, JSONException {
        RequestBody body = new FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "password")
                .add("username", username)
                .add("password", password)
                .build();
        Request request = new Request.Builder().url(TOKEN_URL).post(body).build();
        try (Response response = CLIENT.newCall(request).execute()) {
            String json = readBody(response);
            if (!response.isSuccessful()) throw new IOException(extractError(json, response.code()));
            return new JSONObject(json).getString("access_token");
        }
    }

    /**
     * Uploads one message (and its photo) as a new blog post.
     * Returns the URL of the new post. Must be called on a background thread.
     */
    public String uploadMessage(Message m) throws IOException, JSONException {
        String token = settings.getToken();
        String site = settings.getSite();

        // Escape HTML so user text cannot inject scripts into the blog (security)
        String html = TextUtils.htmlEncode(m.getContent()).replace("\n", "<br>\n");
        long featuredId = -1;

        if (m.hasImage()) {
            JSONObject media = uploadImage(token, site, new File(m.getImagePath()));
            featuredId = media.optLong("ID", -1);
            String imageUrl = media.optString("URL", "");
            if (!imageUrl.isEmpty()) {
                html = "<img src=\"" + imageUrl + "\" alt=\"" + TextUtils.htmlEncode(m.getTitle())
                        + "\" /><br>\n" + html;
            }
        }

        FormBody.Builder form = new FormBody.Builder()
                .add("title", m.getTitle())
                .add("content", html)
                .add("status", m.isDraft() ? "draft" : "publish");
        if (featuredId > 0) form.add("featured_image", String.valueOf(featuredId));

        Request request = new Request.Builder()
                .url(API_BASE + site + "/posts/new")
                .header("Authorization", "Bearer " + token)
                .post(form.build())
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            String json = readBody(response);
            if (!response.isSuccessful()) throw new IOException(extractError(json, response.code()));
            return new JSONObject(json).optString("URL", "");
        }
    }

    /** Uploads a photo to the WordPress media library and returns its JSON info. */
    private JSONObject uploadImage(String token, String site, File file) throws IOException, JSONException {
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("media[]", file.getName(), RequestBody.create(file, JPEG))
                .build();
        Request request = new Request.Builder()
                .url(API_BASE + site + "/media/new")
                .header("Authorization", "Bearer " + token)
                .post(body)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            String json = readBody(response);
            if (!response.isSuccessful()) throw new IOException(extractError(json, response.code()));
            JSONArray media = new JSONObject(json).optJSONArray("media");
            if (media == null || media.length() == 0) throw new IOException("Image upload failed");
            return media.getJSONObject(0);
        }
    }

    private static String readBody(Response response) throws IOException {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    private static String extractError(String json, int code) {
        String msg = "";
        try {
            JSONObject o = new JSONObject(json);
            msg = o.optString("error_description", o.optString("message", o.optString("error", "")));
        } catch (JSONException ignored) { }
        if (msg.isEmpty()) msg = "Server returned HTTP " + code;
        if (code == 401 || code == 403) msg += " (please sign in again in Settings)";
        return msg;
    }

    /**
     * Checks for an internet connection.
     * API 23+ uses NetworkCapabilities; API 21-22 falls back to NetworkInfo.
     */
    @SuppressWarnings("deprecation")
    private static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    /**
     * Shared upload flow used by the list screen (single, group, "upload all")
     * and the detail screen. Runs network work on a background thread and
     * shows progress + a result dialog on the UI thread.
     */
    public static void runUpload(AppCompatActivity activity, List<Message> messages, Runnable onFinished) {
        if (messages == null || messages.isEmpty()) {
            Toast.makeText(activity, "There is nothing to upload", Toast.LENGTH_SHORT).show();
            return;
        }

        SettingsManager sm = new SettingsManager(activity);
        if (!sm.isConnected()) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Connect WordPress")
                    .setMessage("Sign in to your WordPress.com account before uploading.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Sign in", (d, w) ->
                            activity.startActivity(new Intent(activity, AccountActivity.class)))
                    .show();
            return;
        }
        if (!isOnline(activity)) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("You're offline")
                    .setMessage("Your posts are safely saved on this device. Connect to the internet to upload them.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        AlertDialog progress = UiUtils.showProgress(activity, "Uploading…");
        DatabaseHelper db = DatabaseHelper.getInstance(activity);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        WordPressUploader uploader = new WordPressUploader(activity);

        new Thread(() -> {
            int success = 0;
            StringBuilder errors = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                Message m = messages.get(i);
                final String status = "Uploading " + (i + 1) + " of " + messages.size() + "…";
                mainHandler.post(() -> UiUtils.updateProgress(progress, status));
                try {
                    String url = uploader.uploadMessage(m);
                    db.markUploaded(m.getId(), url);
                    success++;
                } catch (Exception e) {
                    errors.append("• ").append(m.getTitle()).append(": ").append(e.getMessage()).append("\n");
                }
            }
            final int ok = success;
            final String err = errors.toString();
            mainHandler.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                progress.dismiss();
                String text = ok + " of " + messages.size() + " post(s) uploaded successfully.";
                if (!err.isEmpty()) text += "\n\nProblems:\n" + err;
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(err.isEmpty() ? "Upload complete 🎉" : "Upload finished")
                        .setMessage(text)
                        .setPositiveButton("OK", null)
                        .show();
                if (onFinished != null) onFinished.run();
            });
        }).start();
    }
}