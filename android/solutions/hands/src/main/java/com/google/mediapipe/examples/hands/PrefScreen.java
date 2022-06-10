package com.google.mediapipe.examples.hands;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.preference.MultiSelectListPreference;

import androidx.preference.PreferenceFragmentCompat;

public class PrefScreen extends PreferenceFragmentCompat {

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        MultiSelectListPreference emojiListPref = (MultiSelectListPreference) findPreference("emoji_pref");

        emojiListPref.setEnabled(true);

    }
}
