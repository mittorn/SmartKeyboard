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
import android.preference.PreferenceManager;

public class FakeInputMethodService extends AccessibilityService // AbstractInputMethodService
{
	public static FakeInputMethodService mSingleton;
	public void setInputView (View view){}
	public void requestHideSelf (int flags){}
	boolean initialized;
	Handler mHandler;
	static final String TAG = "SmartKeyboard";
	static final int NUM_KEYS = 5;
	static final int ACTION_DOWN = 0;
	static final int ACTION_UP = 1;
	static final int ACTION_LONG = 2;
	static final int ACTION_DOUBLE = 3;
	static final int NUM_ACTIONS = 4;
	static ActionCallback[] mActionCallbacks = new ActionCallback[NUM_KEYS*NUM_ACTIONS];
	static boolean mAutoActivate = false;
	static long mLongPressDelay, mDoublePressDelay;
	static boolean mSkipSelf;
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
	class ActionCallback implements Runnable
	{
		public boolean return_value;
		public boolean pending; // set to false after long click acted
		void cb(){}
		public void run()
		{
			pending = false;
			cb();
		}
	}
	class ShellAction extends ActionCallback
	{
		String command;
		ShellAction(String s)
		{
			command = s;
		}
		void cb()
		{
			try{
				java.lang.Process process = Runtime.getRuntime().exec( new String[]{ "/system/bin/sh", "-c", command });
				process.waitFor();
			}
			catch(Exception e){e.printStackTrace();}
		}
	}
	class IntentAction extends ActionCallback
	{
		Intent intent;
		IntentAction(String s) throws Exception
		{
			if(s.startsWith("notify:"))
			{
				intent = new Intent();
				intent.setComponent(new android.content.ComponentName("com.dexilog.smartkeyboard","com.dexilog.smartkeyboard.NotifyActivity"));
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.putExtra("text", s.substring(7));
			}
			else
			{
			intent = Intent.parseUri(s, Intent.URI_ALLOW_UNSAFE | Intent.URI_INTENT_SCHEME);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);	
			}
		}
		void cb()
		{
			startActivity(intent);
		}
	}

	class KeyboardAction extends ActionCallback
	{
		FakeInputMethodService srv;
		KeyboardAction(FakeInputMethodService s)
		{
			srv = s;
		}
		void cb()
		{
			srv.startEditing();
		}
	}
	
	protected void loadSettings(SharedPreferences sp)
	{
		String[] keys = new String[]{"volup", "voldown", "camera", "sysr", "sysl"};
		String[] actions = new String[]{"down", "up", "long", "double"};
		for(int i = 0; i < NUM_KEYS; i++)
		{
			for(int j = 0; j < NUM_ACTIONS; j++)
			{
				String act = sp.getString("keymap_" + keys[i] + "_" + actions[j], "");
				ActionCallback r = null;
				boolean ret = true;
				// + disables key override
				if(act.startsWith("+"))
				{
					ret = false;
					act = act.substring(1);
				}
				try {
					final String KW_SH = "shell:";
					if(act.equals("keyboard"))
						r = new KeyboardAction(this);
					else if(act.equals("empty"))
						r = new ActionCallback();
					else if(act.startsWith("shell:"))
						r = new ShellAction(act.substring(KW_SH.length()));
					else if(act.length() > 0)
						r = new IntentAction(act);
					if( r != null )
						r.return_value = ret;
					Log.e(TAG, "LoadKeymap: " + keys[i] +" "+ actions[j] + " " + act + " _ " + (r != null? r.toString() : "NULL"));
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
				mActionCallbacks[NUM_ACTIONS * i + j] = r;
			}
		}
		mAutoActivate = sp.getBoolean("pico_auto_activate",false);
		mLongPressDelay = Integer.valueOf(sp.getString("keymapper_long_press_delay", "400"));
		mDoublePressDelay = Integer.valueOf(sp.getString("keymapper_double_press_delay", "300"));
	}
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

	public AccessibilityNodeInfo mLastEditable, mSelectedNode;
	CharSequence mLastText;
	int mLastStart, mLastEnd;
	// try to apply text to node (true on success)
	public boolean setText1(AccessibilityNodeInfo node, CharSequence text, int start, int end)
	{
		boolean r = false;
		try{
			// hack: try switch focus
			AccessibilityNodeInfo info1 = getRootInActiveWindow();
			info1.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
			node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);

		}catch(Exception e){}
		try {
			Bundle arguments = new Bundle();
			arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
			r = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
		}catch(Exception e){e.printStackTrace();}
				try{
		
			Bundle arguments = new Bundle();
			arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start);
			arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end);
			node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments);

		}catch(Exception e){}
		return r;
	}

	// save text from editor
	public void setText(CharSequence text, int start, int end)
	{
		mLastText = text;
		mLastStart = start;
		mLastEnd = end;
		//setText1(mLastEditable, text, start, end);
	}

	boolean mFullscreen;
	static final int TYPES_FULLSCREEN = AccessibilityEvent.TYPE_WINDOWS_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
	static final int TYPES_DEFAULT = AccessibilityEvent.TYPE_VIEW_CLICKED |
				AccessibilityEvent.TYPE_VIEW_FOCUSED | AccessibilityEvent.TYPE_WINDOWS_CHANGED | AccessibilityEvent.TYPE_VIEW_HOVER_EXIT | AccessibilityEvent.TYPE_VIEW_HOVER_ENTER | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;

	static final int PICO_FULLSCREEN_WIDTH = 4320, PICO_FULLSCREEN_HEIGHT = 2160;
	// sed key mapping and event handling rules based on current active window 
	public void updateForeground()
	{
		AccessibilityNodeInfo info = getRootInActiveWindow();
		if(info != null )
		{
			Log.e(TAG, info.toString());
			Rect bounds = new Rect();
			info.getBoundsInScreen(bounds);
			boolean newFullscreen = bounds.top == 0 && bounds.left == 0 && bounds.right == PICO_FULLSCREEN_WIDTH && bounds.bottom == PICO_FULLSCREEN_HEIGHT;
			if( newFullscreen != mFullscreen )
			{
				mFullscreen = newFullscreen;
				mInfo.eventTypes = newFullscreen ? TYPES_FULLSCREEN : TYPES_DEFAULT;
				setServiceInfo(mInfo);
				Log.e(TAG, "fullscreen: " + newFullscreen);
				// reset text input selection
				if(newFullscreen)
					mSelectedNode = null;
			}
		}
	}
	long mLastEventTime;
	@Override
	public boolean onKeyEvent(KeyEvent event) {
		int action = event.getAction();
		int keyCode = event.getKeyCode();
		Log.e(TAG, "Key " + action + " " + keyCode);
		if(action == 0)
			updateForeground();

		if(!mFullscreen)
		{
			int key_idx = -1;
			boolean ret = false;
			switch(keyCode)
			{
				case 24:
					key_idx = 0;
					break;
				case 25:
					key_idx = 1;
					break;
				case 27:
					key_idx = 2;
					break;
				case 902:
					key_idx = 3;
					break;
				case 901:
					key_idx = 4;
					break;
			}
			if(key_idx < 0)
				return false;
			ActionCallback l = mActionCallbacks[NUM_ACTIONS * key_idx + ACTION_LONG];
			if(l != null)
			{
				try{
					// long callback sets return, but maybe overriden by up/down
					ret = l.return_value;
					if(action == ACTION_DOWN) // setup long/double handlers
						mHandler.postDelayed(l, mLongPressDelay);
					else
					{
						mHandler.removeCallbacks(l);
						// if callback is called, skip double/up processing
						if(!l.pending)
							return ret;
						l.pending = false;
					}
				}catch(Exception e){}
			}
			ActionCallback d = mActionCallbacks[NUM_ACTIONS * key_idx + ACTION_DOUBLE];
			ActionCallback r = mActionCallbacks[NUM_ACTIONS * key_idx + action];
			if( d != null )
			{
				long tm = event.getEventTime();
				if(tm - mLastEventTime < mDoublePressDelay)
				{
					if(action == ACTION_DOWN)
						d.run();
					// always run down/up callback if it is not intercepting
					// note: intercepting down event may be broken when double enabled, but is it even needed?
					if(r != null && !r.return_value)
						r.run();
					// cancel intercepting up event
					if(mActionCallbacks[NUM_ACTIONS * key_idx + ACTION_UP] != null)
					{
						try{
							mHandler.removeCallbacks(mActionCallbacks[NUM_ACTIONS * key_idx + ACTION_UP]);
						}catch(Exception e){}
					}
					return d.return_value;
				}
				else if(action == ACTION_UP)
				{
					// if UP action is intercepting, post it delayed, so it may be canceled
					if(r != null && r.return_value)
					{
						mHandler.postDelayed(r,mDoublePressDelay);
						return true;
					}
				}
				if(action == ACTION_DOWN)
					mLastEventTime = tm;
			}
			

			Log.e(TAG, "key_idx " + key_idx + " " + String.valueOf(r));
			if(r != null)
			{
				r.run();
				ret = r.return_value;
			}
			return ret;
		}
		//else Log.e(TAG, "skipping fullscreen event!");
		
		return super.onKeyEvent(event);

	}

	// get text from editor
	void updateText()
	{
		setText(PicoActivity.mSingleton.mEditor.getText(), PicoActivity.mSingleton.mEditor.getSelectionStart(),PicoActivity.mSingleton.mEditor.getSelectionEnd() );
	}

	// generate some reliable (hope) unique id to paste text only to same input field
	String getIdFromNode(AccessibilityNodeInfo node)
	{
		String id = node.getPackageName() + "/" + node.getClassName();
		String rid = node.getViewIdResourceName();
		if(rid != null)
		id += "/" + rid;
		return id;
	}

	// update pending editable nodes (if old editble failed), select new nodes
	void handleEditableNode(AccessibilityNodeInfo node, int t)
	{
		if(!node.isEditable())
			return;
		// if we still have penging text (maybe mLastEditable not useful) paste text to any hovered field with same id
		String id = getIdFromNode(node);
		if(mLastText != null && mLastEditable != null && id.equals(getIdFromNode(mLastEditable)))
		{
			setText1(node, mLastText, mLastStart, mLastEnd);
			mLastText = null;
		}
		if(mAutoActivate && mSelectedNode != null && t == AccessibilityEvent.TYPE_VIEW_CLICKED &&  getIdFromNode(mSelectedNode).equals(id))
		{
			startEditing();
			return;
		}
			
		if( t == AccessibilityEvent.TYPE_VIEW_FOCUSED || t == AccessibilityEvent.TYPE_VIEW_CLICKED )
			mSelectedNode = node;
	}

	// start editor on selected node
	boolean startEditing()
	{
		boolean r;
		if(mSelectedNode == null)
			return false;
		AccessibilityNodeInfo info = mSelectedNode;
		info.refresh();
		if(!info.isShowingHintText())
			r = setText1(info, info.getText(), info.getTextSelectionStart(), info.getTextSelectionEnd());
		else
			r = setText1(info, "", 0, 0);
		if(!r)
			return false;
		mLastEditable = info;
		mSelectedNode = null;
		mSkipSelf = true;
		Intent i = new Intent(this,PicoActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(i);
		return true;
		
	}

	// set text on pending editor node
	void finishEditing()
	{
		mSkipSelf = false;
		if((mLastText != null) && setText1(mLastEditable, mLastText, mLastStart, mLastEnd))
			mLastText = null;
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent e)
	{
		String pkg = e.getPackageName().toString();
		int t = e.getEventType();
		// Log.e(TAG, e.toString());

		// do not handle events in editor
		if(mSkipSelf && pkg.equals("com.dexilog.smartkeyboard"))
			return;

		AccessibilityNodeInfo info = e.getSource();

		// as soon editor closed, try update text in old node
		try{
			finishEditing();
		}catch(Exception ee){}
		if(info == null) return;
		// Log.e(TAG, info.toString());
		handleEditableNode(info, t);
	}

	AccessibilityServiceInfo mInfo;
	@Override
	public void onServiceConnected() {
		mSingleton = this;
		mHandler = new Handler(Looper.getMainLooper());
		mInfo = new AccessibilityServiceInfo();
		// Set the type of events that this service wants to listen to. Others
		// aren't passed to this service.
		mInfo.eventTypes = TYPES_DEFAULT;
	
	
		// Set the type of feedback your service provides.
		mInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
	
		// Default services are invoked only if no package-specific services are
		// present for the type of AccessibilityEvent generated. This service is
		// app-specific, so the flag isn't necessary. For a general-purpose service,
		// consider setting the DEFAULT flag.
	
		mInfo.flags = 
			AccessibilityServiceInfo.DEFAULT | 
			AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | 
			AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
			AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS |
//			AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | // this breaks everything? todo: debug
0;
	
		mInfo.notificationTimeout = 100;
	
		setServiceInfo(mInfo);
		Initialize();
		loadSettings(PreferenceManager.getDefaultSharedPreferences(this));
	
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