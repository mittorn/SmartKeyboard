/*
 * Copyright (C) 2010-2017 Cyril Deguet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dexilog.smartkeyboard.settings;

import android.content.SharedPreferences;

// Adapter to retrieve vibrator duration
public class VibratorSettings {

    // legacy setting
    static final String PREF_VIBRATE_DURATION = "vibrator_duration";
    static final String PREF_VIBRATE_DURATION_MS = "vibrator_duration_ms";

    static final int DEFAULT_LEGACY_DURATION = 1;

    SharedPreferences prefs;
    float vibratorDurationMs;

    public VibratorSettings(SharedPreferences prefs) {
        this.prefs = prefs;
        vibratorDurationMs = prefs.getFloat(PREF_VIBRATE_DURATION_MS, -1);
        if (vibratorDurationMs == -1) {
            convertLegacyPreference();
        }
    }

    public float getDurationMs() {
        return vibratorDurationMs;
    }

    private void convertLegacyPreference() {
        float legacyDuration = prefs.getFloat(PREF_VIBRATE_DURATION, -1.f);
        SharedPreferences.Editor editor = prefs.edit();
        if (legacyDuration != -1) {
            editor.remove(PREF_VIBRATE_DURATION);
        } else {
            legacyDuration = DEFAULT_LEGACY_DURATION;
        }
        vibratorDurationMs = 10 + 5 * legacyDuration;
        editor.putFloat(PREF_VIBRATE_DURATION_MS, vibratorDurationMs);
        editor.commit();
    }

}
