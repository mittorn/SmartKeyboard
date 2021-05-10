/*
 * Copyright (C) 2008 The Android Open Source Project
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Calendar;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import android.text.TextUtils;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.dexilog.smartkeyboard.input.InputConnectionProvider;
import com.dexilog.smartkeyboard.input.InputController;
import com.dexilog.smartkeyboard.keyboard.KeyboardFactory;
import com.dexilog.smartkeyboard.ui.CalibrationInfo;
import com.dexilog.smartkeyboard.ui.KeyboardView;
import com.dexilog.smartkeyboard.ui.SkinLoader;
import com.dexilog.smartkeyboard.ui.SkinLoader.SkinInfo;

import com.android.inputmethod.voice.UiListener;
import com.dexilog.smartkeyboard.input.HardKeyboardTranslator;
import com.dexilog.smartkeyboard.input.MyMetaKeyKeyListener;
import com.dexilog.smartkeyboard.input.SwipeGestures;
import com.dexilog.smartkeyboard.input.TextEntryState;
import com.dexilog.smartkeyboard.input.WordComposer;
import com.dexilog.smartkeyboard.input.WordComposerImpl;
import com.dexilog.smartkeyboard.keyboard.CustomKeys;
import com.dexilog.smartkeyboard.keyboard.GlobalResources;
import com.dexilog.smartkeyboard.keyboard.Keyboard;
import com.dexilog.smartkeyboard.keyboard.KeyboardSwitcher;
import com.dexilog.smartkeyboard.lang.Converter;
import com.dexilog.smartkeyboard.lang.Korean;
import com.dexilog.smartkeyboard.settings.VibratorSettings;
import com.dexilog.smartkeyboard.ui.KeyboardView;
import com.dexilog.smartkeyboard.utils.EditingUtil;
import com.dexilog.smartkeyboard.utils.Workarounds;

import com.dexilog.smartkeyboard.ui.CandidateInputService;
import com.dexilog.smartkeyboard.ui.CandidateView;
import com.dexilog.smartkeyboard.ui.CandidateViewContainer;
import android.widget.*;
import android.view.*;
import android.graphics.*;
import android.util.*;


public class SmartKeyboard extends InputMethodService implements
		SharedPreferences.OnSharedPreferenceChangeListener, UiListener, InputConnectionProvider, CandidateInputService {

	static final boolean DEBUG = false;
	static final boolean TRACE = false;
	public static final String TAG = "SmartKeyboard";
	public static final String ACTION_RECOGNITION_DONE = "net.cdeguet.smartkeyboardpro.RECOGNITION_DONE";

	private static final int MSG_UPDATE_SUGGESTIONS = 0;
	private static final int MSG_UPDATE_SHIFT_STATE = 1;
	public static final int MSG_SEND_VOICE_TEXT = 2;
	private static final int MSG_ENABLE_TRIAL_BUTTONS = 3;
	public static final int MSG_VOICE_RESULTS = 4;
	public static final int MSG_UPDATE_OLD_SUGGESTIONS = 5;

	// How many continuous deletes at which to start deleting at a higher speed.
	private static final int DELETE_ACCELERATE_AT = 20;
	// Weight added to a user picking a new word from the suggestion strip
	static final int FREQUENCY_FOR_PICKED = 3;
	// Weight added to a user typing a new word that doesn't get corrected (or
	// is reverted)
	static final int FREQUENCY_FOR_TYPED = 1;

	static final int KEYCODE_ENTER = (int)'\n';
	static final int KEYCODE_SPACE = (int)' ';
	static final int KEYCODE_PERIOD = (int)'.';

	// private static final int SOUND_ANDROID = 0;
	private static final int SOUND_IPHONE = 1;
	private static final int SOUND_GALAXY = 2;
	private static final int SOUND_WIN7 = 3;

	private static final int LANG_ICON_NEVER = 0;
	private static final int LANG_ICON_HARD = 1;
	private static final int LANG_ICON_SOFT = 2;

	private static final String HTC_MAIL = "com.htc.android.mail";
	public final VoiceInputController voiceInputController = new VoiceInputController(this);
	private final MyOnKeyboardActionListener onKeyboardActionListener = new MyOnKeyboardActionListener(this);
	private final SwipeGestures swipeGestures = new SwipeGestures();
	SuggestController suggestController;

	private View mInputView;
	private CandidateViewContainer mCandidateViewContainer;
	public Suggest mSuggest;
	public CompletionInfo[] mCompletions;

	public AlertDialog mOptionsDialog;

	public SharedPreferences mSharedPref;

	KeyboardSwitcher mKeyboardSwitcher;
	CustomKeys mCustomKeys;
	SkinLoader mSkinLoader;

	private ContactsDictionary mContactsDictionary;

	private KeyboardPreferences keyboardPreferences = new KeyboardPreferences();
	public boolean mPredictionOn;
	private boolean mEnableDoubleSpace;
	public boolean mCompletionOn;
	private boolean mAutoSpace;
	private boolean mAutoCorrectOn;
	private boolean mReCorrectionEnabled = true;
	public boolean mCapsLock;
	private boolean mVibrateOn;
	private boolean mSoundOn;
	private boolean mAutoCap;
	private boolean mQuickFixes;
	private boolean mShowSuggestions = false;
	private boolean mShowPreview;
	private boolean mSuggestHardKbd;
	public int mCorrectionMode;
	private int mOrientation;
	public int mSwipeLeft;
	public int mSwipeRight;
	public int mSwipeUp;
	public int mSwipeDown;
	private int mOpacity;
	private float mVolume = -1.0f;
	public boolean mDebug = false;
	private boolean mAlwaysSuggest;
	private boolean mAlwaysCaps;
	public boolean mMicButton;
	private boolean mShowTouchPoints;
	private int mPortraitMode;
	private int mNbPortraitModes;
	private int mCandidateColor;
	private boolean mSpaceWhenPick;
	private boolean mSwapPunctuationSpace;
	private int mSmileyMode;
	private boolean mIMmode = false;
	private SoundPool mSoundPool;
	private int[] mSoundID = { -1, -1, -1, -1 };
	private int mSoundStyle;
	private boolean mSlidePopup;
	private boolean mSpacePreview;
	private boolean mIsShowingHint;
	private boolean mRTLSuggestions;
	public Converter mCurTranslator;
	public List<CharSequence> mSuggestPuncList;
	public boolean mSuggestPunctuation;
	private int mLongpressDuration;
	private int mMultitapInterval;
	private int mSwipeFactor;
	private boolean mEnterSendsSMS;
	private int mDomainKey;
	private boolean mNoAltPreview;
	public boolean mDynamicResizing;
	private boolean mDoubleSpacePeriod;
	private boolean mDisableMT;
	private boolean mCompoundSuggestions;
	private boolean mNoLandscapeFullscreen = true;
	private boolean mPortraitFullscreen = false;
	private boolean mSuggestNumbers;
	public boolean mUseSpaceForNextWord; // For chinese/japanese
	private boolean mAskEnglishDic = false;
	private CalibrationInfo mCalibration;
	public boolean mSmsMode;
	private int mBottomPadding = 0;
	private boolean mIgnoreHardKbd = false;
	private boolean mHidePortrait = false;
	private boolean mNoLandscapeSuggestions;
	private int mArrowsMain = 0;
	private boolean mCursorVolume = false;
	private boolean mAccentsPriority = false;
	private boolean mSpaceAlert = false;
	private int mShowLangIcon = 0;

	public boolean mUseSmartDictionary;
	public boolean mAutoAddToUserDic;
	private boolean mDisableComposing;

	private Vibrator mVibrator;
	private int mVibrateDuration;

	private AudioManager mAudioManager;
	// Align sound effect volume on music volume
	private boolean mSilentMode;

	public boolean mConfigurationChanging = false;

	// Keeps track of most recently inserted text (multi-character key) for
	// reverting
	// private CharSequence mEnteredText;

	private volatile boolean mPrefChanged = false;
	private long mHardMetaState = 0;

	private Method mGetRotation = null;
	private int mScreenLayout = 0;

	private WindowManager mWM = null;
	private boolean mFloating = false;
	private boolean mFloatingSticky = false;

	// For each word, a list of potential replacements, usually from voice.
	public Map<String, List<CharSequence>> mWordToSuggestions = new HashMap<String, List<CharSequence>>();

	private ArrayList<WordAlternatives> mWordHistory = new ArrayList<WordAlternatives>();
	InputController mInputController;

	private static final HashSet<Integer> PUNCTUATION_CHARACTERS = new HashSet<Integer>(
			16);
	static {
		String src = ".\n!?,:;@<>()[]{}";
		for (int i = 0; i < src.length(); ++i)
			PUNCTUATION_CHARACTERS.add((int) src.charAt(i));
	}

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_UPDATE_SUGGESTIONS:
				suggestController.updateSuggestions();
				break;
			case MSG_UPDATE_OLD_SUGGESTIONS:
				setOldSuggestions();
				break;
			case MSG_UPDATE_SHIFT_STATE:
				updateShiftKeyStateFromEditorInfo();
				break;
			case MSG_SEND_VOICE_TEXT:
				ArrayList<String> results = msg.getData().getStringArrayList(
						"results");
				voiceInputController.displayVoiceResult(results);
				break;
			case MSG_ENABLE_TRIAL_BUTTONS:
				AlertDialog alert = (AlertDialog) msg.obj;
				alert.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
				alert.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
				break;
			case MSG_VOICE_RESULTS:
				voiceInputController.handleVoiceResults();
				break;
			}
		}
	};

	// receive ringer mode changes to detect silent mode
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateRingerMode();
		}
	};

	private BroadcastReceiver mSpeechReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// Called when voice input has completed
			ArrayList<String> result = intent
					.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
			Message msg = mHandler.obtainMessage(MSG_SEND_VOICE_TEXT);
			msg.getData().putStringArrayList("results", result);
			mHandler.removeMessages(MSG_SEND_VOICE_TEXT);
			mHandler.sendMessageDelayed(msg, 100);
		}
	};
	private TrialPolicy trialPolicy;
	private KeyboardFactory keyboardFactory;

	public static boolean isProVersion() {
		return BuildConfig2.PRO;
	}

	@Override
	public void onInitializeInterface() {

		if (mDebug)
			Log.d("KBD", "onInitializeInterface");

		// Retrieve preferences
		SharedPreferences sp = mSharedPref;
		keyboardPreferences.initialize(sp);
		mVibrateOn = sp.getBoolean(KeyboardPreferences.PREF_VIBRATE_ON, false);
		if (mVibrateOn) {
			mVibrateDuration = (int)(new VibratorSettings(sp).getDurationMs());
			mSpaceAlert = sp.getBoolean(KeyboardPreferences.PREF_SPACE_ALERT, false);
		}
		mSoundOn = sp.getBoolean(KeyboardPreferences.PREF_SOUND_ON, false);
		mSwipeLeft = swipeGestures.getSwipeAction(sp.getString("swipe_left", "None"));
		mSwipeRight = swipeGestures.getSwipeAction(sp.getString("swipe_right", "Symbols"));
		mSwipeUp = swipeGestures.getSwipeAction(sp.getString("swipe_up", "Shift"));
		mSwipeDown = swipeGestures.getSwipeAction(sp.getString("swipe_down", "Close"));
		final String curLanguage = sp.getString("curLang", "EN");
		final String skin = sp.getString(KeyboardPreferences.PREF_SKIN, "Android");
		mSkinLoader.loadSkin(skin);
		mOpacity = (int)sp.getFloat(KeyboardPreferences.PREF_TRANSPARENCY, 50);
		final float volume = sp.getFloat(KeyboardPreferences.PREF_VOLUME, 100);
		mVolume = (float) Math.exp((volume - 100) / 20);
		mMicButton = sp.getBoolean(KeyboardPreferences.PREF_MIC_BUTTON, false);
		voiceInputController.mRestartVoice = sp.getBoolean(KeyboardPreferences.PREF_RESTART_VOICE, false);
		voiceInputController.mVoiceBest = sp.getBoolean(KeyboardPreferences.PREF_VOICE_BEST, false);
		voiceInputController.mLegacyVoice = sp.getBoolean(KeyboardPreferences.PREF_LEGACY_VOICE, false);
		mSmileyMode = Integer.parseInt(sp.getString(KeyboardPreferences.PREF_SMILEY_KEY, "0"));
		mDebug = sp.getBoolean(KeyboardPreferences.PREF_DEBUG, false);
		mShowTouchPoints = sp.getBoolean(KeyboardPreferences.PREF_TOUCH_POINTS, false);
		mShowPreview = sp.getBoolean(KeyboardPreferences.PREF_SHOW_PREVIEW, false);
		mPortraitMode = Integer.parseInt(sp.getString(KeyboardPreferences.PREF_PORTRAIT_MODE, "0"));
		mSpaceWhenPick = sp.getBoolean(KeyboardPreferences.PREF_SPACE_WHEN_PICK, false);
		mSwapPunctuationSpace = sp.getBoolean(KeyboardPreferences.PREF_SWAP_PUNCTUATION_SPACE,
				false);
		mUseSmartDictionary = sp.getBoolean(KeyboardPreferences.PREF_SMART_DICTIONARY, false);
		mAutoAddToUserDic = sp.getBoolean(KeyboardPreferences.PREF_LEARN_NEW_WORDS, false)
				&& mUseSmartDictionary;
		mSlidePopup = sp.getBoolean(KeyboardPreferences.PREF_SLIDE_POPUP, true);
		mSpacePreview = sp.getBoolean(KeyboardPreferences.PREF_SPACE_PREVIEW, false);
		mCandidateColor = sp.getInt(KeyboardPreferences.PREF_CANDIDATE_COLOR, 0);
		mSuggestPunctuation = sp.getBoolean(KeyboardPreferences.PREF_SUGGEST_PUNCTUATION, true);
		mLongpressDuration = (int)sp.getFloat(KeyboardPreferences.PREF_LONGPRESS_DURATION, 50);
		mMultitapInterval = (int)sp.getFloat(KeyboardPreferences.PREF_MULTITAP_INTERVAL, 80) * 10;
		mSwipeFactor = 100 - (int)sp.getFloat(KeyboardPreferences.PREF_SWIPE_FACTOR, 70);
		mEnterSendsSMS = sp.getBoolean(KeyboardPreferences.PREF_ENTER_SENDS_SMS, false);
		mNoAltPreview = sp.getBoolean(KeyboardPreferences.PREF_NO_ALT_PREVIEW, false);
		final String latinLayout = sp.getString(KeyboardPreferences.PREF_LATIN_LAYOUT, "");
		mRTLSuggestions = sp.getBoolean(KeyboardPreferences.PREF_RTL_SUGGESTIONS, true);
		mDomainKey = Integer.parseInt(sp.getString(KeyboardPreferences.PREF_DOMAIN_KEY, "0"));
		mDynamicResizing = sp.getBoolean(KeyboardPreferences.PREF_DYNAMIC_RESIZING, true);
		mDoubleSpacePeriod = sp.getBoolean(KeyboardPreferences.PREF_DOUBLE_SPACE_PERIOD, true);
		mDisableMT = sp.getBoolean(KeyboardPreferences.PREF_DISABLE_MT, false);
		mSmsMode = sp.getBoolean(KeyboardPreferences.PREF_SMS_MODE, false);
		mCompoundSuggestions = sp.getBoolean(KeyboardPreferences.PREF_COMPOUND_SUGGESTIONS, false);
		mAccentsPriority = sp.getBoolean(KeyboardPreferences.PREF_ACCENTS_PRIORITY, false);
		boolean hebrewAlt = sp.getBoolean(KeyboardPreferences.PREF_HEBREW_ALT, false);
		boolean czechFull = sp.getBoolean(KeyboardPreferences.PREF_CZECH_FULL, false);
		mNoLandscapeFullscreen = sp.getBoolean(KeyboardPreferences.PREF_NO_LANDSCAPE_FULLSCREEN,
				getResources().getBoolean(R.bool.default_no_landscape_fullscreen));
		mPortraitFullscreen = sp.getBoolean(KeyboardPreferences.PREF_PORTRAIT_FULLSCREEN, false);
		int arrowsStyle = Integer
				.parseInt(sp.getString(KeyboardPreferences.PREF_ARROWS_STYLE, "1"));
		boolean t9LengthPriority = sp.getBoolean(KeyboardPreferences.PREF_T9_LENGTH_PRIORITY, true);
		mSuggestNumbers = sp.getBoolean(KeyboardPreferences.PREF_SUGGEST_NUMBERS, false);
		mAskEnglishDic = sp.getBoolean(KeyboardPreferences.PREF_ASK_ENGLISH_DIC, true);
		mBottomPadding = (int)sp.getFloat(KeyboardPreferences.PREF_BOTTOM_PADDING, 0);
		mIgnoreHardKbd = sp.getBoolean(KeyboardPreferences.PREF_IGNORE_HARD_KBD, false);
		mHidePortrait = sp.getBoolean(KeyboardPreferences.PREF_HIDE_IN_PORTRAIT, false);
		mCursorVolume = sp.getBoolean(KeyboardPreferences.PREF_CURSOR_VOLUME, false);
		mCalibration = new CalibrationInfo(sp);
		mShowLangIcon = Integer.parseInt(sp.getString(KeyboardPreferences.PREF_LANG_ICON, "1"));
		if (suggestController.mCandidateView != null) {
			suggestController.mCandidateView.setCandidateColor(mCandidateColor);
			suggestController.mCandidateView.setRTLSuggestions(mRTLSuggestions);
		}
		mReCorrectionEnabled = sp.getBoolean(KeyboardPreferences.PREF_RECORRECTION_ENABLED,
				getResources().getBoolean(R.bool.default_recorrection_enabled));

		final boolean enableArrows = sp.getBoolean(KeyboardPreferences.PREF_ENABLE_ARROWS, false);
		final boolean t9NextKey = sp.getBoolean(KeyboardPreferences.PREF_T9_NEXT_KEY, true);
		final boolean t9Prediction = sp.getBoolean(KeyboardPreferences.PREF_T9_PREDICTION, true);
		final int arrowsMain = Integer.parseInt(sp.getString(KeyboardPreferences.PREF_ARROWS_MAIN,
				"0"));
		final int numbersTop = Integer.parseInt(sp.getString(KeyboardPreferences.PREF_NUMBERS_TOP,
				"0"));
		final boolean altCompact = sp.getBoolean(KeyboardPreferences.PREF_ALT_COMPACT, false);
		final boolean hideLangKey = sp.getBoolean(KeyboardPreferences.PREF_HIDE_LANG_KEY, false);
		mKeyboardSwitcher.setT9NextKey(t9NextKey);
		mKeyboardSwitcher.setT9Prediction(t9Prediction);
		mKeyboardSwitcher.setEnableArrowKeypad(enableArrows);
		mKeyboardSwitcher.setArrowsStyle(arrowsStyle);
		List<String> langList = getEnabledLanguages(curLanguage);
		mKeyboardSwitcher.setAvailLang(langList);
		keyboardFactory.setLatinLayout(latinLayout);
		mKeyboardSwitcher.setCurLang(curLanguage);
		mKeyboardSwitcher.setSmileyMode(mSmileyMode, false);
		mKeyboardSwitcher.setHebrewAlt(hebrewAlt);
		mKeyboardSwitcher.setCzechFull(czechFull);
		mKeyboardSwitcher.setArrowsMain(arrowsMain);
		mKeyboardSwitcher.setNumbersTop(numbersTop);
		mKeyboardSwitcher.setAltCompact(altCompact);
		mKeyboardSwitcher.setHideLangKey(hideLangKey);

		// Check if the device is a tablet (xlarge screen)
		boolean isTablet = (mScreenLayout & 4) != 0;

		int newKeyHeight = sp.getInt(KeyboardPreferences.PREF_KEY_HEIGHT, 50);
		int newKeyHeightLandscape = sp.getInt(KeyboardPreferences.PREF_KEY_HEIGHT_LANDSCAPE, 50);
		// Adjust height on tablets
		if (isTablet) {
			newKeyHeight = newKeyHeight * 3 / 2;
			newKeyHeightLandscape = newKeyHeightLandscape * 2;
		}

		final boolean hidePeriod = sp.getBoolean(KeyboardPreferences.PREF_HIDE_PERIOD, false);
		final boolean hideComma = sp.getBoolean(KeyboardPreferences.PREF_HIDE_COMMA, false);
		final float widthScale = sp.getFloat("keyboardWidth",1);
		// Recreate the keyboards if the key height has changed
		final boolean clearKbd = (newKeyHeight != GlobalResources.mKeyHeight)
				|| (newKeyHeightLandscape != GlobalResources.mKeyHeightLandscape)
				|| (hidePeriod != GlobalResources.mHidePeriod)
				|| (hideComma != GlobalResources.mHideComma)
				|| (arrowsMain != mArrowsMain)
				|| (widthScale != GlobalResources.mWidthScale);
		GlobalResources.mKeyHeight = newKeyHeight;
		GlobalResources.mKeyHeightLandscape = newKeyHeightLandscape;
		GlobalResources.mHidePeriod = hidePeriod;
		GlobalResources.mHideComma = hideComma;
		GlobalResources.mWidthScale = widthScale;
		GlobalResources.mFontScale = sp.getFloat("fontScale",1);
		GlobalResources.mIconScale = sp.getFloat("iconScale",1);
		GlobalResources.mBottomScale = sp.getFloat("bottomScale",1);
		mArrowsMain = arrowsMain;

		mKeyboardSwitcher.makeKeyboards(clearKbd);

		// For japanese use space bar in a special way
		mUseSpaceForNextWord = curLanguage.equals("JP")
				|| curLanguage.equals("ZH");

		// Disable always caps mode for thai and korean
		mAlwaysCaps = sp.getBoolean(KeyboardPreferences.PREF_ALWAYS_CAPS, false)
				&& mKeyboardSwitcher.mAutoCap;

		mShowSuggestions = sp.getBoolean(KeyboardPreferences.PREF_SHOW_SUGGESTIONS, true);
		mNoLandscapeSuggestions = sp.getBoolean(KeyboardPreferences.PREF_NO_LANDSCAPE_SUGGESTIONS,
				false);
		boolean contactsOn = sp.getBoolean(KeyboardPreferences.PREF_CONTACTS, false)
				& mShowSuggestions;
		final String langCode = curLanguage.substring(0, 2);
		mSuggest.loadDict(langCode);
		if (contactsOn && mContactsDictionary == null) {
			mContactsDictionary = new ContactsDictionary(SmartKeyboard.this);
		}
		mSuggest.setContactsDictionary(contactsOn ? mContactsDictionary : null);
		mSuggest.useSmartDictionary(mUseSmartDictionary);
		mSuggest.setT9LengthPriority(t9LengthPriority);
		final String soundStyle = sp.getString(KeyboardPreferences.PREF_SOUND_STYLE, "Android");
		mSoundStyle = 0;
		if (soundStyle.equals("iPhone")) {
			mSoundStyle = SOUND_IPHONE;
			if (mSoundID[SOUND_IPHONE] == -1) {
				mSoundID[SOUND_IPHONE] = mSoundPool.load(SmartKeyboard.this, com.dexilog.smartkeyboard.R.raw.tock, 1);
			}
		} else if (soundStyle.equals("Galaxy")) {
			mSoundStyle = SOUND_GALAXY;
			if (mSoundID[SOUND_GALAXY] == -1) {
				mSoundID[SOUND_GALAXY] = mSoundPool.load(SmartKeyboard.this, com.dexilog.smartkeyboard.R.raw.keypress,
						1);
			}
		} else if (soundStyle.equals("Win7")) {
			mSoundStyle = SOUND_WIN7;
			if (mSoundID[SOUND_WIN7] == -1) {
				mSoundID[SOUND_WIN7] = mSoundPool.load(SmartKeyboard.this, com.dexilog.smartkeyboard.R.raw.win7,
						1);
			}
		}
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (mKeyboardView != null) {
			setInputOptions(false);
		}
		initSuggestPuncList();
	}

	@Nonnull
	private List<String> getEnabledLanguages(String curLanguage) {
		List<String> langList = LangKeyPref.buildLangList(mSharedPref,
				getResources());
		// Make sure we have at least one language in the list
		if (langList.size() == 0) {
			langList.add(curLanguage);
		}
		return langList;
	}

	@Override
	public void onCreate() {

		if (DEBUG)
			Log.d("KBD", "onCreate");

		super.onCreate();

		final Resources res = super.getResources();
		mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		trialPolicy = new TrialPolicy(Calendar.getInstance(), mSharedPref);

		mCustomKeys = new CustomKeys(this, mSharedPref);
		mKeyboardSwitcher = createKeyboardSwitcher();
		final Configuration conf = res.getConfiguration();

		final boolean apostropheSeparator = mSharedPref.getBoolean(KeyboardPreferences.PREF_APOSTROPHE_SEPARATOR, false);
		mInputController = new InputController(this, apostropheSeparator);

		ExpandableDictionary autoDictionary = new AutoDictionary(this);
		mSuggest = new Suggest(new DictionaryFactoryImpl(this));
		suggestController = new SuggestController(this, mSuggest, mInputController, autoDictionary);
		suggestController.mAutoTextDictionary = new AutoTextDictionary(this);
		mSuggest.setCorrectionMode(mCorrectionMode);
		mSuggest.setContactsDictionary(mContactsDictionary);
		mSuggest.setAutoDictionary(autoDictionary);
		mSuggest.setAutoTextDictionary(suggestController.mAutoTextDictionary);
		mOrientation = conf.orientation;

		// Get method to retrieve rotation (Android 2.2 only)
		try {
			mGetRotation = Display.class.getMethod("getRotation",
					(Class[]) null);
		} catch (Exception e) {
			Log.i(TAG, "getRotation is not available");
		}
		// Retrieve screen size (Android 1.6 only)
		try {
			Field screenLayout = Configuration.class.getField("screenLayout");
			mScreenLayout = screenLayout.getInt(getResources()
					.getConfiguration());
		} catch (Exception e) {
			Log.i(TAG, "screenLayout is not available");
		}

		// Get number of portrait modes
		mNbPortraitModes = res.getStringArray(R.array.portrait_values).length;

		// register to receive ringer mode changes for silent mode
		IntentFilter filter = new IntentFilter(
				AudioManager.RINGER_MODE_CHANGED_ACTION);
		registerReceiver(mReceiver, filter);

		// Register speech receiver
		filter = new IntentFilter(ACTION_RECOGNITION_DONE);
		registerReceiver(mSpeechReceiver, filter);

		migrateLegacyPreferences();

		// Register preferences changes
		mSharedPref.registerOnSharedPreferenceChangeListener(SmartKeyboard.this);

		final boolean disableLauncher = mSharedPref.getBoolean(
				KeyboardPreferences.PREF_DISABLE_LAUNCHER, false);
		// Set state of main activity
		final int launcherState = disableLauncher ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
				: PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
		getPackageManager().setComponentEnabledSetting(
				new ComponentName(SmartKeyboard.this, SetupActivity.class), launcherState,
				PackageManager.DONT_KILL_APP);

		mSoundPool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 0);
		mSkinLoader = new SkinLoader(SmartKeyboard.this, mOrientation);
		mWM = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
	}

	protected void migrateLegacyPreferences() {
		// Hack to handle iphone hebrew layout, which has been removed
		if (mSharedPref.getString("curLang", "EN").equals("HE_IPHONE")) {
			SharedPreferences.Editor editor = mSharedPref.edit();
			editor.putString("curLang", "HE");
			editor.commit();
		}
		if (mSharedPref.getBoolean("langHE_IPHONE", false)) {
			SharedPreferences.Editor editor = mSharedPref.edit();
			editor.putBoolean("langHE", true);
			editor.remove("langHE_IPHONE");
			editor.commit();
		}
		// Hack to migrate from Korean-specific option
		if (!mSharedPref.getBoolean(KeyboardPreferences.PREF_KOREAN_NUMBERS_PRIORITY, true)) {
			SharedPreferences.Editor editor = mSharedPref.edit();
			editor.putBoolean(KeyboardPreferences.PREF_ACCENTS_PRIORITY, true);
			editor.remove(KeyboardPreferences.PREF_KOREAN_NUMBERS_PRIORITY);
			editor.commit();
		}
		// Migrate persistent domain option
		if (mSharedPref.getBoolean(KeyboardPreferences.PREF_PERSISTENT_DOMAIN_KEY, false)) {
			mSharedPref.edit()
			.putString(KeyboardPreferences.PREF_DOMAIN_KEY, "2")
			.commit();
		}
	}

	@Override
	public void onDestroy() {
		// mUserDictionary.close();
		if (mContactsDictionary != null)
			mContactsDictionary.close();
		unregisterReceiver(mSpeechReceiver);
		unregisterReceiver(mReceiver);
		super.onDestroy();
	}

	@Override
	public void onConfigurationChanged(Configuration conf) {

		if (DEBUG)
			Log.d("KBD", "onConfigurationChanged");

		// Reset the pressed state of the current key
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (mKeyboardView != null) {
			mKeyboardView.resetKeyState();
		}

		// If orientation changed while predicting, commit the change
		if (conf.orientation != mOrientation) {
			suggestController.commitTyped(getCurrentInputConnection());
			mOrientation = conf.orientation;
			mSkinLoader.setOrientation(mOrientation);
		}

		if (mKeyboardSwitcher == null) {
			mKeyboardSwitcher = createKeyboardSwitcher();
		}
		// Reload prediction settings to handle no_landscape_suggestions
		loadSettings();

		super.onConfigurationChanged(conf);
	}

	@Nonnull
	private KeyboardSwitcher createKeyboardSwitcher() {
		boolean isPortraitMode = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
		// Compute list of latin layouts
		final String[] latinLayouts = getResources().getStringArray(R.array.latin_layouts);
		keyboardFactory = new KeyboardFactory(latinLayouts, isPortraitMode);
		return new KeyboardSwitcher(this, keyboardFactory, keyboardPreferences);
	}


	private KeyboardView mLastKeyboard;
	private int mDisplayWidth, mDisplayHeight;
	private int mKeyboardWidth;
	private void setFloating(boolean floating)
	{
		if( mLastKeyboard == null )
			return;
		detachKeyboardViews();

		if( floating )
		{
			mLastKeyboard.setWindowMover(new KeyboardView.WindowMover()
			{
				@Override
				public void move(int dx, int dy)
				{
					WindowManager.LayoutParams params = (WindowManager.LayoutParams)mLastKeyboard.getLayoutParams();
					params.x += dx;
					params.y += dy;
					if(params.x < 0)
						params.x = 0;
					if(params.y < 0)
						params.y = 0;
					if(params.x + mKeyboardWidth > mDisplayWidth)
						params.x = mDisplayWidth - params.width;

					if(params.y + mLastKeyboard.getHeight() > mDisplayHeight)
						params.y = mDisplayHeight - mLastKeyboard.getHeight();
					mWM.updateViewLayout(mLastKeyboard,params);
				}
			}
			);
			WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_SYSTEM_ERROR, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.OPAQUE);
			params.gravity = Gravity.LEFT | Gravity.TOP;
			params.x = mFloatingX;
			params.y = mFloatingY;
			params.width = mKeyboardWidth;     
			//params.alpha = mOpacity;
			if(mOpacity != 50)
				params.format = PixelFormat.TRANSPARENT;
			params.height = mDisplayHeight;
			mWM.addView(mLastKeyboard, params);
		}
		else if(mFloating)
		{
			
			//onInitializeInterface();
			hideWindow();
			if( mFloatingSticky )
				return;
			//showWindow(true);
		}
		else
		{
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
			params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
			((LinearLayout)mInputView).addView(mLastKeyboard, params);
			mLastKeyboard.setWindowMover(null);
		}
		mFloating = floating;
	}
	public void toggleFloating(boolean sticky)
	{
		if( sticky )
			mFloatingSticky = !mFloatingSticky;
		else
			setFloating(!mFloating);

		((com.dexilog.smartkeyboard.keyboard.Keyboard) mKeyboardSwitcher.getKeyboardView().getKeyboard())
			.setFloatingSticky(mFloating,mFloatingSticky);
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		mKeyboardView.setShifted(mCapsLock || mKeyboardView.isShifted(),
								 true);
				
								 
								 
	}
	int mFloatingX, mFloatingY;
	private void detachKeyboardViews()
	{
		if( mLastKeyboard == null )
			return;
		try
		{
			((LinearLayout)mInputView).removeView(mLastKeyboard);
		}
		catch(Exception e){}
		try
		{
			WindowManager.LayoutParams params = (WindowManager.LayoutParams)mLastKeyboard.getLayoutParams();
			mFloatingX = params.x;
			mFloatingY = params.y;
		}
		catch(Exception e){}
		try
		{
			mWM.removeView(mLastKeyboard);
		}
		catch(Exception e){}
	}

	@Override
	public View onCreateInputView() {
		if (mDebug)
			Log.d(TAG, "onCreateInputView");

		mInputView = getLayoutInflater().inflate(R.layout.input, null);

		KeyboardView mainKeyboardView = new KeyboardView(getLayoutInflater().getContext(),null);//(KeyboardView) mInputView.findViewById(R.id.keyboard);

		DisplayMetrics dm = getResources().getDisplayMetrics();
		mDisplayWidth = dm.widthPixels;
		mDisplayHeight = dm.heightPixels;
		mKeyboardWidth = (int)(mDisplayWidth * GlobalResources.mWidthScale);
		detachKeyboardViews();
		mLastKeyboard = mainKeyboardView;
		setFloating(false);
		
		mKeyboardSwitcher.setInputView(mainKeyboardView);
		mainKeyboardView.setCustomKeys(mCustomKeys);
		setInputOptions(false);
		mainKeyboardView.setOnKeyboardActionListener(onKeyboardActionListener);
		mKeyboardSwitcher.setInputView(mainKeyboardView);
		mKeyboardSwitcher.makeKeyboards(false);

		// Create the candidate view now...
		mCandidateViewContainer = (CandidateViewContainer)
				mInputView.findViewById(R.id.candidates_container);
		mCandidateViewContainer.initViews();
		suggestController.mCandidateView = (CandidateView) mCandidateViewContainer
				.findViewById(R.id.candidates);
		suggestController.mCandidateView.setService(this);
		suggestController.mCandidateView.setCandidateColor(mCandidateColor);
		suggestController.mCandidateView.setRTLSuggestions(mRTLSuggestions);
		SkinInfo skin = mSkinLoader.getCurrentSkin();
		mCandidateViewContainer.applySkin(skin);
		setCandidatesViewShown(true);

		return mInputView;
	}

	public int getRotation() {
		int result = 0;
		if (mGetRotation != null) {
			// Detect rotation (Android 2.2 only)
			WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
			try {
				result = (Integer) mGetRotation.invoke(wm.getDefaultDisplay());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	private void setInputOptions(boolean restart) {
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		mKeyboardView.setPreviewEnabled(mShowPreview);
		SkinInfo skin = mSkinLoader.getCurrentSkin();
		if (mCandidateViewContainer != null) {
			mCandidateViewContainer.applySkin(skin);
		}
		mKeyboardView.applySkin(skin);
		mKeyboardView.setTransparency(mOpacity);
		mKeyboardView.setAlwaysCaps(mAlwaysCaps);
		mKeyboardView.setShowTouchpoints(mShowTouchPoints);
		mKeyboardView.setSlidePopup(mSlidePopup);
		mKeyboardView.setSpacePreview(mSpacePreview);
		mKeyboardView.setLongpressDuration(mLongpressDuration);
		mKeyboardView.setMultitapInterval(mMultitapInterval);
		mKeyboardView.setSwipeFactor(mSwipeFactor);
		mKeyboardView.setNoAltPreview(mNoAltPreview);
		mKeyboardView.disableMT(mDisableMT);
		mKeyboardView.setCalibration(mCalibration);
		mKeyboardView.setAccentsPriority(mAccentsPriority);
		mKeyboardSwitcher.setMicButton(mMicButton);
		int padding = 0;
		if (mOrientation == Configuration.ORIENTATION_PORTRAIT) {
			mKeyboardSwitcher.setPortraitMode(true, mPortraitMode, restart);
			padding = mBottomPadding;
		} else {
			mKeyboardSwitcher.setPortraitMode(false,
					KeyboardSwitcher.PORTRAIT_NORMAL, restart);
		}
		mKeyboardView.setPadding(0, 0, 0, padding);
	}

	@Override
	public void setCandidatesView(final View view) {
		// To ensure that CandidatesView will never be set.
	}

	@Override
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		if (mDebug)
			Log.d(TAG, "onStartInput: restarting="
					+ Boolean.toString(restarting));

		setLangStatus();

		// If restarting and using hard keyboard, don't do anything
		if (restarting) {
			return;
		}

		TextEntryState.newSession(SmartKeyboard.this);

		boolean disableAutoCorrect = false;
		mPredictionOn = false;
		mCompletionOn = false;
		mCompletions = null;
		mCapsLock = false;
		mHardMetaState = 0;

		loadSettings();

		switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
		case EditorInfo.TYPE_CLASS_NUMBER:
		case EditorInfo.TYPE_CLASS_TEXT:

			mPredictionOn = true;
			int variation = attribute.inputType
					& EditorInfo.TYPE_MASK_VARIATION;

			if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME) {
				disableAutoCorrect = true;
				mAutoSpace = false;
			} else {
				mAutoSpace = true;
			}
			if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
				mPredictionOn = mAlwaysSuggest;
			} else if (variation == EditorInfo.TYPE_TEXT_VARIATION_URI) {
				mPredictionOn = mAlwaysSuggest;
				disableAutoCorrect = true;
			} else if (variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {

			} else if (variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
				mPredictionOn = mAlwaysSuggest;
			} else if (variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
				// If it's a browser edit field and auto correct is not ON
				// explicitly, then
				// disable auto correction, but keep suggestions on.
				if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
					disableAutoCorrect = true;
				}
			}

			// If NO_SUGGESTIONS is set, don't do prediction.
			if (!mAlwaysSuggest && (attribute.inputType & 0x00080000) != 0) {
				mPredictionOn = false;
				disableAutoCorrect = true;
			}
			// If it's not multiline and the autoCorrect flag is not set, then
			// don't correct
			if (!mAlwaysSuggest
					&& (attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0
					&& (attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
				disableAutoCorrect = true;
			}
			if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
				mPredictionOn = mAlwaysSuggest;
				mCompletionOn = isFullscreenMode();
			}
			// Make sure that passwords are not displayed in candidate view
			if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
				mPredictionOn = false;
			}
			break;
		case 15: // Samsung email client
			mPredictionOn = true;
			break;
		}
		WordComposer word = mInputController.getCurrentWordComposer();
		word.reset();
		suggestController.mT9Suggestion = null;
		suggestController.mCandidateSelected = false;
		suggestController.mConvertedComposing = word.getConvertedWord();
		mInputController.setPredicting(false);
		onKeyboardActionListener.mDeleteCount = 0;
		voiceInputController.mVoiceInputHighlighted = false;
		mWordToSuggestions.clear();
		// Make sure to reset this dangerous flag!
		suggestController.mWaitingForSuggestions = false;
		setCandidatesViewShown(false);
		if (suggestController.mCandidateView != null)
			suggestController.mCandidateView.setSuggestions(null, false, false, false);
		// Override auto correct
		if (disableAutoCorrect) {
			mAutoCorrectOn = false;
			if (mCorrectionMode == Suggest.CORRECTION_FULL) {
				mCorrectionMode = Suggest.CORRECTION_BASIC;
			}
		}
		if (mSuggest != null) {
			mSuggest.setCorrectionMode(mCorrectionMode);
			// Try to reload the main dictionary if needed
			mSuggest.tryReloadDic();
		}

		// Enable double space for period even if predictions are turned off
		mEnableDoubleSpace = mPredictionOn;
		mPredictionOn = mPredictionOn
				&& (mCorrectionMode > 0 || isSuggestionOn());

		// Reset text translator
		mCurTranslator = mKeyboardSwitcher.getConverter();
	}

	@Override
	public void onStartInputView(EditorInfo attribute, boolean restarting) {

		if (mDebug)
			Log.d(TAG, "onStartInputView inputType=" + Integer.toString(attribute.inputType));

		// Is keyboard visible?
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (mKeyboardView != null) {
			final boolean isVisible = isKeyboardVisible();
			mKeyboardView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
		}
		detachKeyboardViews();
		setFloating(mFloating);

		// Always disable composing text in CoPilot
		if (mDebug)
			Log.d(TAG, "Called from package " + attribute.packageName);
		boolean disableComposing = attribute.packageName.startsWith("com.alk.copilot")
				|| attribute.packageName.startsWith("com.starfinanz.smob.android.sbanking");

		// Hack for Gmail on ICE
		boolean isGmailHack = (android.os.Build.VERSION.SDK_INT >= 14 &&
				attribute.inputType == 720929 &&
				attribute.packageName.startsWith("com.google.android.gm"));

		mDisableComposing = disableComposing || isGmailHack;

		final boolean alwaysSuggest = mAlwaysSuggest
				&& !attribute.packageName.equals("com.swanz.epistle")
				&& !isGmailHack;


		voiceInputController.voiceStartInputView();

		// In landscape mode, this method gets called without the input view
		// being created.
		if (mKeyboardView == null) {
			return;
		}

		mKeyboardSwitcher.makeKeyboards(false);

		TextEntryState.newSession(SmartKeyboard.this);

		boolean disableAutoCorrect = false;
		mPredictionOn = false;
		mCompletionOn = false;
		mCompletions = null;
		mCapsLock = false;
		mIMmode = false;
		// mEnteredText = null;

		loadSettings();

		switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
		case EditorInfo.TYPE_CLASS_NUMBER:
		case EditorInfo.TYPE_CLASS_DATETIME:
		case EditorInfo.TYPE_CLASS_PHONE:
			mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_NUMBERS,
					attribute.imeOptions, KeyboardSwitcher.KBD_NORMAL);
			break;
		case EditorInfo.TYPE_CLASS_TEXT:
			mKeyboardSwitcher.setKeyboardMode(
					getKeyboardMode(KeyboardSwitcher.MODE_TEXT),
					attribute.imeOptions, KeyboardSwitcher.KBD_NORMAL);
			// startPrediction();
			mPredictionOn = true;
			int variation = attribute.inputType
					& EditorInfo.TYPE_MASK_VARIATION;

			if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME) {
				mAutoSpace = false;
				disableAutoCorrect = true;
			} else {
				mAutoSpace = true;
			}
			if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
				mPredictionOn = alwaysSuggest;
				mKeyboardSwitcher.setKeyboardMode(
						getKeyboardMode(KeyboardSwitcher.MODE_TEXT), // MODE_EMAIL
						attribute.imeOptions, KeyboardSwitcher.KBD_NORMAL);
			} else if (variation == EditorInfo.TYPE_TEXT_VARIATION_URI) {
				mPredictionOn = alwaysSuggest;
				disableAutoCorrect = true;
				int mode = (mDomainKey != 1) ? KeyboardSwitcher.MODE_URL : KeyboardSwitcher.MODE_TEXT;
				mKeyboardSwitcher.setKeyboardMode(mode,
						attribute.imeOptions, KeyboardSwitcher.KBD_NORMAL);
			} else if (variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
				mIMmode = true;
				mKeyboardSwitcher.setKeyboardMode(
						getKeyboardMode(KeyboardSwitcher.MODE_IM),
						attribute.imeOptions, KeyboardSwitcher.KBD_NORMAL);
			} else if (variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
				mPredictionOn = alwaysSuggest;
			} else if (variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
				// If it's a browser edit field and auto correct is not ON
				// explicitly, then
				// disable auto correction, but keep suggestions on.
				if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
					disableAutoCorrect = true;
				}
			}

			// If NO_SUGGESTIONS is set, don't do prediction.
			if ((attribute.inputType & 0x00080000) != 0) {
				mPredictionOn = mAlwaysSuggest;
				disableAutoCorrect = true;
			}
			// If it's not multiline and the autoCorrect flag is not set, then
			// don't correct
			if (!alwaysSuggest
					&& (attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0
					&& (attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
				disableAutoCorrect = true;
			}
			if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
				final boolean isFullscreen = isFullscreenMode();
				// Don't suggest in fullscreen to avoid conflict with
				// completions
				mPredictionOn = alwaysSuggest && !isFullscreen;
				mCompletionOn = isFullscreen;
			}
			// Make sure that passwords are not displayed in candidate view
			if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
					|| variation == 224 /* TYPE_TEXT_VARIATION_WEB_PASSWORD */ ) {
				mPredictionOn = false;
			}

			updateShiftKeyState(attribute);
			break;
		case 15:
			// Samsung galaxy Mail client
			mKeyboardSwitcher.setKeyboardMode(
					getKeyboardMode(KeyboardSwitcher.MODE_TEXT),
					attribute.imeOptions, KeyboardSwitcher.KBD_NORMAL);
			updateShiftKeyState(attribute);
			mPredictionOn = true;
			break;
		default:
			mKeyboardSwitcher.setKeyboardMode(
					getKeyboardMode(KeyboardSwitcher.MODE_TEXT),
					attribute.imeOptions, KeyboardSwitcher.KBD_NORMAL);
			updateShiftKeyState(attribute);
		}
		mKeyboardView.closing();
		WordComposer word = mInputController.getCurrentWordComposer();
		word.reset();
		suggestController.mT9Suggestion = null;
		suggestController.mConvertedComposing = word.getConvertedWord();

		mInputController.setPredicting(false);
		onKeyboardActionListener.mDeleteCount = 0;
		suggestController.setNextSuggestions();

		// Override auto correct
		if (disableAutoCorrect) {
			mAutoCorrectOn = false;
			if (mCorrectionMode == Suggest.CORRECTION_FULL) {
				mCorrectionMode = Suggest.CORRECTION_BASIC;
			}
		}
		mKeyboardView.setProximityCorrectionEnabled(true);
		if (mSuggest != null) {
			mSuggest.setCorrectionMode(mCorrectionMode);
		}
		mPredictionOn = mPredictionOn
				&& (mCorrectionMode > 0 || isSuggestionOn());
		mKeyboardSwitcher.setPrediction(mPredictionOn);

		setCandidatesViewShown(isCandidateStripVisible() || mCompletionOn);

		// If we just entered a text field, maybe it has some old text that
		// requires correction
		checkReCorrectionOnStart();
	}

	private void checkReCorrectionOnStart() {
		if (mReCorrectionEnabled && isPredictionOn()) {
			// First get the cursor position. This is required by
			// setOldSuggestions(), so that
			// it can pass the correct range to setComposingRegion(). At this
			// point, we don't
			// have valid values for lastSelectionStart/Stop because
			// onUpdateSelection() has
			// not been called yet.
			InputConnection ic = getCurrentInputConnection();
			if (ic == null)
				return;
			ExtractedTextRequest etr = new ExtractedTextRequest();
			etr.token = 0; // anything is fine here
			ExtractedText et = ic.getExtractedText(etr, 0);
			if (et == null)
				return;

			mInputController.setLastSelectionStart(et.startOffset + et.selectionStart);
			mInputController.setLastSelectionEnd(et.startOffset + et.selectionEnd);

			// Then look for possible corrections in a delayed fashion
			if (!TextUtils.isEmpty(et.text) && mInputController.isCursorTouchingWord()) {
				postUpdateOldSuggestions();
			}
		}
	}

	private int getKeyboardMode(int mode) {
		return (mDomainKey == 2) ? KeyboardSwitcher.MODE_URL : mode;
	}

	@Override
	public void onFinishInput() {

		if (mDebug)
			Log.d(TAG, "onFinishInput");
		super.onFinishInput();

		voiceInputController.cancelVoiceInput(this);
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (mKeyboardView != null) {
			mKeyboardView.closing();
		}
	}

	@Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        // Remove penging messages related to update suggestions
        mHandler.removeMessages(MSG_UPDATE_SUGGESTIONS);
        mHandler.removeMessages(MSG_UPDATE_OLD_SUGGESTIONS);
		if(!mFloatingSticky)
			detachKeyboardViews();
    }

	@Override
	public void onUpdateExtractedText(int token, ExtractedText text) {
		super.onUpdateExtractedText(token, text);
	}

	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd,
			int newSelStart, int newSelEnd, int candidatesStart,
			int candidatesEnd) {

		if (DEBUG)
			Log.d(TAG, "onUpdateSelection " + Integer.toString(oldSelStart) + " "
					+ Integer.toString(oldSelEnd) + " "
					+ Integer.toString(newSelStart) + " "
					+ Integer.toString(newSelEnd) + " "
					+ Integer.toString(candidatesStart) + " "
					+ Integer.toString(candidatesEnd) + " "
					+ Integer.toString(mInputController.getLastSelectionStart()) + " "
					+ Boolean.toString(mInputController.getPredicting()));

		super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
				candidatesStart, candidatesEnd);

		// If the current selection in the text view changes, we should
		// clear whatever candidate text we have.
		WordComposer word = mInputController.getCurrentWordComposer();
		if ((((word.size() > 0 && mInputController.getPredicting()) || voiceInputController.mVoiceInputHighlighted)
				&& (newSelStart != candidatesEnd || newSelEnd != candidatesEnd))
			    && ((mInputController.getLastSelectionStart() != newSelStart && !suggestController.mWaitingForSuggestions)
			    /* hack to handle backspace in WhatsApp */
				   || newSelStart < oldSelStart)) {
			if (DEBUG) Log.d(TAG, "reset word");
			word.reset();
			suggestController.mT9Suggestion = null;
			suggestController.mCandidateSelected = false;
			mInputController.setPredicting(false);
			postUpdateSuggestions();
			TextEntryState.reset();
			InputConnection ic = getCurrentInputConnection();
			if (ic != null) {
				ic.finishComposingText();
			}
			voiceInputController.mVoiceInputHighlighted = false;
		} else if (!mInputController.getPredicting() && !suggestController.mJustAccepted) {
			switch (TextEntryState.getState()) {
			case ACCEPTED_DEFAULT:
				TextEntryState.reset();
				// fall through
			case SPACE_AFTER_PICKED:
				// mJustAddedAutoSpace = false; // The user moved the cursor.
				break;
			}
		}
		final boolean wasJustAccepted = suggestController.mJustAccepted;
		suggestController.mJustAccepted = false;
		postUpdateShiftKeyState();

		// Make a note of the cursor position
		mInputController.setLastSelectionStart(newSelStart);
		mInputController.setLastSelectionEnd(newSelEnd);

		// Don't show recorrections if a word was picked manually and "space after picking"
		// is disabled, unless this is the last word
		if (mReCorrectionEnabled) {
			recorrectWordUnderCursor(oldSelStart, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
		}
	}

	private void recorrectWordUnderCursor(int oldSelStart, int newSelStart, int newSelEnd,
										  int candidatesStart, int candidatesEnd) {
		if (DEBUG) Log.d(TAG, "Check recorrect");
		// Don't look for corrections if the keyboard is not visible
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (mKeyboardView != null && mKeyboardView.isShown()) {
            // Check if we should go in or out of correction mode.
            if (isPredictionOn()
                    && suggestController.mJustRevertedSeparator == null
                    && (candidatesStart == candidatesEnd
                            || newSelStart != oldSelStart || TextEntryState
                            .isCorrecting())
                    && (newSelStart < newSelEnd - 1 || (!mInputController.getPredicting()))
                    && !voiceInputController.mVoiceInputHighlighted) {
                if ((mInputController.isCursorTouchingWord() || mInputController.getLastSelectionStart() < mInputController.getLastSelectionEnd())
                        && !mInputController.isCursorAtEnd()) {
                    if (DEBUG) Log.d(TAG, "postUpdateOldSuggestions");
                    postUpdateOldSuggestions();
                } else {
                    if (DEBUG) Log.d(TAG, "abortCorrection");
                    abortCorrection(false);
					// Show the punctuation suggestions list if the current one is not
					// and if not showing "Touch again to save".
					if (suggestController.mCandidateView != null
						&& !mSuggestPuncList.equals(suggestController.mCandidateView.getSuggestions())) {
						suggestController.setNextSuggestions();
					}
                }
            }
        }
	}

	@Override
	public void hideWindow() {

		if (mDebug) {
			Exception e = new Exception();
			Log.d(TAG, "hideWindow " + Log.getStackTraceString(e));
		}

		if (TRACE)
			Debug.stopMethodTracing();
		if (mOptionsDialog != null && mOptionsDialog.isShowing()) {
			mOptionsDialog.dismiss();
			mOptionsDialog = null;
		}
		if (!mConfigurationChanging) {
			voiceInputController.voiceHideWindow();
		}
        mWordToSuggestions.clear();
        mWordHistory.clear();
        hideStatusIcon();
		super.hideWindow();
		TextEntryState.endSession();
	}

	@Override
	public void onDisplayCompletions(CompletionInfo[] completions) {
		suggestController.onDisplayCompletions(completions);
	}

	@Override
	public boolean onEvaluateFullscreenMode() {

		Configuration config = getResources().getConfiguration();
		if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
			return mPortraitFullscreen;
		}

		// Now we are in landscape

		if (mNoLandscapeFullscreen) {
			return false;
		}

		// Workaround to HTC mail issue
		if (getCurrentInputEditorInfo().packageName.equals(HTC_MAIL)) {
			return true;
		}

		return super.onEvaluateFullscreenMode();
	}

	@SuppressLint("MissingSuperCall")
	@Override
	public boolean onEvaluateInputViewShown() {
		/*
		if (mIgnoreHardKbd && (mOrientation == Configuration.ORIENTATION_LANDSCAPE)) {
			// For Droid Pro ignore presence of the hard keyboard in landscape
			return true;
		} else if (mHidePortrait && (mOrientation == Configuration.ORIENTATION_PORTRAIT)) {
			return false;
		} else {
			return super.onEvaluateInputViewShown();
		}*/
		return true;
	}

	public boolean isKeyboardVisible() {
		if (mIgnoreHardKbd && (mOrientation == Configuration.ORIENTATION_LANDSCAPE)) {
			// For Droid Pro ignore presence of the hard keyboard in landscape
			return true;
		} else if (mHidePortrait && (mOrientation == Configuration.ORIENTATION_PORTRAIT)) {
			return false;
		} else {
			return super.onEvaluateInputViewShown();
		}
	}

	@Override
	public void setCandidatesViewShown(boolean shown) {
		if (mDebug)
			Log.d(TAG, "setCandidatesViewShown " + Boolean.toString(shown));
		// TODO: Remove this if we support candidates with hard keyboard
		// if (onEvaluateInputViewShown()) {
		if (mCandidateViewContainer != null) {
			mCandidateViewContainer.setVisibility(shown ? View.VISIBLE : View.GONE);
		}
		// }
	}

	@Override
	public void onComputeInsets(InputMethodService.Insets outInsets) {
		super.onComputeInsets(outInsets);
		if (!isFullscreenMode()) {
			outInsets.contentTopInsets = outInsets.visibleTopInsets;
		}
	}

	private boolean rotation90() {
		// Check if phone is in normal landscape rotation
		return (mOrientation != Configuration.ORIENTATION_PORTRAIT)
				&& (getRotation() != 3); // ROTATION_270;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		// Handle volume keys
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (mCursorVolume && mKeyboardView != null && mKeyboardView.isShown()) {
			switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				sendDownUpKeyEvents(rotation90() ? KeyEvent.KEYCODE_DPAD_RIGHT
						: KeyEvent.KEYCODE_DPAD_LEFT);
				return true;
			case KeyEvent.KEYCODE_VOLUME_UP:
				sendDownUpKeyEvents(rotation90() ? KeyEvent.KEYCODE_DPAD_LEFT
						: KeyEvent.KEYCODE_DPAD_RIGHT);
				return true;
			default:
				break;
			}
		}

		if (mSuggestHardKbd) {
			Boolean handledKey = onHardKeyDown(keyCode, event);
			if (handledKey != null) return handledKey;
		} else {
			switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
				if (event.getRepeatCount() == 0 && mKeyboardView != null) {
					if (mKeyboardView.handleBack()) {
						return true;
					}
				}
				break;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Nullable
	private Boolean onHardKeyDown(int keyCode, KeyEvent event) {
		// Handle special keys
		if (bypassKey(keyCode)) {
            return super.onKeyDown(keyCode, event);
        }

		switch (keyCode) {

        // Handle state keys
        case KeyEvent.KEYCODE_ALT_LEFT:
        case KeyEvent.KEYCODE_ALT_RIGHT:
        case KeyEvent.KEYCODE_SHIFT_LEFT:
        case KeyEvent.KEYCODE_SHIFT_RIGHT:
        case KeyEvent.KEYCODE_SYM:
            mHardMetaState = MyMetaKeyKeyListener.handleKeyDown(
                    mHardMetaState, keyCode, event);
            return super.onKeyDown(keyCode, event);

        case KeyEvent.KEYCODE_DEL: {
            final int del = Keyboard.KEYCODE_DELETE;
            final int codes[] = { del };
            onKeyboardActionListener.onKey(del, codes, true, false);
            return true;
        }

        case 99: // Language key on the HTC Desire Z
        {
            switchLang(-1);
            return true;
        }

        case KeyEvent.KEYCODE_SPACE:
            if (event.isShiftPressed()) {
                // Switch language
                mHardMetaState = 0;
                setHardMetaState();
                switchLang(-1);
                return true;
            } else if (MetaKeyKeyListener.getMetaState(mHardMetaState,
                    MyMetaKeyKeyListener.META_ALT_ON) > 0) {
                // Handle alt+space
                return super.onKeyDown(keyCode, event);
            }

            // BE CAREFUL NOT TO INSERT A NEW CASE HERE (to handle space key
            // correctly)
        default: {
            final HardKeyboardTranslator translator = mKeyboardSwitcher
                    .getHardKeyboardTranslator();
            final int code = translator.translateKey(keyCode,
                    mHardMetaState);

            if (mDebug)
                Log.d(TAG, "keyCode: " + Integer.toString(keyCode));
            if (DEBUG)
                Log.d(TAG, "onKeyDown " + event.toString() + " code="
                        + Integer.toString(code));

            if (code != 0) {
                final int codes[] = { code };
                onKeyboardActionListener.onKey(code, codes, true, translator.isMultiTap());
                mHardMetaState = MyMetaKeyKeyListener
                        .adjustMetaAfterKeypress(mHardMetaState);

                // If enter is pressed, let the caller handle it
                if (code == KEYCODE_ENTER) {
                    return false;
                } else {
                    setHardMetaState();
                    return true;
                }
            }
        }
        }
		return null;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {

		// Handle volume keys
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (mCursorVolume && mKeyboardView != null && mKeyboardView.isShown()) {
			switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
				return true;
			default:
				break;
			}
		}

		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_DOWN:
		case KeyEvent.KEYCODE_DPAD_UP:
		case KeyEvent.KEYCODE_DPAD_LEFT:
		case KeyEvent.KEYCODE_DPAD_RIGHT:

			// Enable shift key and DPAD to do selections
			if (mKeyboardView != null && mKeyboardView.isShown()
					&& mKeyboardView.isShifted()) {
				event = new KeyEvent(event.getDownTime(), event.getEventTime(),
						event.getAction(), event.getKeyCode(), event
								.getRepeatCount(), event.getDeviceId(), event
								.getScanCode(), KeyEvent.META_SHIFT_LEFT_ON
								| KeyEvent.META_SHIFT_ON);
				InputConnection ic = getCurrentInputConnection();
				if (ic != null)
					ic.sendKeyEvent(event);
				return true;
			}
			break;

		// Handle state keys
		case KeyEvent.KEYCODE_ALT_LEFT:
		case KeyEvent.KEYCODE_ALT_RIGHT:
		case KeyEvent.KEYCODE_SHIFT_LEFT:
		case KeyEvent.KEYCODE_SHIFT_RIGHT:
		case KeyEvent.KEYCODE_SYM:
			if (mSuggestHardKbd) {
				mHardMetaState = MyMetaKeyKeyListener.handleKeyUp(
						mHardMetaState, keyCode, event);
			}
			return super.onKeyDown(keyCode, event);
		}

		final int code = event.getUnicodeChar();
		if (mSuggestHardKbd) {
			// Log.d("KBD", "onKeyUp " + Character.toString((char)code));

			// Handle special keys
			if (bypassKey(keyCode)) {
				return super.onKeyUp(keyCode, event);
			}

			final HardKeyboardTranslator translator = mKeyboardSwitcher.getHardKeyboardTranslator();
			translator.keyUp();

			if (code == KEYCODE_ENTER) {
				return false;
			} else if (code != 0) {
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	private boolean bypassKey(int keyCode) {
		if (keyCode == 84) {
			return true;
		}
		if (keyCode >= 96 && keyCode <=110) {
			return true;
		}
		return false;
	}

	private void setHardMetaState() {
		// Reset the meta state of the input connection
		InputConnection ic = getCurrentInputConnection();
		if (ic != null) {
			int clearStatesFlags = 0;
			if (MyMetaKeyKeyListener.getMetaState(mHardMetaState,
					MyMetaKeyKeyListener.META_ALT_ON) == 0)
				clearStatesFlags += KeyEvent.META_ALT_ON;
			if (MyMetaKeyKeyListener.getMetaState(mHardMetaState,
					MyMetaKeyKeyListener.META_SHIFT_ON) == 0)
				clearStatesFlags += KeyEvent.META_SHIFT_ON;
			if (MyMetaKeyKeyListener.getMetaState(mHardMetaState,
					MyMetaKeyKeyListener.META_SYM_ON) == 0)
				clearStatesFlags += KeyEvent.META_SYM_ON;
			ic.clearMetaKeyStates(clearStatesFlags);
		}
	}

	public void commitVoiceInput() {
		voiceInputController.commitVoiceInput();
	}

	public void postUpdateShiftKeyState() {
		mHandler.removeMessages(MSG_UPDATE_SHIFT_STATE);
		mHandler.sendMessageDelayed(mHandler
				.obtainMessage(MSG_UPDATE_SHIFT_STATE), 300);
	}

	public void updateShiftKeyState(EditorInfo attr) {
		InputConnection ic = getCurrentInputConnection();
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (attr != null && mKeyboardView != null
				&& mKeyboardSwitcher.isAlphabetMode() && ic != null) {
			mKeyboardView.setShifted(
					mCapsLock || getCursorCapsMode(ic, attr) != 0, false);
		}
	}

	private int getCursorCapsMode(InputConnection ic, EditorInfo attr) {
		int caps = 0;
		if (mAutoCap && attr != null && attr.inputType != EditorInfo.TYPE_NULL
		// No autocap for thai and korean
				&& mKeyboardSwitcher.mAutoCap) {
			caps = ic.getCursorCapsMode(attr.inputType);
		}
		return caps;
	}

	private void swapPunctuationAndSpace() {
		final InputConnection ic = getCurrentInputConnection();
		if (ic == null || !mSwapPunctuationSpace)
			return;
		final CharSequence lastTwo = ic.getTextBeforeCursor(2, 0);
		if (lastTwo != null && lastTwo.length() == 2
				&& lastTwo.charAt(0) == KEYCODE_SPACE
				&& mInputController.isSentenceSeparator(lastTwo.charAt(1))) {
			ic.beginBatchEdit();
			ic.deleteSurroundingText(2, 0);
			ic.commitText(lastTwo.charAt(1) + " ", 1);
			ic.endBatchEdit();
			updateShiftKeyStateFromEditorInfo();
		}
	}

	private void doubleSpace() {
		if (mDoubleSpacePeriod)
			mInputController.insertPeriodOnDoubleSpace();
	}

	public boolean addTypedWordToDictionary() {
		if (mSuggest != null) {
			CharSequence word;
			WordComposer wordComposer = mInputController.getCurrentWordComposer();
			if (getConverter() instanceof Korean) {
				word = wordComposer.getTypedWord();
			} else {
				word = wordComposer.getConvertedWord();
			}
			mSuggest.addUserWord(word.toString());

		}
		return true;
	}

	private boolean isAlphabet(int code) {
		if (Character.isLetter(code)) {
			return true;
		} else {
			return false;
		}
	}

	// Implementation of KeyboardViewListener

	@Override
	public void onBindInput() {
		if (mDebug)
			Log.d(TAG, "onBindInput");
	}

	@Override
	public void onUnbindInput() {
		if (mDebug)
			Log.d(TAG, "onUnbindInput");
	}

	@Override
	public Converter getConverter() {
		if (mCurTranslator == null) {
			final Converter converter = mKeyboardSwitcher.getConverter();
			if (converter != null) {
				// Set translator permanently, to handle case of mixed
				// korean/english text
				mCurTranslator = converter;
			}
		}
		return mCurTranslator;
	}

	@Override
	public void setConvertedComposing(CharSequence convertedWord) {
		suggestController.mConvertedComposing = convertedWord;
	}

	public boolean isT9PredictionOn() {
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		return isModeT9() && mKeyboardView != null
				&& mKeyboardView.isT9PredictionOn();
	}

	public void handleBackspace() {

		if (DEBUG)
		Log.d(TAG, "backspace " +Boolean.toString(mInputController.getPredicting()));

		if (voiceInputController.mVoiceInputHighlighted) {
			voiceInputController.revertVoiceInput();
			return;
		}
		suggestController.deleteLastCharacter();
	}

	public void sendDeleteChar() {
		sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
		if (onKeyboardActionListener.mDeleteCount > DELETE_ACCELERATE_AT) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }
	}

	private void handleBackword(InputConnection ic) {
		try {
			if (ic == null) {
				return;
			}
			if (mInputController.getPredicting()) {
				WordComposer wordComposer = mInputController.getCurrentWordComposer();
				wordComposer.reset();
				suggestController.mT9Suggestion = null;
				suggestController.mCandidateSelected = false;
				ic.setComposingText("", 1);
				mInputController.setPredicting(false);
				postUpdateSuggestions();
				return;
			}
			CharSequence prevSeq = ic.getTextBeforeCursor(100, 0);

			if (prevSeq == null)
				return;

			char[] cs = prevSeq.toString().toCharArray();

			if (cs == null || cs.length < 1)
				return;

			int cnt = 0;
			boolean letterOrDigitFound = false;
			boolean nonSpaceDeleted = false;
			for (int i = cs.length - 1; i >= 0; i--) {
				if (!Character.isLetterOrDigit(cs[i])) {
					if (letterOrDigitFound)
						break;

					if (cs[i] == ' ' && nonSpaceDeleted)
						break;
				} else
					letterOrDigitFound = true;

				nonSpaceDeleted = cs[i] != ' ';
				cnt++;
			}
			if (cnt > 0)
				ic.deleteSurroundingText(cnt, 0);
		} finally {
			postUpdateShiftKeyState();
		}
	}

	public void handleShift(boolean forceDraw) {
		mHandler.removeMessages(MSG_UPDATE_SHIFT_STATE);
		if (mKeyboardSwitcher.isAlphabetMode()) {
			// Alphabet keyboard
			checkToggleCapsLock();
			final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
			mKeyboardView.setShifted(mCapsLock || !mKeyboardView.isShifted(),
					forceDraw);
		} else {
			mKeyboardSwitcher.toggleShift();
		}
	}

	public void handleDakuten() {
		if (mInputController.getPredicting()) {
			WordComposer wordComposer = mInputController.getCurrentWordComposer();
			wordComposer.handleDakuten();
			InputConnection ic = getCurrentInputConnection();
			mInputController.setComposingText(ic, false);
			postUpdateSuggestions();
		}
	}

	private void abortCorrection(boolean force) {
		if (DEBUG) Log.d(TAG, "abortCorrection " + Boolean.toString(force));
		if (force || TextEntryState.isCorrecting()) {
			getCurrentInputConnection().finishComposingText();
			clearSuggestions();
		}
	}

	public void handleCharacter(int primaryCode, int[] keyCodes,
			boolean replace, boolean hardKbd) {
		if (DEBUG) Log.d(TAG, "handleCharacter " + Character.toString((char)primaryCode));

		// Check trial
		if (!isProVersion()) {
			checkTrialPopup();
		}
		if (mAskEnglishDic && mSuggest != null && mSuggest.hasNoEnglishDic()
				&& mKeyboardSwitcher.getCurLangCode().equals("EN")) {
			displayEnglishDicDialog();
		}

		if (mUseSpaceForNextWord && suggestController.mCandidateSelected) {
			// For japanese, commit the word selected with space key
			suggestController.pickDefaultSuggestion();
		}

		if (voiceInputController.mVoiceInputHighlighted) {
			voiceInputController.commitVoiceInput();
		}

        if (mInputController.isLastSelectionEmpty() && TextEntryState.isCorrecting()) {
            abortCorrection(false);
        }

		if (!mInputController.getPredicting() && isComposingModeNeeded(primaryCode)) {
            mInputController.setPredicting(true);
            saveWordInHistory(suggestController.mBestWord);
            WordComposer wordComposer = mInputController.getCurrentWordComposer();
            wordComposer.reset();
            suggestController.mT9Suggestion = null;
            suggestController.mCandidateSelected = false;
            mCurTranslator = mKeyboardSwitcher.getConverter();
        }

		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		boolean isShifted = (mKeyboardView != null && mKeyboardView.isShifted());
		// With hard keyboard the input view may not exist
		if (hardKbd && (mKeyboardView == null || !mKeyboardView.isShown())) {
			isShifted = MetaKeyKeyListener.getMetaState(mHardMetaState,
					MyMetaKeyKeyListener.META_SHIFT_ON) > 0;

			InputConnection ic = getCurrentInputConnection();
			if (ic != null) {
				EditorInfo ei = getCurrentInputEditorInfo();
				isShifted |= (mCapsLock || getCursorCapsMode(ic, ei) != 0);
			}
		}
		if (isShifted) {
			// TODO: This doesn't work with sqr(u trema), need to fix it in the
			// next release.
			if (keyCodes == null || keyCodes[0] < Character.MIN_CODE_POINT
					|| keyCodes[0] > Character.MAX_CODE_POINT) {
				return;
			}
			primaryCode = Workarounds.toUpper((char) primaryCode);
		} else if (primaryCode == '\u0130') {
			// special for turkish
			primaryCode = 'i';
			keyCodes[0] = 'i';
		} else if (keyCodes.length >= 2 && keyCodes[1] == '\u0130') {
			// For turkish compact
			keyCodes[1] = 'i';
		} else if (keyCodes.length >= 5 && keyCodes[4] == '\u0130') {
			// For turkish T9
			keyCodes[4] = 'i';
		}
		if (mInputController.getPredicting()) {
			mInputController.addCharacterWithComposing(primaryCode, keyCodes, replace, isShifted);
			postUpdateSuggestions();
		} else {
			mInputController.addCharacterWithoutComposing(primaryCode, replace, hardKbd);
		}
		updateShiftKeyStateFromEditorInfo();
		// We don't care if character has been changed to upper case automatically,
		// as typedCharacter only checks if it is a space
		TextEntryState.typedCharacter((char) primaryCode, mInputController.isWordSeparator(primaryCode));
	}

	private boolean isComposingModeNeeded(int primaryCode) {
		return (isAlphabet(primaryCode) || mSuggestNumbers)
				&& ((((isPredictionOn() &&
					(mCompoundSuggestions && !mInputController.isCursorInsideWord() || !mInputController.isCursorTouchingWord()))
					|| isModeT9())
						&& !mDisableComposing)
				|| mKeyboardSwitcher.mComposingLang)
			|| mKeyboardSwitcher.mIsUnicode;
	}

	public void handleSeparator(int primaryCode, boolean replace,
			boolean hardKbd) {
		boolean pickedDefault = false;
		boolean dontSendChar = false;

		if (voiceInputController.mVoiceInputHighlighted) {
			voiceInputController.commitVoiceInput();
		}

		// Handle separator
		final InputConnection ic = getCurrentInputConnection();
		if (ic != null) {
			ic.beginBatchEdit();
		}
		if (mInputController.getPredicting()) {
			// In certain languages where single quote is a separator, it's
			// better
			// not to auto correct, but accept the typed word. For instance,
			// in Italian dov' should not be expanded to dove' because the
			// elision
			// requires the last vowel to be removed.

			boolean autoCorrect = mAutoCorrectOn;
			final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
			if (isModeT9() && mKeyboardView != null) {
				// Autocorrect if and only if T9 prediction is off
				// Always use autocorrect in Japanese T9 (to make enter key
				// work)
				autoCorrect = mKeyboardView.isT9PredictionOn()
						|| mUseSpaceForNextWord;
			}
			// Override autocorrect to make auto-text work
			if (mSuggest.wasAutoTextFound()) {
				autoCorrect = true;
			}

			if (autoCorrect
					&& primaryCode != '\''
					&& (suggestController.mJustRevertedSeparator == null
							|| suggestController.mJustRevertedSeparator.length() == 0
							|| suggestController.mJustRevertedSeparator
							.charAt(0) != primaryCode)) {
				suggestController.pickDefaultSuggestion();
				pickedDefault = true;
			} else {
				suggestController.commitTyped(ic);
			}

			if ((mUseSpaceForNextWord || mKeyboardSwitcher.mIsUnicode)
					&& primaryCode == KEYCODE_ENTER) {
				// In japanese only commit the current selection if in
				// predicting mode
				dontSendChar = true;
			}
		}

		if (replace) {
			sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
		}

		if (!dontSendChar) {
			// Don't send enter key with hard keyboard
			if (primaryCode != KEYCODE_ENTER || !hardKbd) {
				if (primaryCode == KEYCODE_ENTER && mIMmode) {
					if (!mEnterSendsSMS) {
						// Send newline if enter is displayed instead of a
						// smiley
						if (ic != null) {
							ic.commitText(String.valueOf((char) KEYCODE_ENTER), 1);
						}
					} else {
						// Hack to avoid sending SMS twice on Android 2.0: force
						// the default editor action
						// if enter key is displayed when it should have been a
						// smiley
						if (!sendDefaultEditorAction(false)) {
							sendKeyChar((char) primaryCode);
						}
					}
				} else {
					sendKeyChar((char) primaryCode);
				}
			}
		}

		// Handle the case of ". ." -> " .." with auto-space if necessary
		// before changing the TextEntryState.
		if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED
				&& primaryCode == KEYCODE_PERIOD) {
			mInputController.reswapPeriodAndSpace();
		}

		TextEntryState.typedCharacter((char) primaryCode, true);
		if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED
				&& primaryCode != KEYCODE_ENTER) {
			swapPunctuationAndSpace();
		} else if (mEnableDoubleSpace && primaryCode == ' ') {
			// else if (TextEntryState.STATE_SPACE_AFTER_ACCEPTED) {
			doubleSpace();
		}
		if (pickedDefault && suggestController.mBestWord != null) {
			WordComposer wordComposer = mInputController.getCurrentWordComposer();
			TextEntryState.acceptedDefault(wordComposer.getTypedWord(), suggestController.mBestWord);
		}
		updateShiftKeyStateFromEditorInfo();
		if (ic != null) {
			ic.endBatchEdit();
		}
	}

	public void handleClose() {

		if (mDebug) {
			Exception e = new Exception();
			Log.d(TAG, "handleClose " + Log.getStackTraceString(e));
		}

		suggestController.commitTyped(getCurrentInputConnection());
		requestHideSelf(0);
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		mKeyboardView.closing();
		TextEntryState.endSession();
	}

	private void checkToggleCapsLock() {
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (mKeyboardView.getKeyboard().isShifted()) {
			toggleCapsLock();
		}
	}

	private void toggleCapsLock() {
		mCapsLock = !mCapsLock;
		if (mKeyboardSwitcher.isAlphabetMode()) {
			final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
			((com.dexilog.smartkeyboard.keyboard.Keyboard) mKeyboardView.getKeyboard())
					.setShiftLocked(mCapsLock);
		}
	}

	public void saveWordInHistory(CharSequence result) {
		WordComposer wordComposer = mInputController.getCurrentWordComposer();
		if (wordComposer.size() <= 1) {
			wordComposer.reset();
			return;
		}
		// Skip if result is null. It happens in some edge case.
		if (TextUtils.isEmpty(result)) {
			return;
		}

		// Make a copy of the CharSequence, since it is/could be a mutable
		// CharSequence
		final String resultCopy = result.toString();
		TypedWordAlternatives entry = new TypedWordAlternatives(this, resultCopy,
				new WordComposerImpl((WordComposerImpl)wordComposer));
		mWordHistory.add(entry);
	}

	public void postUpdateSuggestions() {
		// Set this flag so that updateSuggestions does nothing as long as
		// suggestions have not
		// been displayed. This avoids a race condition with korean and japanese
		// when starting a
		// new word
		suggestController.mWaitingForSuggestions = true;
		mHandler.removeMessages(MSG_UPDATE_SUGGESTIONS);
		mHandler.sendMessageDelayed(mHandler
				.obtainMessage(MSG_UPDATE_SUGGESTIONS), 100);
	}

	private void postUpdateOldSuggestions() {
		mHandler.removeMessages(MSG_UPDATE_OLD_SUGGESTIONS);
		mHandler.sendMessageDelayed(mHandler
				.obtainMessage(MSG_UPDATE_OLD_SUGGESTIONS), 300);
	}

	public boolean isPredictionOn() {
		boolean predictionOn = mPredictionOn;
		// if (isFullscreenMode()) predictionOn &= mPredictionLandscape;
		return predictionOn;
	}

	private boolean isSuggestionOn() {
		return mShowSuggestions
				&& (mOrientation == Configuration.ORIENTATION_PORTRAIT || !mNoLandscapeSuggestions);
	}

	public boolean isCandidateStripVisible() {
		return isPredictionOn() && isSuggestionOn() && !mKeyboardSwitcher.getCurLang().equals("EM");
	}

	public void onCancelVoice() {
		if (voiceInputController.mRecognizing) {
			switchToKeyboardView();
		}
	}

	public void switchToKeyboardView() {
		mHandler.post(new Runnable() {
			public void run() {
				voiceInputController.mRecognizing = false;
				if (mInputView != null) {
					setInputView(mInputView);
				}
				updateInputViewShown();
			}
		});
	}

	public void switchToRecognitionStatusView() {
		final boolean configChanged = mConfigurationChanging;
		mHandler.post(new Runnable() {
			public void run() {
				voiceInputController.voiceSwitchToRecognitionStatus(configChanged);
			}
		});
	}

	public void onVoiceResults(List<String> candidates,
			Map<String, List<CharSequence>> alternatives) {
		voiceInputController.onVoiceResults(candidates, alternatives);
	}

	private void clearSuggestions() {
		setSuggestions(null, false, false, false);
	}

	public void setSuggestions(List<CharSequence> suggestions,
							   boolean completions,

							   boolean typedWordValid, boolean haveMinimalSuggestion) {

		if (mIsShowingHint) {
			setCandidatesView(mCandidateViewContainer);
			mIsShowingHint = false;
		}

		if (suggestController.mCandidateView != null) {
			suggestController.mCandidateView.setSuggestions(suggestions, completions,
					typedWordValid, haveMinimalSuggestion);
		}
	}

	public boolean isModeT9() {
		return (mOrientation == Configuration.ORIENTATION_PORTRAIT)
				&& (mPortraitMode != KeyboardSwitcher.PORTRAIT_NORMAL);
	}

	public void updateSuggestions() {
		suggestController.updateSuggestions();
	}

	public List<CharSequence> getTypedSuggestions(WordComposer word) {
		return suggestController.getTypedSuggestions(word);
	}

	private void showCorrections(WordAlternatives alternatives) {
		List<CharSequence> stringList = alternatives.getAlternatives();
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (mKeyboardView != null && mKeyboardView.getKeyboard() != null) {
			mKeyboardView.getKeyboard().setPreferredLetters(null);
			showSuggestions(stringList, alternatives.getOriginalWord(), false,
					false);
		}
	}

	private void showSuggestions(List<CharSequence> stringList,
			CharSequence typedWord, boolean typedWordValid,
			boolean correctionAvailable) {
		setSuggestions(stringList, false, typedWordValid, correctionAvailable);
		if (stringList.size() > 0) {
			if (correctionAvailable && !typedWordValid && stringList.size() > 1) {
				suggestController.mBestWord = stringList.get(1);
			} else {
				suggestController.mBestWord = typedWord;
			}
		} else {
			suggestController.mBestWord = null;
		}
		setCandidatesViewShown(isCandidateStripVisible() || mCompletionOn);
	}

	public void forceUpdateSuggestions() {
		if (mHandler.hasMessages(MSG_UPDATE_SUGGESTIONS)) {
			mHandler.removeMessages(MSG_UPDATE_SUGGESTIONS);
			suggestController.updateSuggestions();
		}
	}

	public void pickSuggestionManually(int index, CharSequence suggestion) {
        final boolean correcting = TextEntryState.isCorrecting();
		InputConnection ic = getCurrentInputConnection();
		if (ic != null) {
			ic.beginBatchEdit();
		}
		if (mCompletionOn && mCompletions != null && index >= 0
				&& index < mCompletions.length) {
			CompletionInfo ci = mCompletions[index];
			if (ic != null) {
				ic.commitCompletion(ci);
			}
			suggestController.mCommittedLength = suggestion.length();
			if (suggestController.mCandidateView != null) {
				suggestController.mCandidateView.clear();
			}
			updateShiftKeyStateFromEditorInfo();
			if (ic != null) {
				ic.endBatchEdit();
			}
			return;
		}

		// If this is a punctuation, apply it through the normal key press
		if (suggestion.length() == 1 && mInputController.isWordSeparator(suggestion.charAt(0))
				&& !mInputController.getPredicting()) {
			onKeyboardActionListener.onKey(suggestion.charAt(0), null, false, false);
			if (ic != null) {
				ic.endBatchEdit();
			}
			return;
		}
		suggestController.mJustAccepted = true;
		suggestController.pickSuggestion(suggestion, correcting);
		WordComposer word = mInputController.getCurrentWordComposer();
		TextEntryState.acceptedSuggestion(word.getPreferredWord(), suggestion);
		// Follow it with a space
		// Don't add space for JP and ZH
		if (mAutoSpace && !correcting && mSpaceWhenPick && !mUseSpaceForNextWord) {
			sendSpace();
		}

        if (!correcting) {
            // Fool the state watcher so that a subsequent backspace will not do a revert, unless
            // we just did a correction, in which case we need to stay in
            // TextEntryState.State.PICKED_SUGGESTION state.
            TextEntryState.typedCharacter((char) KEYCODE_SPACE, true);
            suggestController.setNextSuggestions();
        } else {
            // If we're not showing the "Touch again to save", then show corrections again.
            // In case the cursor position doesn't change, make sure we show the suggestions again.
            clearSuggestions();
            postUpdateOldSuggestions();
        }
		if (ic != null) {
			ic.endBatchEdit();
		}

	}

	/**
	 * Tries to apply any voice alternatives for the word if this was a spoken
	 * word and there are voice alternatives.
	 *
	 * @param touching
	 *            The word that the cursor is touching, with position
	 *            information
	 * @return true if an alternative was found, false otherwise.
	 */
	/*
	 * private boolean applyVoiceAlternatives(EditingUtil.SelectedWord touching)
	 * { // Search for result in spoken word alternatives String selectedWord =
	 * touching.word.toString().trim(); if
	 * (!mWordToSuggestions.containsKey(selectedWord)) { selectedWord =
	 * selectedWord.toLowerCase(); } if
	 * (mWordToSuggestions.containsKey(selectedWord)) { mShowingVoiceSuggestions
	 * = true; List<CharSequence> suggestions =
	 * mWordToSuggestions.get(selectedWord); // If the first letter of touching
	 * is capitalized, make all the suggestions // start with a capital letter.
	 * if (Character.isUpperCase(touching.word.charAt(0))) { for (int i = 0; i <
	 * suggestions.size(); i++) { String origSugg = (String) suggestions.get(i);
	 * String capsSugg = origSugg.toUpperCase().charAt(0) +
	 * origSugg.subSequence(1, origSugg.length()).toString(); suggestions.set(i,
	 * capsSugg); } } setSuggestions(suggestions, false, true, true);
	 * setCandidatesViewShown(true); return true; } return false; }
	 */

	/**
	 * Tries to apply any typed alternatives for the word if we have any cached
	 * alternatives, otherwise tries to find new corrections and completions for
	 * the word.
	 *
	 * @param touching
	 *            The word that the cursor is touching, with position
	 *            information
	 * @return true if an alternative was found, false otherwise.
	 */
	private boolean applyTypedAlternatives(EditingUtil.SelectedWord touching) {
		// If we didn't find a match, search for result in typed word history
		WordComposer foundWord = null;
		WordAlternatives alternatives = null;
		for (WordAlternatives entry : mWordHistory) {
			if (TextUtils.equals(entry.getChosenWord(), touching.word)) {
				if (entry instanceof TypedWordAlternatives) {
					foundWord = ((TypedWordAlternatives) entry).word;
				}
				alternatives = entry;
				break;
			}
		}
		// If we didn't find a match, at least suggest completions
		// XXX false false
		if (foundWord == null
				&& (mSuggest.isValidWord(touching.word, false, false) || mSuggest
						.isValidWord(touching.word.toString().toLowerCase(),
								false, false))) {
			foundWord = new WordComposerImpl();
			for (int i = 0; i < touching.word.length(); i++) {
				foundWord.add(touching.word.charAt(i),
						new int[] { touching.word.charAt(i) });
			}
			// foundWord.setFirstCharCapitalized(Character.isUpperCase(touching.word.charAt(0)));
		}
		// Found a match, show suggestions
		if (foundWord != null || alternatives != null) {
			if (alternatives == null) {
				alternatives = new TypedWordAlternatives(this, touching.word,
						foundWord);
			}
			showCorrections(alternatives);
			mInputController.resetWordComposer((WordComposerImpl)foundWord);
			return true;
		}
		return false;
	}

	private void setOldSuggestions() {
		if (DEBUG) Log.d(TAG, "setOldSuggestions");
		/*
		 * mShowingVoiceSuggestions = false; if (mCandidateView != null &&
		 * mCandidateView.isShowingAddToDictionaryHint()) { return; }
		 */
		InputConnection ic = getCurrentInputConnection();
		if (ic == null)
			return;
		if (!mInputController.getPredicting()) {
			// Extract the selected or touching text
			EditingUtil.SelectedWord touching = EditingUtil
					.getWordAtCursorOrSelection(ic, mInputController.getLastSelectionStart(),
							mInputController.getLastSelectionEnd(), mInputController.getWordSeparators());

			if (touching != null && touching.word.length() > 1) {
				ic.beginBatchEdit();

				if (/* !applyVoiceAlternatives(touching) && */!applyTypedAlternatives(touching)) {
					abortCorrection(true);
				} else {
					TextEntryState.selectedForCorrection();
					// Disable this for now due to side effects
					//EditingUtil.underlineWord(ic, touching);
				}

				ic.endBatchEdit();
			} else {
				abortCorrection(true);
				suggestController.setNextSuggestions(); // Show the punctuation suggestions list
			}
		} else {
			abortCorrection(true);
		}
	}

	private void sendSpace() {
		sendKeyChar((char) KEYCODE_SPACE);
		updateShiftKeyStateFromEditorInfo();
		// onKey(KEY_SPACE[0], KEY_SPACE);
	}

	public void swipeAction(int action) {
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		switch (action) {
			case SwipeGestures.SWIPE_ACTION_SHIFT:
				handleShift(true);
				break;
			case SwipeGestures.SWIPE_ACTION_SYMBOLS:
				changeKeyboardMode();
				break;
			case SwipeGestures.SWIPE_ACTION_ALT_SYMBOLS:
				if (mKeyboardSwitcher.isAlphabetMode()) {
					changeKeyboardMode();
					mKeyboardSwitcher.toggleShift();
				} else if (mKeyboardView.isShifted()) {
					changeKeyboardMode();
				} else {
					mKeyboardSwitcher.toggleShift();
				}
				break;
			case SwipeGestures.SWIPE_ACTION_ARROWS:
				mKeyboardSwitcher.toggleArrows();
				break;
			case SwipeGestures.SWIPE_ACTION_CLOSE:
				handleClose();
				break;
			case SwipeGestures.SWIPE_ACTION_SPEECH:
				voiceInputController.voiceSearch();
				break;
			case SwipeGestures.SWIPE_ACTION_LANG:
				switchLang(-1);
				break;
			case SwipeGestures.SWIPE_ACTION_PREV_LANG:
				switchLang(-2);
				break;
			case SwipeGestures.SWIPE_ACTION_COMPACT_OR_T9:
				setPortraitMode((mPortraitMode + 2) % mNbPortraitModes);
				break;
			case SwipeGestures.SWIPE_ACTION_T9:
				setPortraitMode(mPortraitMode == KeyboardSwitcher.PORTRAIT_T9 ? KeyboardSwitcher.PORTRAIT_NORMAL
						: KeyboardSwitcher.PORTRAIT_T9);
				break;
			case SwipeGestures.SWIPE_ACTION_COMPACT:
				setPortraitMode(mPortraitMode == KeyboardSwitcher.PORTRAIT_COMPACT ? KeyboardSwitcher.PORTRAIT_NORMAL
						: KeyboardSwitcher.PORTRAIT_COMPACT);
				break;
			case SwipeGestures.SWIPE_ACTION_USER_DIC: {
				Intent intent = new Intent(SmartKeyboard.this,
						com.dexilog.smartkeyboard.UserDictionaryEditor.class);
				intent.setAction(Intent.ACTION_MAIN);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
				break;
			}
			case SwipeGestures.SWIPE_ACTION_CUSTOM_AUTOTEXT: {
				Intent intent = new Intent(SmartKeyboard.this,
						com.dexilog.smartkeyboard.AutoTextEditor.class);
				intent.setAction(Intent.ACTION_MAIN);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
				break;
			}
			case SwipeGestures.SWIPE_ACTION_BACKSPACE:
				handleBackspace();
				break;
			case SwipeGestures.SWIPE_ACTION_SMILEY_KEY:
				setSmileyMode(mSmileyMode == Keyboard.SMILEY_KEY_ON ? Keyboard.SMILEY_KEY_OFF
						: Keyboard.SMILEY_KEY_ON);
				break;
			case SwipeGestures.SWIPE_ACTION_DELETE_WORD:
				handleBackword(getCurrentInputConnection());
				break;
			case SwipeGestures.SWIPE_ACTION_NEXT:
				suggestController.handleNextSuggestion(false, this);
				break;
			case SwipeGestures.SWIPE_ACTION_CURSOR_LEFT:
				sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
				break;
			case SwipeGestures.SWIPE_ACTION_CURSOR_RIGHT:
				sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
				break;
			case SwipeGestures.SWIPE_ACTION_CURSOR_UP:
				sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP);
				break;
			case SwipeGestures.SWIPE_ACTION_CURSOR_DOWN:
				sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN);
				break;
			case SwipeGestures.SWIPE_ACTION_SPACE:
				handleSeparator(' ', false, false);
				break;
			case SwipeGestures.SWIPE_ACTION_CHANGE_IME:
				swipeGestures.changeIME(this);
				break;
			case SwipeGestures.SWIPE_ACTION_EMOJIS:
				switchToEmoji();
				break;
		}
	}

	public void switchLang(int langIndex) {
		mKeyboardSwitcher.changeLang(langIndex);
		onLangChanged();
	}

	private void onLangChanged() {
		setCandidatesViewShown(isCandidateStripVisible() || mCompletionOn);

		if (!mKeyboardSwitcher.mAutoCap) {
			// Disable caps lock
			mCapsLock = false;
		}
		updateShiftKeyStateFromEditorInfo();
		saveCurLang();
		setLangStatus();
	}

	private void saveCurLang() {
		String curLang = mKeyboardSwitcher.getCurLang();
		if (!curLang.equals(KeyboardSwitcher.EMOJI_LANG)) {
			SharedPreferences.Editor editor = mSharedPref.edit();
			editor.putString("curLang", curLang);
			editor.apply();
		}
	}

	private void setLangStatus() {
		int icon = 0;
		final boolean inputShown = isKeyboardVisible();

		if ((!inputShown && mSuggestHardKbd && ((mShowLangIcon & LANG_ICON_HARD) > 0))
				|| (inputShown && (mShowLangIcon & LANG_ICON_SOFT) > 0)) {
			icon = mKeyboardSwitcher.getLangIcon();
		}
		if (icon == 0) {
			hideStatusIcon();
		} else {
			showStatusIcon(icon);
		}
	}

	public void setSmileyMode(int smileyMode) {
		mKeyboardSwitcher.setSmileyMode(smileyMode, true);
		SharedPreferences.Editor editor = mSharedPref.edit();
		editor.putString(KeyboardPreferences.PREF_SMILEY_KEY, Integer.toString(smileyMode));
		editor.commit();
	}

	public void setPortraitMode(int portraitMode) {
		SharedPreferences.Editor editor = mSharedPref.edit();
		editor.putString(KeyboardPreferences.PREF_PORTRAIT_MODE, Integer.toString(portraitMode));
		editor.commit();
		setInputOptions(true);
	}

	// update flags for silent mode
	private void updateRingerMode() {
		if (mAudioManager == null) {
			mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		}
		if (mAudioManager != null) {
			mSilentMode = (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL);
		}
	}

	public void playKeyClick(int primaryCode) {
		// if mAudioManager is null, we don't have the ringer state yet
		// mAudioManager will be set by updateRingerMode
		if (mAudioManager == null) {
			final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
			if (mKeyboardView != null) {
				updateRingerMode();
			}
		}
		if (mSoundOn && !mSilentMode) {
			if (mSoundStyle > 0) {
				mSoundPool.play(mSoundID[mSoundStyle], mVolume, mVolume, 1, 0,
						0);
			} else {
				// FIXME: These should be triggered after auto-repeat logic
				int sound = AudioManager.FX_KEYPRESS_STANDARD;
				switch (primaryCode) {
				case Keyboard.KEYCODE_DELETE:
					sound = AudioManager.FX_KEYPRESS_DELETE;
					break;
				case KEYCODE_ENTER:
					sound = AudioManager.FX_KEYPRESS_RETURN;
					break;
				case KEYCODE_SPACE:
					sound = AudioManager.FX_KEYPRESS_SPACEBAR;
					break;
				}
				mAudioManager.playSoundEffect(sound, mVolume);
			}
		}
	}

	public void sendText(String text) {
		if (mDebug)
			Log.d(TAG, "voice input: " + text);
		final InputConnection ic = getCurrentInputConnection();
		if (ic != null) {
			// Check if text must be capitalized
			EditorInfo ei = getCurrentInputEditorInfo();
			if (ei != null && mKeyboardSwitcher.isAlphabetMode()
					&& getCursorCapsMode(ic, ei) != 0) {
				text = Character.toUpperCase(text.charAt(0))
						+ text.substring(1, text.length());
			}

			// Commit the composing text
			ic.finishComposingText();

			// Add a space if the field already has text.
			// Except for Japanese
			CharSequence charBeforeCursor = ic.getTextBeforeCursor(1, 0);
			if (charBeforeCursor != null && !charBeforeCursor.equals(" ")
					&& (charBeforeCursor.length() > 0) && !mUseSpaceForNextWord) {
				text = " " + text;
			}

			ic.setComposingText(text, 1);

			if (voiceInputController.mRestartVoice) {
				voiceInputController.voiceSearch();
			}
		} else {
			Log.e(TAG, "No input connection!");
		}
	}

	private void checkTrialPopup() {
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (mKeyboardView == null || !mKeyboardView.isShown()) {
			return;
		}
		if (trialPolicy.checkDisplayPopup()) {
			showTrialPopup(mKeyboardView);
		}
	}

	private void showTrialPopup(KeyboardView mKeyboardView) {
		try {
			final Resources res = getResources();
			AlertDialog.Builder builder = new AlertDialog.Builder(SmartKeyboard.this);
			builder.setTitle("Smart Keyboard");
			builder.setMessage(res.getString(R.string.trial_popup));
			builder.setPositiveButton(res.getString(R.string.buy),
					new OnClickListener() {
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
							handleClose();
							Intent intent = new Intent(Intent.ACTION_VIEW);
							intent
									.setData(Uri
											.parse("http://www.dexilog.com/smartkeyboard/buy"));
							intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							startActivity(intent);
						}
					});
			builder.setNegativeButton(res.getString(R.string.no_thanks),
					new OnClickListener() {
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
						}
					});
			builder.setCancelable(false);
			AlertDialog alert = builder.create();
			Window window = alert.getWindow();
			WindowManager.LayoutParams lp = window.getAttributes();
			lp.token = mKeyboardView.getWindowToken();
			lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
			window.setAttributes(lp);
			window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
			alert.show();
			alert.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
			alert.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
			// Enable the dialog later
			mHandler.sendMessageDelayed(mHandler.obtainMessage(
					MSG_ENABLE_TRIAL_BUTTONS, alert), 4000);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void displayEnglishDicDialog() {
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (mKeyboardView == null || !mKeyboardView.isShown()) {
			return;
		}

		try {
			final Resources res = getResources();
			AlertDialog.Builder builder = new AlertDialog.Builder(SmartKeyboard.this);
			builder.setTitle("Smart Keyboard");
			builder.setMessage(res.getString(R.string.english_dic));
			builder.setPositiveButton(res.getString(R.string.yes),
					new OnClickListener() {
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
							handleClose();
							Intent intent = new Intent(Intent.ACTION_VIEW);
							intent
									.setData(Uri
											.parse("market://search?q=pname:net.cdeguet.smartkeyboardpro.en"));
							intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							startActivity(intent);
						}
					});
			builder.setNegativeButton(res.getString(R.string.no),
					new OnClickListener() {
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
						}
					});
			builder.setCancelable(false);
			AlertDialog alert = builder.create();
			Window window = alert.getWindow();
			WindowManager.LayoutParams lp = window.getAttributes();
			lp.token = mKeyboardView.getWindowToken();
			lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
			window.setAttributes(lp);
			window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
			alert.show();
			alert.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
			alert.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
			// Enable the dialog later
			mHandler.sendMessageDelayed(mHandler.obtainMessage(
					MSG_ENABLE_TRIAL_BUTTONS, alert), 6000);
			// Don't ask again
			mAskEnglishDic = false;
			SharedPreferences.Editor editor = mSharedPref.edit();
			editor.putBoolean(KeyboardPreferences.PREF_ASK_ENGLISH_DIC, false);
			editor.commit();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void increaseWordCount(String word) {
		if (DEBUG)
			Log.d(TAG, "Increase frequency for word: " + word);
		if (mSuggest != null) {
			mSuggest.increaseWordCount(word);
		}
	}

	public void vibrate(int primaryCode) {
		if (!mVibrateOn || (mSpaceAlert && primaryCode != KEYCODE_SPACE)) {
			return;
		}
		if (mVibrator == null) {
			mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		}
		mVibrator.vibrate(mVibrateDuration);
	}

	void promoteToUserDictionary(String word, int frequency) {

		suggestController.promoteToUserDictionary(word, frequency);
	}

	private void loadSettings() {
		// Get the settings preferences
		final SharedPreferences sp = mSharedPref;
		mAutoCap = sp.getBoolean(KeyboardPreferences.PREF_AUTO_CAP, true);
		mQuickFixes = sp.getBoolean(KeyboardPreferences.PREF_QUICK_FIXES, true);
		mShowSuggestions = sp.getBoolean(KeyboardPreferences.PREF_SHOW_SUGGESTIONS, true);
		final boolean autoComplete = sp.getBoolean(KeyboardPreferences.PREF_AUTO_COMPLETE, true)
				& isSuggestionOn();
		mAutoCorrectOn = mSuggest != null && (autoComplete || mQuickFixes);
		mCorrectionMode = autoComplete ? Suggest.CORRECTION_FULL
				: (mQuickFixes ? Suggest.CORRECTION_BASIC
						: Suggest.CORRECTION_NONE);
		mSuggestHardKbd = sp.getBoolean(KeyboardPreferences.PREF_SUGGEST_HARD, false);
		mAlwaysSuggest = sp.getBoolean(KeyboardPreferences.PREF_ALWAYS_SUGGEST, true)
				& isSuggestionOn();

		voiceInputController.mHasUsedVoiceInput = sp.getBoolean(KeyboardPreferences.PREF_HAS_USED_VOICE_INPUT, false);
		voiceInputController.mHasUsedVoiceInputUnsupportedLocale = sp.getBoolean(
				KeyboardPreferences.PREF_HAS_USED_VOICE_INPUT_UNSUPPORTED_LOCALE, false);
	}

	private void initSuggestPuncList() {
		mSuggestPuncList = new ArrayList<CharSequence>();
		String defaultPuncs = getResources().getString(
				R.string.suggested_punctuations_old);
		String suggestPuncs = mSharedPref.getString(KeyboardPreferences.PREF_CUSTOM_PUNCTUATION,
				defaultPuncs);
		if (suggestPuncs != null) {
			for (int i = 0; i < suggestPuncs.length(); i++) {
				mSuggestPuncList.add(suggestPuncs.subSequence(i, i + 1));
			}
		}
	}

	public void changeKeyboardMode() {
		mKeyboardSwitcher.toggleSymbols();
		if (mCapsLock && mKeyboardSwitcher.isAlphabetMode()) {
			final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
			((com.dexilog.smartkeyboard.keyboard.Keyboard) mKeyboardView.getKeyboard())
					.setShiftLocked(mCapsLock);
		}

		updateShiftKeyStateFromEditorInfo();
	}

	public void updateShiftKeyStateFromEditorInfo() {
		updateShiftKeyState(getCurrentInputEditorInfo());
	}

	public boolean launchSettings() {
		// Close to avoid sticky 123 preview
		final boolean disableSettings = mSharedPref.getBoolean(KeyboardPreferences.PREF_DISABLE_SETTINGS, false);
		if (disableSettings) {
			return false;
		}
		launchSettingsActivity();
		return true;
	}

	private void launchSettingsActivity() {
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		mKeyboardView.closing();
		Intent intent = new Intent(SmartKeyboard.this,
                Settings.class);
		intent.setAction(Intent.ACTION_MAIN);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}

	public void saveT9Pref(boolean state) {
		SharedPreferences.Editor editor = mSharedPref.edit();
		editor.putBoolean(KeyboardPreferences.PREF_T9_PREDICTION, state);
		editor.commit();
	}

	// Called when preferences are changed
	@Override
	synchronized public void onSharedPreferenceChanged(
			SharedPreferences sharedPreferences, String key) {
		// avoid infinite loop...
		if (!mPrefChanged) {
			mPrefChanged = true;
			// reset interface
			mCustomKeys.reload();
			onInitializeInterface();
			mPrefChanged = false;
		}
	}

	void toggleT9() {
		boolean newState = mKeyboardSwitcher.toggleT9();
		// Save T9 state
		if (mPredictionOn) {
            saveT9Pref(newState);
            if (!newState && mInputController.getPredicting()) {
				suggestController.switchOffT9Prediction();
			}
        }
	}

	void resetPredictionStateAfterPickedSuggestion(CharSequence suggestion) {
		mInputController.setPredicting(false);
		suggestController.mCandidateSelected = false;
        suggestController.mCommittedLength = suggestion.length();
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		if (mKeyboardView != null) {
            final Keyboard kbd = mKeyboardView.getKeyboard();
            if (kbd != null) {
                kbd.setPreferredLetters(null);
            }
        }
    }

	boolean isKeyboardShifted() {
		final KeyboardView mKeyboardView = mKeyboardSwitcher.getKeyboardView();
		return mKeyboardSwitcher.isAlphabetMode() && mKeyboardView != null && mKeyboardView
                .isShifted();
    }

	public void switchToLatestLanguage() {
		mKeyboardSwitcher.switchToLatestLanguage();
		onLangChanged();
	}

	public void switchToEmoji() {
		mKeyboardSwitcher.switchToEmoji();
		onLangChanged();
	}
}
