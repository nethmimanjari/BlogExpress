package uk.ac.wlv.blogexpress;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores app settings in private SharedPreferences (MODE_PRIVATE = only this
 * app can read them): WordPress connection (site, username, OAuth token),
 * profile, dark mode and first-run information.
 * The user's WordPress password is NEVER stored.
 */
public class SettingsManager {

    private static final String PREFS = "blogexpress_prefs";
    private static final String KEY_SITE = "site";
    private static final String KEY_USER = "username";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_NAME = "profile_name";
    private static final String KEY_EMAIL = "profile_email";
    private static final String KEY_DARK = "dark_mode";
    private static final String KEY_SEEDED = "samples_seeded";
    private static final String KEY_FIRST_RUN = "first_run";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------- WordPress ----------------

    public String getSite() { return prefs.getString(KEY_SITE, ""); }
    public String getUsername() { return prefs.getString(KEY_USER, ""); }
    public String getToken() { return prefs.getString(KEY_TOKEN, ""); }

    public boolean isConnected() {
        return !getSite().isEmpty() && !getToken().isEmpty();
    }

    public void saveConnection(String site, String username, String token) {
        prefs.edit()
                .putString(KEY_SITE, site)
                .putString(KEY_USER, username)
                .putString(KEY_TOKEN, token)
                .apply();
    }

    /** Signs out of WordPress (profile and other settings are kept). */
    public void clearWordPress() {
        prefs.edit().remove(KEY_SITE).remove(KEY_USER).remove(KEY_TOKEN).apply();
    }

    // ---------------- Profile ----------------

    public String getProfileName() { return prefs.getString(KEY_NAME, "BlogExpress Writer"); }
    public String getProfileEmail() { return prefs.getString(KEY_EMAIL, ""); }

    public void saveProfile(String name, String email) {
        prefs.edit().putString(KEY_NAME, name).putString(KEY_EMAIL, email).apply();
    }

    // ---------------- App preferences ----------------

    public boolean isDarkMode() { return prefs.getBoolean(KEY_DARK, false); }
    public void setDarkMode(boolean dark) { prefs.edit().putBoolean(KEY_DARK, dark).apply(); }

    public boolean isSeeded() { return prefs.getBoolean(KEY_SEEDED, false); }
    public void setSeeded(boolean seeded) { prefs.edit().putBoolean(KEY_SEEDED, seeded).apply(); }

    /** Time of the first launch (saved the first time it is asked for). */
    public long getFirstRunTime() {
        long time = prefs.getLong(KEY_FIRST_RUN, 0);
        if (time == 0) {
            time = System.currentTimeMillis();
            prefs.edit().putLong(KEY_FIRST_RUN, time).apply();
        }
        return time;
    }
}