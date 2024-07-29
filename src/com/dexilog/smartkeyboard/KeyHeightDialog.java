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

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.DialogPreference;  
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.SeekBar;


public class KeyHeightDialog extends DialogPreference {	
    private Context mContext; 
    private SeekBar mBarPortrait;
    private SeekBar mBarLandscape;
    private SharedPreferences mPref;
    
    public KeyHeightDialog(Context context, AttributeSet attrs) { 
        super(context, attrs); 
        mContext = context;
        mPref = PreferenceManager.getDefaultSharedPreferences(context);
    } 
    
    protected void onPrepareDialogBuilder(Builder builder) {
    	LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    	View view = inflater.inflate(R.layout.key_height, null);
    	
    	// Set listeners
    	mBarPortrait = (SeekBar)view.findViewById(R.id.portrait);
    	mBarLandscape = (SeekBar)view.findViewById(R.id.landscape);
    	setButtonListener(view, R.id.button_portrait, mBarPortrait);
    	setButtonListener(view, R.id.button_landscape, mBarLandscape);
    	
    	// Set initial values
    	mBarPortrait.setProgress(getPersistedInt(50));
    	mBarLandscape.setProgress(mPref.getInt(KeyboardPreferences.PREF_KEY_HEIGHT_LANDSCAPE, 73));
    	
        builder.setView(view); 
    }
    
    private void setButtonListener(View view, int buttonId, final SeekBar bar) {
    	ImageView button = (ImageView)view.findViewById(buttonId);
    	button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Reset the bar to default value
				bar.setProgress(50);
			}
    		
    	});
    }
    
    protected void onDialogClosed(boolean positiveResult) { 
        if(positiveResult){
        	// Save portrait value
            persistInt(mBarPortrait.getProgress());
            // Save landscape value
            mPref.edit().putInt(KeyboardPreferences.PREF_KEY_HEIGHT_LANDSCAPE, mBarLandscape.getProgress())
            			.commit();
        } 
        super.onDialogClosed(positiveResult);
    }
}
