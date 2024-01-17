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

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Message;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import com.android.inputmethod.voice.FieldContext;
import com.android.inputmethod.voice.VoiceInput;
import com.dexilog.smartkeyboard.settings.PermissionManager;
import com.google.android.voiceime.VoiceRecognitionTrigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VoiceInputController {
    private final SmartKeyboard smartKeyboard;
    boolean initialized = false;
    boolean mRecognizing;
    public boolean mVoiceInputHighlighted;
    boolean mHasUsedVoiceInput;
    boolean mHasUsedVoiceInputUnsupportedLocale;
    boolean mLocaleSupportedForVoiceInput = true;
    boolean mRestartVoice;
    boolean mVoiceBest;
    boolean mLegacyVoice;
    private VoiceResults mVoiceResults = new VoiceInputController.VoiceResults();

    private VoiceInput mVoiceInput;
    private VoiceRecognitionTrigger mVoiceRecognitionTrigger;
    private AlertDialog mVoiceWarningDialog;

    public VoiceInputController(SmartKeyboard smartKeyboard) {
        this.smartKeyboard = smartKeyboard;
    }

    private void initialize() {
        if (!initialized) {
            try {
                // Available only on Android 2.2
                mVoiceInput = new VoiceInput(smartKeyboard, smartKeyboard);
            } catch (Throwable t) {
                Log.i("SKP", "No voice input API, fallback to the old one");
            }
        }
        initialized = true;
    }

    void revertVoiceInput() {
        InputConnection ic = smartKeyboard.getCurrentInputConnection();
        if (ic != null)
            ic.commitText("", 1);
        smartKeyboard.updateSuggestions();
        mVoiceInputHighlighted = false;
    }

    public void commitVoiceInput() {
        InputConnection ic = smartKeyboard.getCurrentInputConnection();
        if (ic != null)
            ic.finishComposingText();
        smartKeyboard.updateSuggestions();
        mVoiceInputHighlighted = false;
    }

    void startListening(boolean swipe) {
        if (!mHasUsedVoiceInput
                || (!mLocaleSupportedForVoiceInput && !mHasUsedVoiceInputUnsupportedLocale)) {
            // Calls reallyStartListening if user clicks OK, does nothing if
            // user clicks Cancel.
            showVoiceWarningDialog(swipe);
        } else {
            reallyStartListening(swipe);
        }
    }

    void reallyStartListening(boolean swipe) {
        if (!mHasUsedVoiceInput) {
            // The user has started a voice input, so remember that in the
            // future (so we don't show the warning dialog after the first run).
            SharedPreferences.Editor editor = smartKeyboard.mSharedPref.edit();
            editor.putBoolean(KeyboardPreferences.PREF_HAS_USED_VOICE_INPUT, true);
            editor.commit();
            mHasUsedVoiceInput = true;
        }

        if (!mLocaleSupportedForVoiceInput
                && !mHasUsedVoiceInputUnsupportedLocale) {
            // The user has started a voice input from an unsupported locale, so
            // remember that
            // in the future (so we don't show the warning dialog the next time
            // they do this).
            SharedPreferences.Editor editor = smartKeyboard.mSharedPref.edit();
            // editor.putBoolean(PREF_HAS_USED_VOICE_INPUT_UNSUPPORTED_LOCALE,
            // true);
            editor.commit();
            mHasUsedVoiceInputUnsupportedLocale = true;
        }

        // Clear N-best suggestions
        smartKeyboard.setSuggestions(null, false, false, true);

        FieldContext context = new FieldContext(smartKeyboard.getCurrentInputConnection(),
                smartKeyboard.getCurrentInputEditorInfo(), smartKeyboard.mKeyboardSwitcher.getVoiceLang()
                .toLowerCase(), null);
        mVoiceInput.startListening(context, mVoiceBest ? 1 : 5);
        smartKeyboard.switchToRecognitionStatusView();
    }

    void showVoiceWarningDialog(final boolean swipe) {
        AlertDialog.Builder builder = new AlertDialog.Builder(smartKeyboard);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.ic_mic_dialog);
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        reallyStartListening(swipe);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                });

        if (mLocaleSupportedForVoiceInput) {
            String message = smartKeyboard.getString(R.string.voice_warning_may_not_understand)
                    + "\n\n"
                    + smartKeyboard.getString(R.string.voice_warning_how_to_turn_off);
            builder.setMessage(message);
        } else {
            String message = smartKeyboard.getString(R.string.voice_warning_locale_not_supported)
                    + "\n\n"
                    + smartKeyboard.getString(R.string.voice_warning_may_not_understand)
                    + "\n\n"
                    + smartKeyboard.getString(R.string.voice_warning_how_to_turn_off);
            builder.setMessage(message);
        }

        builder.setTitle(R.string.voice_warning_title);
        mVoiceWarningDialog = builder.create();

        Window window = mVoiceWarningDialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = smartKeyboard.mKeyboardSwitcher.getMainKeyboardView().getWindowToken();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mVoiceWarningDialog.show();
    }

    public void onVoiceResults(List<String> candidates,
                               Map<String, List<CharSequence>> alternatives) {
        if (!mRecognizing) {
            return;
        }
        mVoiceResults.candidates = candidates;
        mVoiceResults.alternatives = alternatives;
        smartKeyboard.mHandler.sendMessage(smartKeyboard.mHandler.obtainMessage(SmartKeyboard.MSG_VOICE_RESULTS));
    }

    void handleVoiceResults() {
        InputConnection ic = smartKeyboard.getCurrentInputConnection();
        if (!smartKeyboard.isFullscreenMode()) {
            // Start listening for updates to the text from typing, etc.
            if (ic != null) {
                ExtractedTextRequest req = new ExtractedTextRequest();
                ic.getExtractedText(req,
                        InputConnection.GET_EXTRACTED_TEXT_MONITOR);
            }
        }

        smartKeyboard.vibrate(0);
        smartKeyboard.switchToKeyboardView();

        final ArrayList<String> nBest = new ArrayList<String>();
        for (String c : mVoiceResults.candidates) {
            nBest.add(c);
        }

        if (nBest.size() == 0) {
            return;
        }

        if (nBest.size() > 1) {
            Message msg = smartKeyboard.mHandler.obtainMessage(SmartKeyboard.MSG_SEND_VOICE_TEXT);
            msg.getData().putStringArrayList("results", nBest);
            smartKeyboard.mHandler.removeMessages(SmartKeyboard.MSG_SEND_VOICE_TEXT);
            smartKeyboard.mHandler.sendMessageDelayed(msg, 100);
        } else {

            String bestResult = nBest.get(0).toString();

            if (ic != null)
                ic.beginBatchEdit(); // To avoid extra updates on committing
            // older text

            smartKeyboard.suggestController.commitTyped(ic);
            smartKeyboard.sendText(bestResult);

            // EditingUtil.appendText(ic, bestResult);

            if (ic != null)
                ic.endBatchEdit();
        }

		/*
         *
		 *
		 * //mHints.registerVoiceResult(bestResult);
		 *
		 * if (ic != null) ic.beginBatchEdit(); // To avoid extra updates on
		 * committing older text
		 *
		 * commitTyped(ic); EditingUtil.appendText(ic, bestResult);
		 *
		 * if (ic != null) ic.endBatchEdit();
		 *
		 * // Show N-Best alternates, if there is more than one choice. if
		 * (nBest.size() > 1) { mImmediatelyAfterVoiceSuggestions = true;
		 * mShowingVoiceSuggestions = true; setSuggestions(nBest.subList(1,
		 * nBest.size()), false, true, true); setCandidatesViewShown(true); }
		 */
        mVoiceInputHighlighted = true;
        smartKeyboard.mWordToSuggestions.putAll(mVoiceResults.alternatives);

    }

    void displayVoiceResult(List<String> result) {
        // Show list dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(smartKeyboard);
        builder.setTitle("Smart Keyboard");
        final String[] items = new String[result.size()];
        result.toArray(items);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                String choice = items[item];
                smartKeyboard.sendText(choice);
                mVoiceInputHighlighted = true;
            }
        });
        final Resources res = smartKeyboard.getResources();
        builder.setPositiveButton(res.getString(R.string.retry),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        voiceSearch();
                    }
                });
        builder.setNegativeButton(res.getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
        AlertDialog alert = builder.create();
        Window window = alert.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.token = smartKeyboard.mKeyboardSwitcher.getMainKeyboardView().getWindowToken();
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            try {
                alert.show();
            } catch (Exception e) {
                Log.e(SmartKeyboard.TAG, "Exception caught in displayVoiceResult!");
                e.printStackTrace();
                return;
            }
            if (mVoiceBest && result.size() > 0) {
                alert.dismiss();
                smartKeyboard.sendText(items[0]);
                mVoiceInputHighlighted = true;
            }
        } else {
            Log.e(SmartKeyboard.TAG, "Null window for alert!");
        }
    }

    public void voiceSearch() {

        initialize();

        if (!mLegacyVoice) {
            VoiceRecognitionTrigger voiceRecognitionTrigger = getVoiceRecognitionTrigger();
            if (voiceRecognitionTrigger.isInstalled())
                voiceRecognitionTrigger.startVoiceRecognition(smartKeyboard.mKeyboardSwitcher.getVoiceLang().toLowerCase());
            else
                displayVoiceNotInstalled();
        } else {
            PermissionManager.get(smartKeyboard).checkRecordingPermission(new PermissionManager.PermissionsResultCallback() {
                @Override
                public void onRequestPermissionsResult(boolean allGranted) {
                    if (allGranted) {
                        if (!startLegacyVoiceInput()) {
                            displayVoiceNotInstalled();
                        }
                    } else {
                        Log.i("Voice Input", "No mic permission");
                    }
                }
            });
        }
    }

    private VoiceRecognitionTrigger getVoiceRecognitionTrigger() {
        if (mVoiceRecognitionTrigger == null) {
            //mVoiceRecognitionTrigger = new VoiceRecognitionTrigger(smartKeyboard);
        }
        return mVoiceRecognitionTrigger;
    }

    private void displayVoiceNotInstalled() {
        AlertDialog.Builder builder = new AlertDialog.Builder(smartKeyboard);
        builder.setTitle("Smart Keyboard");
        builder.setMessage("Voice recognition is not installed on this phone! You should disable the mic button option.");
        builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                // TODO Auto-generated method stub
            }
        });
        AlertDialog alert = builder.create();
        Window window = alert.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = smartKeyboard.mKeyboardSwitcher.getMainKeyboardView().getWindowToken();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        alert.show();
    }

    private boolean startLegacyVoiceInput() {
        boolean voiceFound = false;
        if (mVoiceInput != null) {
            startListening(false);
            voiceFound = true;
        } else {
            // Old API
            // TODO still needed??
            // Check to see if a recognition activity is present
            PackageManager pm = smartKeyboard.getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
            if (activities.size() != 0) {
                // Callback intent
                Intent doneIntent = new Intent(SmartKeyboard.ACTION_RECOGNITION_DONE);
                PendingIntent pendingDoneIntent = PendingIntent.getBroadcast(
                        smartKeyboard, 0, doneIntent, PendingIntent.FLAG_ONE_SHOT);

                // Create speech intent
                Intent speechIntent = new Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                speechIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                final String lang = smartKeyboard.mKeyboardSwitcher.getVoiceLang()
                        .toLowerCase();
                speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
                speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                speechIntent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                        "Speak slowly for better results");
                speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
                speechIntent.putExtra(
                        RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT,
                        pendingDoneIntent);
                Bundle doneBundle = new Bundle();
                speechIntent.putExtra(
                        RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT_BUNDLE,
                        doneBundle);

                smartKeyboard.startActivity(speechIntent);
                voiceFound = true;
            }
        }
        return voiceFound;
    }

    public void voiceStartInputView() {
        if (!mLegacyVoice) {
            getVoiceRecognitionTrigger().onStartInputView();
        }
    }

    public void cancelVoiceInput(SmartKeyboard smartKeyboard) {
        if (mVoiceInput != null && !smartKeyboard.mConfigurationChanging) {
            mVoiceInput.cancel();
        }
    }

    void voiceHideWindow() {
        if (mVoiceWarningDialog != null && mVoiceWarningDialog.isShowing()) {
            mVoiceWarningDialog.dismiss();
            mVoiceWarningDialog = null;
        }
        if (mVoiceInput != null && mRecognizing) {
            mVoiceInput.cancel();
        }
    }

    void voiceSwitchToRecognitionStatus(boolean configChanged) {
        mRecognizing = true;
        if (mVoiceInput != null) {
            View v = mVoiceInput.getView();
            ViewParent p = v.getParent();
            if (p != null && p instanceof ViewGroup) {
                ((ViewGroup) v.getParent()).removeView(v);
            }
            smartKeyboard.setInputView(v);
            smartKeyboard.updateInputViewShown();
            if (configChanged) {
                mVoiceInput.onConfigurationChanged();
            }
        }
    }

    private class VoiceResults {
        List<String> candidates;
        Map<String, List<CharSequence>> alternatives;
    }
}