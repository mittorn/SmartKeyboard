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

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import com.dexilog.openskin.OpenSkin;
import com.dexilog.smartkeyboard.keyboard.CustomKeys;
import com.dexilog.smartkeyboard.keyboard.Keyboard;
import com.dexilog.smartkeyboard.settings.PermissionManager;
import com.dexilog.smartkeyboard.ui.MainKeyboardView;
import com.dexilog.smartkeyboard.ui.SkinLoader;
import com.dexilog.smartkeyboard.ui.OnKeyboardActionListener;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class SkinList extends Activity implements OnKeyboardActionListener, OnItemClickListener {

	private static final String TAG = "SmartKeyboard";
	private static final List<OpenSkinDesc> EMPTY_SKIN_LIST = new LinkedList<>();
	String[] mBuiltinSkins;
	CharSequence[] mSkinNames = {};
	CharSequence[] mSkinPackages = {};
	MyListAdapter mAdapter;
	
	SharedPreferences mPrefs;
	SkinLoader mSkinLoader;
	CustomKeys mCustomKeys;
	Keyboard mKeyboard;
	MainKeyboardView mInputView;
	boolean mShiftState = false;
	CharSequence mGetSkins;
	int mCurSkin;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mBuiltinSkins = getResources().getStringArray(R.array.skin_choices);
		
		final Resources res = getResources();
		final Configuration conf = res.getConfiguration();
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		String skin = mPrefs.getString(KeyboardPreferences.PREF_SKIN, "Gingerbread");
		
		mGetSkins = res.getString(R.string.get_skins);
		
		setContentView(R.layout.skin_list);
		
		// Create the list view
		ListView list = (ListView)findViewById(R.id.list);

		// Set list adapter
		mAdapter = new MyListAdapter();
		list.setAdapter(mAdapter);
		list.setOnItemClickListener(this);

		// Load skin list
		reloadSkins();
		skin = checkIfCurentSkinStillExists(skin);

		// Load the current skin
		mSkinLoader = new SkinLoader(this, conf.orientation);
		mSkinLoader.loadSkin(skin);
		
		createKeyboardView();
	}

	private String checkIfCurentSkinStillExists(String skin) {
		mCurSkin = 0;
		final int len = mSkinPackages.length;
		boolean skinFound = false;
		for (int i=0; i<len; i++) {
			if (mSkinPackages[i].toString().equals(skin)) {
				skinFound = true;
				mCurSkin = i;
				break;
			}
		}
		if (!skinFound) {
			mCurSkin = 0;
			skin = "iPhone";
			Log.d(TAG, "Current skin does not exist any more!");
		}
		return skin;
	}

	private void createKeyboardView() {
		Resources res = getResources();
		mCustomKeys = new CustomKeys(this, mPrefs);
		mInputView = (MainKeyboardView)findViewById(R.id.keyboard);
		mKeyboard = new Keyboard(this, R.xml.qwerty,  R.id.mode_normal, true, false, false, null);
		mInputView.setKeyboard(mKeyboard);
		mInputView.setPreviewEnabled(false);
		mInputView.setProximityCorrectionEnabled(true);
		mInputView.disableMT(false);
		mInputView.setCustomKeys(mCustomKeys);
		mInputView.setNoAltPreview(true);
		mInputView.setDisplayAlt(mPrefs.getBoolean(KeyboardPreferences.PREF_DISPLAY_ALT, true));
		mInputView.setOnKeyboardActionListener(this);
		mInputView.applySkin(mSkinLoader.getCurrentSkin());
		mKeyboard.setLanguage("EN");
		mKeyboard.setMicButton(res, Keyboard.MicDisplay.REPLACE_COMMA);
	}

	@Override
	public void onRestart() {
		super.onRestart();
		reloadAndUpdateList();
	}

	private void reloadAndUpdateList() {
		reloadSkins();
		mAdapter.notifyDataSetChanged();
	}

	private interface LoadOpenSkinsCallback {
		void onLoad(List<OpenSkinDesc> openSkins);
	}

	void reloadSkins() {
		// Retrieve skins for Better Keyboard
		final List<ApplicationInfo> bkSkins = getBKSkins();
		// Retrieve open skins
		getOpenSkins(new LoadOpenSkinsCallback() {
			@Override
			public void onLoad(List<OpenSkinDesc> openSkins) {
				loadAllSkins(bkSkins, openSkins);
			}
		});
	}

	private void loadAllSkins(final List<ApplicationInfo> bkSkins, final List<OpenSkinDesc> openSkins) {
		final int bkLen = bkSkins.size();
		final int openLen = openSkins.size();
		final int builtinLen = mBuiltinSkins.length;
		mSkinNames = new CharSequence[bkLen + builtinLen + openLen];
		mSkinPackages = new CharSequence[bkLen + builtinLen + openLen];

		for (int i=0; i < builtinLen; i++) {
			final String name = mBuiltinSkins[i];
			mSkinNames[i] = name;
			mSkinPackages[i] = name;
		}

		StringBuilder sb = new StringBuilder();
		for (int i=0; i < openLen; i++) {
			OpenSkinDesc desc = openSkins.get(i);
			mSkinNames[builtinLen + i] = desc.mName;
			sb.setLength(0);
			sb.append("os:");
			sb.append(desc.mPath);
			mSkinPackages[builtinLen + i] = sb.toString();
		}

		final PackageManager packageManager = getPackageManager();
		for (int i=0; i < bkLen; i++) {
			final ApplicationInfo info = bkSkins.get(i);
			sb.setLength(0);
			sb.append("bk:");
			sb.append(info.packageName);
			final CharSequence skinName = info.loadLabel(packageManager);
			Log.d(TAG, "Loaded skin " + skinName.toString());
			mSkinPackages[builtinLen + openLen + i] = sb.toString();
			mSkinNames[builtinLen + openLen + i] = skinName;
		}
	}

	private void getOpenSkins(final LoadOpenSkinsCallback callback) {
		/**PermissionManager.get(this).checkReadStoragePermission(new PermissionManager.PermissionsResultCallback() {
			@Override
			public void onRequestPermissionsResult(boolean allGranted) {
				if (allGranted)
					findOpenSkins(callback);
				else
					callback.onLoad(EMPTY_SKIN_LIST);
			}
		});*/
		findOpenSkins(callback);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults) {
		reloadAndUpdateList();
	}

	private void findOpenSkins(LoadOpenSkinsCallback callback) {
		List<OpenSkinDesc> skinList = new LinkedList<>();
		final String sdcardPath = Environment.getExternalStorageDirectory().getPath();
		File folder = new File(sdcardPath, "skins");
		File[] list = folder.listFiles();
		if (list != null) {
			final int len = list.length;
			for (int i=0; i<len; i++) {
				File file = list[i];
				if (file.getName().endsWith(".zip")) {
					String skinName = OpenSkin.getSkinName(file);
					if (skinName != null) {
						skinList.add(new OpenSkinDesc(file.getAbsolutePath(), skinName));
					}
				}
			}
		}
		callback.onLoad(skinList);
	}

	private List<ApplicationInfo> getBKSkins() {
		LinkedList<ApplicationInfo> skinList = new LinkedList<ApplicationInfo>();
		// Retrieve skins for Better Keyboard
		final PackageManager packageManager = getPackageManager();
		final Intent intent = new Intent("com.betterandroid.betterkeyboard.skins");
		List<ResolveInfo> list = packageManager.queryIntentActivities(intent, 0);
		final int len = list.size();
		for (int i=0; i < len; i++) {
			final ApplicationInfo info = list.get(i).activityInfo.applicationInfo;

			// Check if new format
			boolean isNewFormat = false;
			Resources res;
			try {
				res = packageManager.getResourcesForApplication(info.packageName);
				// Check for newFormat
				int idNewFormat = res.getIdentifier("newformat2", "bool", info.packageName);
				if (idNewFormat != 0) {
					isNewFormat = res.getBoolean(idNewFormat);
				}
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (!isNewFormat) {
				skinList.add(info);
			}
		}
		return skinList;
	}

	/*
	static private class NullClickListener implements DialogInterface.OnClickListener {
		@Override
		public void onClick(DialogInterface dialog, int which) {	
		}
	}
	*/
	
	private class MyListAdapter extends BaseAdapter {

		private class ViewHolder {
			ImageView image;
			TextView text;
		}
		
		@Override
		public int getCount() {
			return mSkinNames.length + 1;
		}

		@Override
		public Object getItem(int position) {
			if (position < mSkinPackages.length) {
				return mSkinPackages[position];
			}
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = (LinearLayout) getLayoutInflater().inflate(R.layout.skin_item, null);
				holder = new ViewHolder();
	            holder.image = (ImageView)convertView.findViewById(R.id.icon);
	            holder.text = (TextView)convertView.findViewById(R.id.skin_name);
	            convertView.setTag(holder);
			} else {
				holder = (ViewHolder)convertView.getTag();
			}
            CharSequence text;
            if (position < mSkinNames.length) {
            	text = mSkinNames[position];
            } else {
            	text = mGetSkins;
            }
            holder.image.setVisibility(position == mCurSkin ? View.VISIBLE : View.INVISIBLE);
            holder.text.setText(text);
            return convertView;
		}
		
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		// Last item = get more skins
		if (position >= mSkinNames.length) {
			Intent intent = new Intent(Intent.ACTION_VIEW,
					Uri.parse("http://www.dexilog.com/smartkeyboard/skins"));
			startActivity(intent);
		} else {
			mCurSkin = position;
			String skinPackage = (String)parent.getItemAtPosition(position);
			mAdapter.notifyDataSetChanged();
			
			if (skinPackage.startsWith("bk:")) {
				// Display BK warning
			/*	AlertDialog d = (new AlertDialog.Builder(this))
				.setTitle(android.R.string.dialog_alert_title)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setMessage( (R.string.bk_warning))
				.setCancelable(true)
				.setPositiveButton(android.R.string.ok, new NullClickListener())
				.create();
				d.show(); */
			} else if (skinPackage.startsWith("os:")) {
				// Cache the skin to the internal storage if possible
				SkinLoader.cacheOpenSkin(this, skinPackage.substring(3));
			}
			
			// Load the new skin
			mSkinLoader.loadSkin(skinPackage);
			mInputView.applySkin(mSkinLoader.getCurrentSkin());
			
			// Save the skin preference
			mPrefs.edit().putString(KeyboardPreferences.PREF_SKIN, skinPackage).commit();
		}
	}
	
	static private class OpenSkinDesc {
		final public String mPath;
		final public String mName;
		
		public OpenSkinDesc(String path, String name) {
			mPath = path;
			mName = name;
		}
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

	@Override
	public void onKey(int primaryCode, int[] keyCodes, boolean hardKbd,
			boolean replace) {
		if (primaryCode ==Keyboard.KEYCODE_SHIFT) {
			mShiftState = !mShiftState;
			mInputView.setShifted(mShiftState, false);
		}
	}

	@Override
	public void onPress(int primaryCode) {		
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
