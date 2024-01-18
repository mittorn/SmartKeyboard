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
import android.accessibilityservice.*;
import android.view.accessibility.*;

public class FakeInputMethodService extends AccessibilityService // AbstractInputMethodService
{
	public static FakeInputMethodService mSingleton;
	public void setInputView (View view){}
	public void requestHideSelf (int flags){}
	boolean initialized;
	public void Initialize()
	{
		if(initialized)
			return;
		if(PicoActivity.mSingleton == null)
			return;
		onInitializeInterface();
		if(PicoActivity.mSingleton == null)
			return;
	}
	/*public void sendKeyChar (char charCode){
		PicoActivity.mSingleton.mIC.sendKeyChar( charCode)
	}*/
    public void sendKeyChar(char charCode) {
		updateText();
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
		updateText();
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
	//public Object getSystemService (String name){ return null;}
//	@Override
	public AbstractInputMethodService.AbstractInputMethodSessionImpl onCreateInputMethodSessionInterface (){ return null;}
//	@Override
	public AbstractInputMethodService.AbstractInputMethodImpl onCreateInputMethodInterface (){ return null;}
	public boolean onKeyMultiple (int keyCode, int count, KeyEvent event) { return false;}
	/*@Override
	public IBinder onBind(Intent intent) 
	{
		return null;
	}*/


	@Override
	public void onInterrupt(){}

	public AccessibilityNodeInfo mLastEditable;
	CharSequence mLastText;
	int mLastStart, mLastEnd;
	public void setText1(AccessibilityNodeInfo node, CharSequence text, int start, int end)
	{
		//mLastEditable.setText(text);
		//mLastEditable.setTextSelection(start, end);
		try {
		 Bundle arguments = new Bundle();
   		arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
		mLastEditable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
		}catch(Exception e){}
		try{
		mLastEditable.performAction(AccessibilityNodeInfo.ACTION_FOCUS);

		}catch(Exception e){}
				try{
		
			Bundle arguments = new Bundle();
			arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start);
			arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end);
			mLastEditable.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments);

		}catch(Exception e){}
	}
	public void setText(CharSequence text, int start, int end)
	{
		/*try {
		 Bundle arguments = new Bundle();
   arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
       "text");
		mLastEditable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
		}catch(Exception e){}*/
		mLastText = text;
		mLastStart = start;
		mLastEnd = end;
		setText1(mLastEditable, text, start, end);
	}

	void updateText()
	{
		setText(PicoActivity.mSingleton.mEditor.getText(), PicoActivity.mSingleton.mEditor.getSelectionStart(),PicoActivity.mSingleton.mEditor.getSelectionEnd() );
	}
	@Override
	public void onAccessibilityEvent(AccessibilityEvent e)
	{
		AccessibilityNodeInfo info = e.getSource();
		try{
		if(mLastText != null)
			updateText();
		}catch(Exception ee){}
		if(info == null) return;
		if(info.getPackageName().equals("com.dexilog.smartkeyboard"))
			return;
		if(info.isEditable())
		{
			String id = null, id1 = null;
			if(mLastEditable != null)
			{
				id = mLastEditable.getViewIdResourceName ();
			}
			if(id == null) id = "";
			if(mLastEditable != null)
			id = id + mLastEditable.getClassName() + mLastEditable.getPackageName();
			id1 = info.getViewIdResourceName ();
			if(id1 == null) id1 = "";
			id1 = id1 + info.getClassName() + info.getPackageName();

			Log.e("SmartKeyboard", info.toString());
			boolean reset = true;
			if(mLastText != null && id.equals(id1))
			{
				setText1(info, mLastText, mLastStart, mLastEnd);
				mLastText = null;
				reset = false;
			}
			int t = e.getEventType();
			if( t == AccessibilityEvent.TYPE_VIEW_FOCUSED || t == AccessibilityEvent.TYPE_VIEW_CLICKED )
			{
				mLastEditable = info;
				if(reset)
				{
					if(!info.isShowingHintText ())
						setText1(info, info.getText(), info.getTextSelectionStart(), info.getTextSelectionEnd());
					else
						setText1(info, "", 0, 0);
				}
				mLastText = null;
			}
			
		}
	}
	@Override
	public void onServiceConnected() {
		mSingleton = this;
		AccessibilityServiceInfo info = new AccessibilityServiceInfo();
		// Set the type of events that this service wants to listen to. Others
		// aren't passed to this service.
		info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED |
				AccessibilityEvent.TYPE_VIEW_FOCUSED | AccessibilityEvent.TYPE_WINDOWS_CHANGED | AccessibilityEvent.TYPE_VIEW_HOVER_EXIT | AccessibilityEvent.TYPE_VIEW_HOVER_ENTER | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED ;
	
	
		// Set the type of feedback your service provides.
		info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
	
		// Default services are invoked only if no package-specific services are
		// present for the type of AccessibilityEvent generated. This service is
		// app-specific, so the flag isn't necessary. For a general-purpose service,
		// consider setting the DEFAULT flag.
	
		 info.flags = 
			AccessibilityServiceInfo.DEFAULT | 
			AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | 
			AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
//			AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | // this breaks everything? todo: debug
0;
	
		info.notificationTimeout = 100;
	
		this.setServiceInfo(info);
		Initialize();
	
	}

	/*@Override
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
	}*/
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