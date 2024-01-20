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
import android.inputmethodservice.*;
import android.view.*;
import android.view.inputmethod.*;
import android.app.*;
import android.content.*;
import android.os.*;
import android.util.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.method.*;
import android.text.*;
import android.media.*;
import android.hardware.*;
import android.content.*;
import android.widget.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.*;
import android.database.*;
import android.hardware.display.DisplayManager;

public class WrapperActivity extends Activity {

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
Display[] displays = displayManager.getDisplays();
		for(int i = 0; i < displays.length;i++)
		{
			Log.e("SmartKeyboard", displays[i].toString());
		}
		ActivityOptions options = ActivityOptions.makeBasic();
		Log.e("SmartKeyboard", displayManager.getDisplay(Display.DEFAULT_DISPLAY).toString());
		Log.e("SmartKeyboard", getWindow().getWindowManager().getDefaultDisplay().toString());
		options.setLaunchDisplayId(getWindow().getWindowManager().getDefaultDisplay().getDisplayId());
		startActivity(new Intent(this, Settings.class), options.toBundle());
//new Intent(this, PicoActivity.class).setComponent(new android.content.ComponentName("com.android.settings", "com.android.settings.Settings")), options.toBundle());
		

	
		
		/*Intent i = new Intent();
		i.setComponent(new android.content.ComponentName("com.vrchat.android","com.unity3d.player.UnityPlayerActivity"));
		i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		startActivity(i);*/
		// broken? FEATSINGLE3D seems have to be patched out of system_server
		// shitdance should get rid of vr pussyness and go back to sucktok
	}

}
