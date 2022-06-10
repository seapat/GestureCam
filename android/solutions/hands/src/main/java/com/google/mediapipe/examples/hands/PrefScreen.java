package com.google.mediapipe.examples.hands;

import android.os.Bundle;

import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceFragmentCompat;

public class PrefScreen extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        MultiSelectListPreference emojiListPref = (MultiSelectListPreference) findPreference("emoji_pref");

        assert emojiListPref != null;
        emojiListPref.setEnabled(true);

    }
}
