package com.google.mediapipe.examples.hands;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.preference.MultiSelectListPreference;

import androidx.preference.PreferenceFragmentCompat;

import java.util.EnumMap;
import java.util.Map;

public class PrefScreen extends PreferenceFragmentCompat {

    private enum HandGesture{
        VICTORY,
        HORNS,
        LOVE,
        INDEX,
        OK,
        MIDDLE,
        CALL,
        THUMBS,
        FIST,
        UNDEFINED
    }

    // Dictionary mapping icons with gestures
    private static final EnumMap<PrefScreen.HandGesture,Integer> gestureEmojis= new EnumMap<>(Map.of(
            PrefScreen.HandGesture.VICTORY, 0x270C,
            PrefScreen.HandGesture.HORNS, 0x1F918,
            PrefScreen.HandGesture.LOVE, 0x1F91F,
            PrefScreen.HandGesture.INDEX, 0x261D,
            PrefScreen.HandGesture.OK, 0x1f44c,
            PrefScreen.HandGesture.MIDDLE, 0x1f595,
            PrefScreen.HandGesture.CALL, 0x1F919,
            PrefScreen.HandGesture.THUMBS, 0x1F44D,
            PrefScreen.HandGesture.FIST, 0x270A
    ));

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        MultiSelectListPreference emojiListPref = (MultiSelectListPreference) findPreference("emoji_pref");

//        emojiListPref.setEntries(gestureEmojis.keySet().toArray(new String[gestureEmojis.size()]));
//        emojiListPref.setEntryValues(gestureEmojis.values().toArray(new String[gestureEmojis.size()]));

        emojiListPref.setEnabled(true);

    }
}
