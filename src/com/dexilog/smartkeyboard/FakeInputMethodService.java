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
public class FakeInputMethodService extends Service // AbstractInputMethodService
{
	public void setInputView (View view){}
	public void requestHideSelf (int flags){}
	public void sendKeyChar (char charCode){}
	public InputConnection getCurrentInputConnection (){ return null;}
	public Object getSystemService (String name){ return null;}
//	@Override
	public AbstractInputMethodService.AbstractInputMethodSessionImpl onCreateInputMethodSessionInterface (){ return null;}
//	@Override
	public AbstractInputMethodService.AbstractInputMethodImpl onCreateInputMethodInterface (){ return null;}
	public boolean onKeyMultiple (int keyCode, int count, KeyEvent event) { return false;}
	@Override
	public IBinder onBind(Intent intent) 
	{
		return null;
	}
	public LayoutInflater getLayoutInflater (){ return null;}
	public boolean isFullscreenMode ()
	{
		return false;
	}

	public void onFinishInput ()
	{
	}
	public void onFinishInputView (boolean finishingInput){}
	public void onUpdateExtractedText (int token, ExtractedText text) {}
public void onUpdateSelection (int oldSelStart, 
                int oldSelEnd, 
                int newSelStart, 
                int newSelEnd, 
                int candidatesStart, 
                int candidatesEnd){}
	public void hideStatusIcon (){}
	public void hideWindow (){}
	public boolean onEvaluateFullscreenMode (){ return false; }
	public EditorInfo getCurrentInputEditorInfo () { return null;}
	public boolean onEvaluateInputViewShown () { return false; }
	public void onComputeInsets (InputMethodService.Insets outInsets) {}
	public void sendDownUpKeyEvents (int keyEventCode) {}
	public boolean onKeyDown (int keyCode, KeyEvent event) { return false; }
	public boolean onKeyUp (int keyCode, KeyEvent event) { return false; }
	public boolean sendDefaultEditorAction (boolean fromEnterKey) { return false; }
	public void updateInputViewShown () {}
	public void showStatusIcon (int iconResId) {}
	public void onInitializeInterface() {}
	public View onCreateInputView() { return null; }
	public void setCandidatesView(final View view) {}
	public void onStartInput(EditorInfo attribute, boolean restarting) {}
	public void onStartInputView(EditorInfo attribute, boolean restarting){}
	public void onDisplayCompletions(CompletionInfo[] completions) {}
	public void setCandidatesViewShown(boolean shown) {}
	public void onBindInput() {}
	public void onUnbindInput() {}
}