package uk.ac.wlv.blogexpress;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Application class: runs once when the app process starts.
 * Applies the saved light/dark theme before any screen is shown.
 */
public class BlogExpressApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SettingsManager settings = new SettingsManager(this);
        settings.getFirstRunTime(); // records "member since" on the very first launch
        AppCompatDelegate.setDefaultNightMode(settings.isDarkMode()
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }
}