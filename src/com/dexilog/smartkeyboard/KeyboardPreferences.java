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

package com.dexilog.smartkeyboard;

import android.content.SharedPreferences;

public class KeyboardPreferences {
    public static final String PREF_VIBRATE_ON = "vibrate";
    public static final String PREF_DISABLE_SETTINGS = "disable_settings";
    static final String PREF_SOUND_ON = "sound_on";
    static final String PREF_AUTO_CAP = "auto_cap";
    static final String PREF_QUICK_FIXES = "quick_fixes";
    static final String PREF_SHOW_SUGGESTIONS = "show_suggestions";
    static final String PREF_AUTO_COMPLETE = "auto_complete";
    static final String PREF_CONTACTS = "contact_dic";
    static final String PREF_SHOW_PREVIEW = "show_preview";
    static final String PREF_SUGGEST_HARD = "suggest_hard";
    public static final String PREF_SKIN = "skin";
    static final String PREF_TRANSPARENCY = "opacity";
    static final String PREF_VOLUME = "volume";
    static final String PREF_DEBUG = "debug";
    static final String PREF_ALWAYS_SUGGEST = "always_suggest";
    static final String PREF_ENABLE_ARROWS = "enable_arrows";
    static final String PREF_ALWAYS_CAPS = "always_caps";
    public static final String PREF_DISPLAY_ALT = "display_alt_labels";
    static final String PREF_MIC_BUTTON = "mic_button";
    static final String PREF_MIC_ABOVE_COMMA = "mic_above_comma";
    static final String PREF_RESTART_VOICE = "restart_voice";
    static final String PREF_VOICE_BEST = "voice_best";
    static final String PREF_LEGACY_VOICE = "legacy_voice";
    static final String PREF_TOUCH_POINTS = "touch_points";
    static final String PREF_PORTRAIT_MODE = "portrait_mode";
    static final String PREF_CANDIDATE_COLOR = "candidate_text_color";
    static final String PREF_DISABLE_LAUNCHER = "disable_launcher";
    static final String PREF_SPACE_WHEN_PICK = "space_when_pick";
    static final String PREF_SWAP_PUNCTUATION_SPACE = "swap_punctuation_space";
    static final String PREF_SMILEY_KEY = "smiley_key";
    static final String PREF_SMART_DICTIONARY = "smart_dictionary";
    static final String PREF_SOUND_STYLE = "sound_style";
    static final String PREF_SLIDE_POPUP = "slide_popup";
    static final String PREF_SPACE_PREVIEW = "space_preview";
    static final String PREF_LATIN_LAYOUT = "latin_layout";
    static final String PREF_RTL_SUGGESTIONS = "rtl_suggestions";
    static final String PREF_SUGGEST_PUNCTUATION = "suggest_punctuation";
    static final String PREF_LONGPRESS_DURATION = "longpress_duration";
    static final String PREF_LEARN_NEW_WORDS = "learn_new_words";
    static final String PREF_ENTER_SENDS_SMS = "enter_sends_sms";
    static final String PREF_PERSISTENT_DOMAIN_KEY = "persistent_domain_key";
    static final String PREF_DOMAIN_KEY = "domain_key";
    static final String PREF_NO_ALT_PREVIEW = "no_alt_preview";
    static final String PREF_DYNAMIC_RESIZING = "dynamic_resizing";
    static final String PREF_DOUBLE_SPACE_PERIOD = "double_space_period";
    static final String PREF_ARROWS_STYLE = "arrows_style";
    static final String PREF_DISABLE_MT = "disable_mt";
    static final String PREF_COMPOUND_SUGGESTIONS = "compound_suggestions";
    static final String PREF_KOREAN_NUMBERS_PRIORITY = "korean_numbers_priority";
    static final String PREF_NO_LANDSCAPE_FULLSCREEN = "no_lansdcape_fullscreen";
    static final String PREF_T9_LENGTH_PRIORITY = "t9_length_priority";
    static final String PREF_T9_NEXT_KEY = "t9_next_key";
    static final String PREF_SUGGEST_NUMBERS = "suggest_numbers";
    static final String PREF_ASK_ENGLISH_DIC = "ask_english_dic3";
    static final String PREF_T9_PREDICTION = "t9_prediction";// Whether or not the user has used voice input before (and thus, whether to
    // show the
    // first-run warning dialog or not).
    public static final String PREF_HAS_USED_VOICE_INPUT = "has_used_voice_input";// Whether or not the user has used voice input from an unsupported locale
    // UI before.
    // For example, the user has a Chinese UI but activates voice input.
    static final String PREF_HAS_USED_VOICE_INPUT_UNSUPPORTED_LOCALE = "has_used_voice_input_unsupported_locale";
    static final String PREF_HEBREW_ALT = "hebrew_alt";
    static final String PREF_PORTRAIT_FULLSCREEN = "portrait_fullscreen";
    static final String PREF_CZECH_FULL = "czech_full";
    static final String PREF_SWIPE_FACTOR = "swipe_factor";
    static final String PREF_MULTITAP_INTERVAL = "multitap_interval";
    static final String PREF_SMS_MODE = "sms_mode";
    public static final String PREF_KEY_HEIGHT = "key_height";
    public static final String PREF_KEY_HEIGHT_LANDSCAPE = "key_height_landscape";
    static final String PREF_ALT_SYMBOLS = "alt_symbols";
    static final String PREF_HIDE_PERIOD = "hide_period";
    static final String PREF_HIDE_COMMA = "hide_comma";
    public static final String PREF_BOTTOM_PADDING = "bottom_padding";
    static final String PREF_APOSTROPHE_SEPARATOR = "apostrophe_separator";
    static final String PREF_IGNORE_HARD_KBD = "ignore_hard_kbd";
    static final String PREF_HIDE_IN_PORTRAIT = "hide_in_portrait";
    static final String PREF_CUSTOM_PUNCTUATION = "custom_punctuation";
    static final String PREF_NO_LANDSCAPE_SUGGESTIONS = "no_landscape_suggestions";
    static final String PREF_ARROWS_MAIN = "show_arrows_main";
    static final String PREF_NUMBERS_TOP = "show_numbers_top";
    static final String PREF_CURSOR_VOLUME = "cursor_volume";
    static final String PREF_ACCENTS_PRIORITY = "accents_priority";
    static final String PREF_RECORRECTION_ENABLED = "recorrection_enabled";
    static final String PREF_ALT_COMPACT = "alt_compact";
    static final String PREF_SPACE_ALERT = "space_alert";
    static final String PREF_HIDE_LANG_KEY = "hide_lang_key";
    static final String PREF_LANG_ICON = "show_language_icon";
    private static final String PREF_MORE_SYMBOLS = "more_symbols";
    private static final String PREF_CUSTOM_SMILEYS = "custom_smileys";

    public boolean altSymbols;
    public boolean moreSymbols;
    public boolean displayAlt;
    public boolean micAboveComma;
    public boolean customSmileys;

    public void initialize(SharedPreferences sp) {
        altSymbols = sp.getBoolean(PREF_ALT_SYMBOLS, true);
        moreSymbols = sp.getBoolean(PREF_MORE_SYMBOLS, true);
        displayAlt = sp.getBoolean(PREF_DISPLAY_ALT, true);
        micAboveComma = sp.getBoolean(PREF_MIC_ABOVE_COMMA, false);
        customSmileys = sp.getBoolean(PREF_CUSTOM_SMILEYS, false);
    }

}