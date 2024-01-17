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

public final class PicoActivity extends Activity {
	public static PicoActivity mSingleton;
	LinearLayout mRoot;
	View mContent;
	EditText mEditor;
	InputConnection mIC;
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
		mSingleton = this;
        super.onCreate(savedInstanceState);
		mRoot = new LinearLayout(this);
		mRoot.setOrientation(LinearLayout.VERTICAL);
		mEditor = new EditText(this);
		mRoot.addView(mEditor);
		setContentView(mRoot);
		if(FakeInputMethodService.mSingleton == null)
			startService( new Intent(this, SmartKeyboard.class));
		else
			onStartService();
    }
	public void onStartService()
	{
			View v = FakeInputMethodService.mSingleton.onCreateInputView();
			if(mContent != null)
				mRoot.removeView(mContent);
			if(v != null)
				mRoot.addView(v);
			mContent = v;
			EditorInfo attr = new EditorInfo();
			attr.inputType = EditorInfo.TYPE_CLASS_TEXT;
			attr.packageName = "com.android.settings";
			mIC = mEditor.onCreateInputConnection(attr);
			
			FakeInputMethodService.mSingleton.onStartInput(attr, false);
			FakeInputMethodService.mSingleton.onStartInputView(attr, false);
	}
}
