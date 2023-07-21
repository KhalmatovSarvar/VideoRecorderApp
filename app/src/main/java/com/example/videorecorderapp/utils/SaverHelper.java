package com.example.videorecorderapp.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SaverHelper {
    private static final String PREF_NAME = "MyPreferences";
    private static final String KEY_NAME = "name";
    private static final String KEY_AGE = "age";

    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;

    public SaverHelper(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = preferences.edit();
    }

    public void saveName(String name) {
        editor.putString(KEY_NAME, name);
        editor.apply();
    }

    public String loadName() {
        return preferences.getString(KEY_NAME, "");
    }

    public void saveAge(int age) {
        editor.putInt(KEY_AGE, age);
        editor.apply();
    }

    public int loadAge() {
        return preferences.getInt(KEY_AGE, 0);
    }

    public String generateVideoFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = sdf.format(new Date());
        return currentTime + ".mp4"; // Assuming the video format is MP4
    }
}
