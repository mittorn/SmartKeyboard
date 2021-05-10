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

import java.lang.reflect.Field;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import com.dexilog.smartkeyboard.keyboard.Keyboard;
import com.dexilog.smartkeyboard.keyboard.Keyboard.Key;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
//import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.dexilog.smartkeyboard.keyboard.CustomKeys;
import com.dexilog.smartkeyboard.keyboard.GlobalResources;
import com.dexilog.smartkeyboard.settings.VibratorSettings;
import com.dexilog.smartkeyboard.ui.CalibrationInfo;
import com.dexilog.smartkeyboard.ui.SkinLoader;
import com.dexilog.smartkeyboard.ui.OnKeyboardActionListener;

public class Calibration extends Activity implements OnKeyboardActionListener {

	static final String SAVED_DX = "calibration_dx";
	static final String SAVED_DY = "calibration_dy";
	static final String SAVED_SPACE = "calibration_space";
	static final String SAVED_PORTRAIT = "calibration_portrait";
	
	SkinLoader mSkinLoader;
	CustomKeys mCustomKeys;
	Keyboard mKeyboard;
	CalibrationKeyboard mInputView;
	int mCursor;
	Spannable mSpan;
	boolean mAcceptText = true;
	CharacterStyle mBgSpan = new BackgroundColorSpan(0xFFF0A000);
	CharacterStyle mFgSpan = new ForegroundColorSpan(0xFF1040FF);
	int mTotalDx = 0;
	int mTotalDy = 0;
	int mTotalSpaceDy = 0;
	int mTypedLetters = 0;
	int mTypedSpace = 0;
	int mAverageDx = 0;
	int mAverageDy = 0;
	int mAverageSpaceDy = 0;
	boolean mPortrait;
	float mDensity;
	int mScreenLayout = 0;

	private boolean mVibrateOn;
	private Vibrator mVibrator;
	private int mVibrateDuration;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Resources res = super.getResources();
		final Configuration conf = res.getConfiguration();

		mDensity = res.getDisplayMetrics().density;
		
		// Retrieve screen size (Android 1.6 only)
		try {
			Field screenLayout = Configuration.class.getField("screenLayout");
			mScreenLayout = screenLayout.getInt(getResources().getConfiguration());
		} catch (Exception e) {
		}
		// Check if the device is a tablet (xlarge screen)
		boolean isTablet = (mScreenLayout & 4) != 0;
				
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		final String skin = sp.getString(KeyboardPreferences.PREF_SKIN, "iPhone");
		GlobalResources.mKeyHeight = sp.getInt(KeyboardPreferences.PREF_KEY_HEIGHT, 50);
		GlobalResources.mKeyHeightLandscape = sp.getInt(KeyboardPreferences.PREF_KEY_HEIGHT_LANDSCAPE, 50);
	    
		// Adjust height on tablets
		if (isTablet) {
			GlobalResources.mKeyHeight = GlobalResources.mKeyHeight * 3 / 2;
			GlobalResources.mKeyHeightLandscape = GlobalResources.mKeyHeightLandscape * 2;
		}
		
		// Vibrator
		mVibrateOn = sp.getBoolean(KeyboardPreferences.PREF_VIBRATE_ON, true);
		if (mVibrateOn) {
			mVibrateDuration = (int)new VibratorSettings(sp).getDurationMs();
		}

		// Load the current skin
		mSkinLoader = new SkinLoader(this, conf.orientation);
		mSkinLoader.loadSkin(skin);

		mCustomKeys = new CustomKeys(this, sp);

		setContentView(R.layout.calibration);

		// Create the keyboard view
		mInputView = (CalibrationKeyboard)findViewById(R.id.keyboard);
		mKeyboard = new Keyboard(this, R.xml.qwerty,  R.id.mode_normal, true, false, false, null);
		mInputView.setKeyboard(mKeyboard);
		mInputView.setProximityCorrectionEnabled(true);
		mInputView.disableMT(false);
		mInputView.setCustomKeys(mCustomKeys);
		mInputView.setNoAltPreview(true);
		mInputView.setDisplayAlt(sp.getBoolean(KeyboardPreferences.PREF_DISPLAY_ALT, true));
		mInputView.setOnKeyboardActionListener(this);
		mInputView.applySkin(mSkinLoader.getCurrentSkin());
		int padding = 0;
		if (conf.orientation == Configuration.ORIENTATION_PORTRAIT) {
			padding = sp.getInt(KeyboardPreferences.PREF_BOTTOM_PADDING, 0);
		}
		mInputView.setPadding(0, 0, 0, padding);

		mKeyboard.setLanguage("EN");
		mKeyboard.setMicButton(res, Keyboard.MicDisplay.HIDDEN);

		TextView textView = (TextView)findViewById(R.id.text);
		mSpan = (Spannable)textView.getText();
		
		Button button = (Button)findViewById(R.id.button);
		button.setOnClickListener(new  View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
            	resetValues(v);
            }});

		// Restore state
		if (savedInstanceState != null) {
			mAverageDx = savedInstanceState.getInt(SAVED_DX, 0);
			mAverageDy = savedInstanceState.getInt(SAVED_DY, 0);
			mAverageSpaceDy = savedInstanceState.getInt(SAVED_SPACE, 0);
			mPortrait = savedInstanceState.getBoolean(SAVED_PORTRAIT, true);
		}
	}

	@Override
	protected void onSaveInstanceState (Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(SAVED_DX, mAverageDx);
		outState.putInt(SAVED_DY, mAverageDy);
		outState.putInt(SAVED_SPACE, mAverageSpaceDy);
		outState.putBoolean(SAVED_PORTRAIT, mPortrait);
	}
	
	@Override
	public void onStart() {
		super.onStart();

		// Reset the text
		reset();
	}
	
	private void reset() {
		mCursor = 0;
		resetSpan();
		mAcceptText = true;
		mTotalDx = 0;
		mTotalDy = 0;
		mTotalSpaceDy = 0;
		mTypedLetters = 0;
		mTypedSpace = 0;
	}

	@Override
	protected Dialog onCreateDialog(int id) {

		final View content = getLayoutInflater().inflate(R.layout.calibration_done, null);

		return new AlertDialog.Builder(this)
		.setTitle(R.string.calibration)
		.setView(content)
		.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// Save parameters
				SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(Calibration.this);
				SharedPreferences.Editor edit = sp.edit();
				if (mPortrait) {
					edit.putInt("calib_p_dx", -mAverageDx);
					edit.putInt("calib_p_dy", -mAverageDy);
					edit.putInt("calib_p_space", -mAverageSpaceDy);
				} else {
					edit.putInt("calib_l_dx", -mAverageDx);
					edit.putInt("calib_l_dy", -mAverageDy);
					edit.putInt("calib_l_space", -mAverageSpaceDy);		
				}
				edit.commit();
				finish();
			}})
		.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				reset();
			}})
		.create();
	}
	
	@Override
	protected void onPrepareDialog(int d, Dialog dialog) {
		TextView profile = (TextView)dialog.findViewById(R.id.profile_value);
		// Display profile value
		StringBuilder sb = new StringBuilder();
		sb.append(mPortrait ? "P: " : "L: ");
		sb.append("x=");
		sb.append(mAverageDx);
		sb.append(" y=");
		sb.append(mAverageDy);
		sb.append(" s=");
		sb.append(mAverageSpaceDy);
		profile.setText(sb);
	}

	private void resetSpan() {
		mSpan.removeSpan(mBgSpan);
		mSpan.removeSpan(mFgSpan);
	}

	@Override
	public boolean onDisplayPrefScreen() {
		return false;
	}

	@Override
	public void onVoiceInput() {
	}

	@Override
	public void onShowEmojis() {
	}

	private void resetValues(View view) {
		Dialog alert = new AlertDialog.Builder(this)
		.setTitle(android.R.string.dialog_alert_title)
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setMessage(R.string.calibration_reset)
		.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// Reset to factory default
				SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(Calibration.this);
				SharedPreferences.Editor edit = sp.edit();
				edit.putInt("calib_p_dx", 0);
				edit.putInt("calib_p_dy", -10);
				edit.putInt("calib_p_space", 6);
				edit.putInt("calib_l_dx", 0);
				edit.putInt("calib_l_dy", -9);
				edit.putInt("calib_l_space", 4);
				edit.putBoolean("calib_done", false);
				edit.commit();
			}})
		.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
			}})
		.create();
		alert.show();
	}

	@Override
	public void onKey(int primaryCode, int[] keyCodes, boolean hardKbd,
			boolean replace) {
		if (mAcceptText == false) {
			return;
		}
		if (primaryCode == Keyboard.KEYCODE_DELETE) {
			if (mCursor > 0) {
				mCursor--;
			}
		} else if (primaryCode == ' ') {
			// space
			handleChar(primaryCode, keyCodes);
		} else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
			mInputView.setShifted(false, false);
		} else if (primaryCode >= 'a' && primaryCode <= 'z') {
			handleChar(primaryCode, keyCodes);
		}

		if (mCursor == 0) {
			resetSpan();
		} else {
			mSpan.setSpan(mBgSpan, 0, mCursor, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			mSpan.setSpan(mFgSpan, mCursor, mCursor, Spannable.SPAN_POINT_POINT);
		}

		if (mCursor == mSpan.length()) {
			mAcceptText = false;
			
			// Retrieve previous values
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(Calibration.this);
			CalibrationInfo oldVal = new CalibrationInfo(sp);
			boolean calibDone = sp.getBoolean("calib_done", false);
			
			mAverageDx = (int)(mTotalDx / (mDensity * mTypedLetters));
			mAverageDy = (int)(mTotalDy / (mDensity * mTypedLetters));
			mAverageSpaceDy = (int)(mTotalSpaceDy / (mDensity * mTypedSpace));
			mPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
			if (!calibDone) {
				// First time calibration is launched?
				sp.edit().putBoolean("calib_done", true).commit();
			} else {
				// Merge with previous values
				if (mPortrait) {
					mAverageDx = (-3*oldVal.mPortraitDx + mAverageDx) / 4;
					mAverageDy = (-3*oldVal.mPortraitDy + mAverageDy) / 4;
					mAverageSpaceDy = (-3*oldVal.mPortraitSpace + mAverageSpaceDy) / 4;
				} else {
					mAverageDx = (-3*oldVal.mLandscapeDx + mAverageDx) / 4;
					mAverageDy = (-3*oldVal.mLandscapeDy + mAverageDy) / 4;
					mAverageSpaceDy = (-3*oldVal.mLandscapeSpace + mAverageSpaceDy) / 4;			
				}
			}
			showDialog(0);
		}
	}

	private void handleChar(int primaryCode, int[] keyCodes) {
		// Check if typed letter is acceptable
		char nextChar = mSpan.charAt(mCursor);
		boolean accepted = false;
		if (nextChar == primaryCode) {
			accepted = true;
		} else {
			for (int i=0; i<keyCodes.length; i++) {
				if (keyCodes[i] == nextChar) {
					accepted = true;
					break;
				}
			}
		}
		if (accepted) {
			// Find key corresponding to the preferred letter
			int lastX = mInputView.mLastX;
			int lastY = mInputView.mLastY;
			final int[] nearestKeys = mKeyboard.getNearestKeys(lastX, lastY);
			final Key[] keys = mKeyboard.getKeys();
			Key preferredKey = null;
			for (int i = 0; i < nearestKeys.length; i++) {
				Key key = keys[nearestKeys[i]];
				if (key.codes[0] == nextChar) {
					preferredKey = key;
					break;
				}
			}
			if (preferredKey != null) {
				// compute delta
				int dx = lastX - preferredKey.x - preferredKey.width / 2;
				int dy = lastY - preferredKey.y - preferredKey.height / 2;
				
				//Log.d("SmartKeyboard", "delta " + Integer.toString(dx) + " " + Integer.toString(dy));

				// Compute total
				if (nextChar == ' ') {
					mTotalSpaceDy += dy;
					mTypedSpace++;
				} else {
					mTotalDx += dx;
					mTotalDy += dy;
					mTypedLetters++;
				}
			}

			mCursor++;
		}
	}

	private void vibrate() {
		if (!mVibrateOn) {
			return;
		}
		if (mVibrator == null) {
			mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		}
		mVibrator.vibrate(mVibrateDuration);
	}
	
	@Override
	public void onPress(int primaryCode) {
		vibrate();
	}

	@Override
	public void onRelease(int primaryCode) {		
	}

	@Override
	public void onText(CharSequence text) {		
	}

	@Override
	public void swipeDown() {		
	}

	@Override
	public void swipeLeft() {		
	}

	@Override
	public void swipeRight() {		
	}

	@Override
	public void swipeUp() {		
	}

}
