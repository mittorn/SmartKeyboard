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
	public static FakeInputMethodService mSingleton;
	public void setInputView (View view){}
	public void requestHideSelf (int flags){}
	/*public void sendKeyChar (char charCode){
		PicoActivity.mSingleton.mIC.sendKeyChar( charCode)
	}*/
    public void sendKeyChar(char charCode) {
        switch (charCode) {
            case '\n': // Apps may be listening to an enter key to perform an action
                if (!sendDefaultEditorAction(true)) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
                }
                break;
            default:
                // Make sure that digits go through any text watcher on the client side.
                if (charCode >= '0' && charCode <= '9') {
                    sendDownUpKeyEvents(charCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.commitText(String.valueOf((char) charCode), 1);
                    }
                }
                break;
        }
    }
    public void sendDownUpKeyEvents(int keyEventCode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long eventTime = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyEventCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD|KeyEvent.FLAG_KEEP_TOUCH_MODE));
        ic.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyEventCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD|KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }
    

	public InputConnection getCurrentInputConnection (){
		return PicoActivity.mSingleton.mIC;
	}
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
	@Override
	public void onStart (Intent intent, 
                int startId)
	{
		mSingleton = this;

		try{
			onInitializeInterface();
			if(PicoActivity.mSingleton == null)
				return;
			PicoActivity.mSingleton.onStartService();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	public LayoutInflater getLayoutInflater (){ return PicoActivity.mSingleton.getLayoutInflater();}
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
	public EditorInfo getCurrentInputEditorInfo () { return PicoActivity.mSingleton.mAttributes;}
	public boolean onEvaluateInputViewShown () { return true; }
	public void onComputeInsets (InputMethodService.Insets outInsets) {}
	//public void sendDownUpKeyEvents (int keyEventCode) {}
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