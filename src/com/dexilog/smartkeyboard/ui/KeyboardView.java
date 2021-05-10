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

package com.dexilog.smartkeyboard.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Paint.Align;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup.LayoutParams;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.dexilog.smartkeyboard.keyboard.CustomKeys;
import com.dexilog.smartkeyboard.keyboard.Keyboard;
import com.dexilog.smartkeyboard.keyboard.Keyboard.Key;
import com.dexilog.smartkeyboard.utils.CompatUtils;
import com.dexilog.smartkeyboard.utils.Workarounds;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.dexilog.smartkeyboard.ui.SkinLoader.SkinInfo;

import com.dexilog.smartkeyboard.R;

import static com.dexilog.smartkeyboard.keyboard.Keyboard.KEYCODE_EMOJI_NUM;
import com.dexilog.smartkeyboard.keyboard.*;


/**
 * A view that renders a virtual {@link Keyboard}. It handles rendering of keys and
 * detecting key presses and touch movements.
 */
public class KeyboardView extends View implements View.OnClickListener {

	private static final String TAG = "SmartKeyboard";
	private static final boolean DEBUG = false;
	protected static final int NOT_A_KEY = -1;
	private static final int[] LONG_PRESSABLE_STATE_SET = { android.R.attr.state_long_pressable };
	public static final int CODE_LANG = -2;

	protected Keyboard mKeyboard;
	private CustomKeys mCustomKeys;
	private int mCurrentKeyIndex = NOT_A_KEY;
	private int mLabelTextSize;
	private int mAltLabelSize;
	private int mKeyTextSize[] = new int[4];
	private int mKeyTextColor;
	private int mPressedTextColor;
	private int mAltLabelColor;
	private int mTextAltColor;
	private float mShadowRadius;
	private float mAltShadowRadius;
	private float mModShadowRadius;
	private Integer mShadowColor;
	private Integer mAltShadowColor;
	private Integer mModShadowColor;
	//private float mBackgroundDimAmount;
	private boolean mDisplayAltLabels = false;
	private boolean mAccentsPriority = false;
	
	private TextView mPreviewText;
	private PopupWindow mPreviewPopup;
	private int mPreviewTextSizeLarge;
	private int mPreviewOffset;
	private int mPreviewHeight;
	private int[] mOffsetInWindow;

	private PopupWindow mPopupKeyboard;
	private View mMiniKeyboardContainer;
	private KeyboardView mMiniKeyboard;
	private boolean mMiniKeyboardOnScreen;
	private View mPopupParent;
	private int mMiniKeyboardOffsetX;
	private int mMiniKeyboardOffsetY;
	private Map<Key,View> mMiniKeyboardCache;
	private int[] mWindowOffset;
	protected Key[] mKeys;

	/** Listener for {@link OnKeyboardActionListener}. */
	private OnKeyboardActionListener mKeyboardActionListener;

	private static final int MSG_SHOW_PREVIEW = 1;
	private static final int MSG_REMOVE_PREVIEW = 2;
	private static final int MSG_REPEAT = 3;
	private static final int MSG_LONGPRESS = 4;
	private static final int MSG_POP_EVENTS = 5;
	private static final int MSG_DISMISS_POPUP = 6;

	private static final int DELAY_BEFORE_PREVIEW = 0;
	private static final int DELAY_AFTER_PREVIEW = 70;

	private int mHorizontalCorrection = 0;
	private int mVerticalCorrection = 0;
	private int mSpaceCorrection = 0;
	private int mProximityThreshold;
	private boolean mCompactOrT9Layout = false;

	private boolean mPreviewCentered = false;
	private boolean mShowPreview = true;
	private boolean mShowTouchPoints = false;
	private boolean mSlidePopup = true;
	private boolean mSpacePreview = false;
	private int mPopupPreviewX;
	private int mPopupPreviewY;
	private boolean mNoAltPreview = false;

	private int mShownPointer = -1;
	private int mLastX;
	private int mLastY;
	private int mStartX;
	private int mStartY;

	private boolean mProximityCorrectOn;
    /** 
     * Whether multi-touch disambiguation needs to be disabled for any reason. There are 2 reasons
     * for this to happen - (1) if a real multi-touch event has occured and (2) we've opened an 
     * extension keyboard.
     */
    private boolean mDisableDisambiguation = false;
    /** Whether we've started dropping move events because we found a big jump */
    private boolean mDroppingEvents = false;
    /** The distance threshold at which we start treating the touch session as a multi-touch */
    private int mJumpThresholdSquare = Integer.MAX_VALUE;
    /** The y coordinate of the last row */
    private int mLastRowY;


	private Paint mPaint;
	private Rect mPadding;

	private long mDownTime;
	private long mLastMoveTime;
	private int mLastKey;
	private int mLastCodeX;
	private int mLastCodeY;
	private int mCurrentKey = NOT_A_KEY;
	private int mDownKey = NOT_A_KEY;
	private long mLastKeyTime;
	private long mCurrentKeyTime;
	private int[] mKeyIndices = new int[12];
	private GestureDetector mGestureDetector;
	private int mPopupX;
	private int mPopupY;
	private int mRepeatKeyIndex = NOT_A_KEY;
	private int mPopupLayout;
	private boolean popupKeyboardDisabled = false;
	private boolean mAbortKey;
	private Key mInvalidatedKey;
	private Rect mClipRegion = new Rect(0, 0, 0, 0);
	private boolean mPossiblePoly;
	private SwipeTracker mSwipeTracker = new SwipeTracker();
	private int mSwipeThreshold;
	private boolean mDisambiguateSwipe;
	private Keyboard mLangPopup;

	// Variables for dealing with multiple pointers
	private int mOldPointerCount = 1;
	private float mOldPointerX;
	private float mOldPointerY;

	private Drawable mKeyBackground;
	private Drawable mAltKeyBackground;
	private Drawable mDeleteKey;
	private Drawable mReturnKey;
	private Drawable mSearchKey;
	private Drawable mMicKey;
	private Drawable mShiftKey;
	private Drawable mSpaceKey;
	private Drawable mLeftArrow;
	private Drawable mRightArrow;
	private Drawable mUpArrow;
	private Drawable mDownArrow;
	private boolean mAlwaysCaps;
	private Typeface mLabelFont;
	private Typeface mAltLabelFont;
	private boolean mSmallKeys;
	private Drawable mBackground;
	private SkinInfo mPopupSkin;
	private int mKeyPadding;

	private static final int REPEAT_INTERVAL = 50; // ~20 keys per second
	private static final int REPEAT_START_DELAY = 400;
	private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
	private int mLongpressDuration;
	private int mSwipeFactor = 30;

	protected static int MAX_NEARBY_KEYS = 12;
	private int[] mDistances = new int[MAX_NEARBY_KEYS];

	// For multi-tap
	private int mLastSentIndex;
	private int mTapCount;
	private long mLastTapTime;
	private boolean mInMultiTap;
	private int mMultitapInterval;
	private StringBuilder mPreviewLabel = new StringBuilder(1);
	
	private Paint mBgPaint;
	private boolean mTransparency = false;

	/** Whether the keyboard bitmap needs to be redrawn before it's blitted. **/
	private boolean mDrawPending;
	/** The dirty region in the keyboard bitmap */
	private Rect mDirtyRect = new Rect();
	/** The keyboard bitmap for faster updates */
	private Bitmap mBuffer;
	/** Notes if the keyboard just changed, so that we could possibly reallocate the mBuffer. */
	private boolean mKeyboardChanged;
	/** The canvas for the above mutable keyboard bitmap */
	private Canvas mCanvas;

	static private boolean mHasMultiTouchAPI;
	private boolean mMultiTouchAvail;
	static final int mActionPointer1Down = 5;
	static final int mActionPointer1Up = 6;
	static final int mActionPointer2Down = 261;
	static final int mActionPointer2Up = 262;
	
	int mLocation[] = {0, 0};
	int mParentLocation[] = {0, 0};
	// Pending events for preview popup
	private LinkedList<MotionEvent> mPendingEvents = new LinkedList<MotionEvent>(); 
	public boolean mSlideMode = false; // Slide mode for the popup keyboard
	public volatile boolean mAttached = false; // True when attached to window
	float mFirstX = -1;
	int mCenterX = -1;
	private boolean customSmileys;


	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_SHOW_PREVIEW:
				showKey(msg.arg1);
				break;
			case MSG_REMOVE_PREVIEW:
				mPreviewText.setVisibility(INVISIBLE);
				mPreviewPopup.dismiss();
				break;
			case MSG_REPEAT:
				if (repeatKey()) {
					Message repeat = Message.obtain(this, MSG_REPEAT);
					sendMessageDelayed(repeat, REPEAT_INTERVAL);                        
				}
				break;
			case MSG_LONGPRESS:
				openPopupIfRequired((MotionEvent) msg.obj);
				break;
			case MSG_POP_EVENTS:
				// Try to dequeue events
				if (mAttached) {
					popEvents();
				} else {
					// Otherwise retry again later
					if (DEBUG) Log.d(TAG, "xxxxx try again later");
					sendMessageDelayed(Message.obtain(this, MSG_POP_EVENTS), 5);
				}
				break;
			case MSG_DISMISS_POPUP:
				dismissPopupKeyboard();
				break;
			}
		}
	};
	
	static {
		// Check if android >= 2.0
		try {
			MotionEventWrapper.checkAvailable();
			mHasMultiTouchAPI = true;
		} catch (Throwable t) {
			Log.i(TAG, "No multitouch API");
			mHasMultiTouchAPI = false;
		}
	}

	public KeyboardView(Context context, AttributeSet attrs) {
		this(context, attrs, R.style.Android);
	}

	public KeyboardView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		LayoutInflater inflate =
			(LayoutInflater) context
			.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		int previewLayout = 0;
		int keyTextSize = 0;

		Resources resources = context.getResources();

		mVerticalCorrection = resources.getDimensionPixelSize(R.dimen.vertical_correction);
		mSpaceCorrection = resources.getDimensionPixelOffset(R.dimen.spacebar_vertical_correction);
		mPreviewOffset = 0;
		mPreviewHeight = resources.getDimensionPixelSize(R.dimen.preview_height);
		mLabelTextSize = (int)(resources.getDimension(R.dimen.label_text_size) * GlobalResources.mFontScale);
		mAltLabelSize = (int)(resources.getDimension(R.dimen.alt_label_size) * GlobalResources.mFontScale);
		mPopupLayout = R.layout.keyboard_popup_keyboard;
		mShadowColor = null;
		mAltShadowColor = null;
		mModShadowColor = null;

		mShadowRadius = resources.getDimension(R.dimen.shadow_radius);
		mAltShadowRadius = resources.getDimension(R.dimen.alt_shadow_radius);
		mModShadowRadius = resources.getDimension(R.dimen.mod_shadow_radius);
		//mBackgroundDimAmount = 0.3f;
		previewLayout = R.layout.keyboard_key_preview;

		mPreviewPopup = new PopupWindow(context);
		CompatUtils.setPopupUnattachedToDecor(mPreviewPopup);
		if (previewLayout != 0) {
			mPreviewText = (TextView) inflate.inflate(previewLayout, null);
			mPreviewTextSizeLarge = (int) mPreviewText.getTextSize();
			mPreviewPopup.setContentView(mPreviewText);
			mPreviewPopup.setBackgroundDrawable(null);
			int height = resources.getDimensionPixelSize(R.dimen.preview_height);
			LayoutParams dim = new LayoutParams(LayoutParams.WRAP_CONTENT, height);
			mPreviewText.setLayoutParams(dim);
		} else {
			mShowPreview = false;
		}

		mPreviewPopup.setTouchable(false);

		mPopupKeyboard = new PopupWindow(context);
		mPopupKeyboard.setBackgroundDrawable(null);
		CompatUtils.setPopupUnattachedToDecor(mPopupKeyboard);
		//mPopupKeyboard.setClippingEnabled(false);

		mPopupParent = this;
		//predicting = true;
		
		mPaint = new Paint();
		mPaint.setAntiAlias(true);
		mPaint.setTextSize(keyTextSize);
		mPaint.setTextAlign(Align.CENTER);
		
		mBgPaint = new Paint();
		mBgPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
		
		mPadding = new Rect(0, 0, 0, 0);
		mMiniKeyboardCache = new HashMap<Key,View>();

		resetMultiTap();
		initGestureDetector();

		mSwipeThreshold = (int) (500 * getResources().getDisplayMetrics().density);
		mDisambiguateSwipe = true;
		initGestureDetector();
		
		mLongpressDuration = LONGPRESS_TIMEOUT;
	}
	
	public void setCalibration(CalibrationInfo calibration) {
		final Resources res = getResources();
		float density = res.getDisplayMetrics().density;
		mVerticalCorrection = res.getDimensionPixelSize(R.dimen.vertical_correction);
		mSpaceCorrection = res.getDimensionPixelOffset(R.dimen.spacebar_vertical_correction);
		if (res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			mHorizontalCorrection = (int)(calibration.mPortraitDx * density / 2);
			mVerticalCorrection += (int)(calibration.mPortraitDy * density / 2);
			//mSpaceCorrection = (int)(calibration.mPortraitSpace * density / 2) - mVerticalCorrection;
			mSpaceCorrection -= (int)(calibration.mPortraitDy * density / 2);
		} else {
			mHorizontalCorrection = (int)(calibration.mLandscapeDx * density / 2);
			mVerticalCorrection += (int)(calibration.mLandscapeDy * density / 2);
			//mSpaceCorrection = (int)(calibration.mLandscapeSpace * density / 2) - mVerticalCorrection;
			mSpaceCorrection -= (int)(calibration.mPortraitDy * density / 2);
		}
		/*
		// Don't exceed twice the default correction
		int maxSpaceCorrection = 2 * res.getDimensionPixelSize(R.dimen.spacebar_vertical_correction);
		if (mSpaceCorrection > maxSpaceCorrection) {
			mSpaceCorrection = maxSpaceCorrection;
		}
		*/
		if (mKeyboard != null) {
			mKeyboard.setSpaceCorrection(mSpaceCorrection);
		}
	}
	
	public void disableMT(boolean disableMT) {
		mMultiTouchAvail = mHasMultiTouchAPI && !disableMT;
	}
	
	public void setLongpressDuration(int duration) {
		mLongpressDuration = (LONGPRESS_TIMEOUT * (10 + duration)) / 60;
	}
	
	public void setMultitapInterval(int interval) {
		mMultitapInterval = interval;
	}
	
	public void setSwipeFactor(int factor) {
		mSwipeFactor = factor;
	}

	public void setAccentsPriority(boolean accentsPriority) {
		mAccentsPriority = accentsPriority;
	}
	
	synchronized public void pushEvent(MotionEvent me) {
		mPendingEvents.add(me);
		// Wake up later to try dequeuing the messages
		mHandler.removeMessages(MSG_POP_EVENTS);
		mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_POP_EVENTS), 0);
	}
	
	synchronized void popEvents() {
		if (DEBUG) Log.d(TAG, "popEvents");
		MotionEvent me;
		while ((me = mPendingEvents.poll()) != null) {
			if (DEBUG) Log.d(TAG, "xxxxx dequeued pending event");
			translateEvent(me);
		}
	}
	
	public void setCustomKeys(CustomKeys customKeys) {
		mCustomKeys = customKeys;
	}
	
	public void setNoAltPreview(boolean noAltPreview) {
		mNoAltPreview = noAltPreview;
	}
	
	public void setLangPopup(Keyboard langPopup) {
		mLangPopup = langPopup;
	}
	
	public void applySkin(SkinInfo skin) {
		mBackground = skin.background;
		mKeyBackground = skin.keyBackground;
		mAltKeyBackground = skin.altKeyBackground;
		mDeleteKey = skin.deleteKey;
		mShiftKey = skin.shiftKey;
		mKeyTextColor = skin.textColor;
		mPressedTextColor = skin.pressedTextColor;
		mAltLabelColor = skin.altLabelColor;
		mTextAltColor = skin.textAltColor;
		mShadowColor = skin.shadowColor;
		mAltShadowColor = skin.altShadowColor;
		mModShadowColor = skin.modShadowColor;
		mReturnKey = skin.returnKey;
		mSpaceKey = skin.spaceKey;
		mSearchKey = skin.searchKey;
		mUpArrow = skin.upArrow;
		mDownArrow = skin.downArrow;
		mLeftArrow = skin.leftArrow;
		mRightArrow = skin.rightArrow;
		mMicKey = skin.micKey;
		mLabelFont = skin.boldLabel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
		if (skin.labelFont != null) {
			mLabelFont = skin.labelFont;
			mAltLabelFont = skin.labelFont;
		} else {
			mLabelFont = skin.boldLabel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
			mAltLabelFont = Typeface.DEFAULT;
		}
		mSmallKeys = skin.smallKeys;
		mKeyPadding = skin.padding;
		mPopupSkin = skin.popupSkin;
		
		Resources res = getResources();
		mKeyTextSize[0] = (int)(res.getDimension(R.dimen.key_text_size) * GlobalResources.mFontScale);
		mKeyTextSize[1] = (int)(res.getDimension(R.dimen.key_small_text_size) * GlobalResources.mFontScale);
		mKeyTextSize[2] = (int)(res.getDimension(R.dimen.key_very_small_text_size) * GlobalResources.mFontScale);
		mKeyTextSize[3] = (int)(res.getDimension(R.dimen.key_big_text_size) * GlobalResources.mFontScale);
		if (mSmallKeys) {
			for (int i=0; i<2; i++) {
				mKeyTextSize[i] = mKeyTextSize[i] * 95 / 100;
			}
		}
		
		// Redraw the keyboard
		mKeyBackground.getPadding(mPadding);
		mKeyboardChanged = true;
		mOffsetInWindow = null;
		invalidateAllKeys();
	}

	public void setDisplayAlt(boolean displayAlt) {
		mDisplayAltLabels = displayAlt;
	}
	
	public void setAlwaysCaps(boolean alwaysCaps) {
		mAlwaysCaps = alwaysCaps;
	}
	
	public void setTransparency(int opacity) {
		mBgPaint.setARGB(255 * (50 + opacity) / 100, 255, 255, 255);
		mTransparency = (opacity != 50);
	}

	public void setCompactLayout(boolean compactLayout) {
		mCompactOrT9Layout = compactLayout;
	}
	
	public void setShowTouchpoints(boolean showTouchpoints) {
		mShowTouchPoints = showTouchpoints;
	}
	
	public void setSlidePopup(boolean slidePopup) {
		mSlidePopup = slidePopup;
	}
	
	public void setSpacePreview(boolean spacePreview) {
		mSpacePreview = spacePreview;
	}
	
	private void initGestureDetector() {
		mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onFling(MotionEvent me1, MotionEvent me2, 
					float velocityX, float velocityY) {
				if (mPossiblePoly) return false;
				final float absX = Math.abs(velocityX);
				final float absY = Math.abs(velocityY);
				float deltaX = me2.getX() - me1.getX();
				float deltaY = me2.getY() - me1.getY();
				int travelX = (getWidth() * mSwipeFactor) / 100;
				int travelY = (getHeight() * mSwipeFactor) / 100;
				mSwipeTracker.computeCurrentVelocity(1000);
				final float endingVelocityX = mSwipeTracker.getXVelocity();
				final float endingVelocityY = mSwipeTracker.getYVelocity();
				boolean sendDownKey = false;
				if (velocityX > mSwipeThreshold && absY < absX && deltaX > travelX) {
					if (mDisambiguateSwipe && endingVelocityX < velocityX / 4) {
						sendDownKey = true;
					} else {
						swipeRight();
						return true;
					}
				} else if (velocityX < -mSwipeThreshold && absY < absX && deltaX < -travelX) {
					if (mDisambiguateSwipe && endingVelocityX > velocityX / 4) {
						sendDownKey = true;
					} else {
						swipeLeft();
						return true;
					}
				} else if (velocityY < -mSwipeThreshold && absX < absY && deltaY < -travelY) {
					if (mDisambiguateSwipe && endingVelocityY > velocityY / 4) {
						sendDownKey = true;
					} else {
						swipeUp();
						return true;
					}
				} else if (velocityY > mSwipeThreshold && absX < absY / 2 && deltaY > travelY) {
					if (mDisambiguateSwipe && endingVelocityY < velocityY / 4) {
						sendDownKey = true;
					} else {
						swipeDown();
						return true;
					}
				}

				if (sendDownKey) {
					detectAndSendKey(mDownKey, mStartX, mStartY, me1.getEventTime(), 1);
				}
				return false;
			}
		});

		mGestureDetector.setIsLongpressEnabled(false);
	}

	public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
		mKeyboardActionListener = listener;
	}

	/**
	 * Returns the {@link OnKeyboardActionListener} object.
	 * @return the listener attached to this keyboard
	 */
	protected OnKeyboardActionListener getOnKeyboardActionListener() {
		return mKeyboardActionListener;
	}
	
	public void resetKeyState() {
		showPreview(NOT_A_KEY);
	}

	/**
	 * Attaches a keyboard to this view. The keyboard can be switched at any time and the
	 * view will re-layout itself to accommodate the keyboard.
	 * @see Keyboard
	 * @see #getKeyboard()
	 * @param keyboard the keyboard to display in this view
	 */
	public void setKeyboard(Keyboard keyboard) {
		if (mKeyboard != null) {
			showPreview(NOT_A_KEY);
		}
		// Remove any pending messages
		removeMessages();
		mKeyboard = keyboard;
		mKeys =  mKeyboard.getKeys();
		requestLayout();
		// Hint to reallocate the buffer if the size changed
		mKeyboardChanged = true;
		invalidateAllKeys();
		computeProximityThreshold(keyboard);
		mMiniKeyboardCache.clear(); // Not really necessary to do every time, but will free up views
		// Switching to a different keyboard should abort any pending keys so that the key up
		// doesn't get delivered to the old or new keyboard
		mAbortKey = true; // Until the next ACTION_DOWN
		
        // One-seventh of the keyboard width seems like a reasonable threshold
        mJumpThresholdSquare = keyboard.getMinWidth() / 7;
        mJumpThresholdSquare *= mJumpThresholdSquare;
        // Assuming there are 4 rows, this is the coordinate of the last row
        // XXX to check !
        mLastRowY = (keyboard.getHeight() * 3) / 4;
		mKeyboard.setSpaceCorrection(mSpaceCorrection);
	}

	/**
	 * Returns the current keyboard being displayed by this view.
	 * @return the currently attached keyboard
	 * @see #setKeyboard(Keyboard)
	 */
	public Keyboard getKeyboard() {
		return mKeyboard;
	}

	/**
	 * Sets the state of the shift key of the keyboard, if any.
	 * @param shifted whether or not to enable the state of the shift key
	 * @return true if the shift key state changed, false if there was no change
	 * @see KeyboardView#isShifted()
	 */
	public boolean setShifted(boolean shifted, boolean forceDraw) {

		if (mKeyboard != null) {
			if (mKeyboard.setShifted(shifted) || forceDraw) {
				// The whole keyboard probably needs to be redrawn
				invalidateAllKeys();
				return true;
			}
		}
		return false;
	}

	//@Override
	public void setCustomSmileys(boolean customSmileys) {
		this.customSmileys = customSmileys;
	}

	/**
	 * Returns the state of the shift key of the keyboard, if any.
	 * @return true if the shift is in a pressed state, false otherwise. If there is
	 * no shift key on the keyboard or there is no keyboard attached, it returns false.
	 */
	public boolean isShifted() {
		if (mKeyboard != null) {
			return mKeyboard.isShifted();
		}
		return false;
	}

	public boolean isT9PredictionOn() {
		if (mKeyboard != null) {
			return mKeyboard.isT9Enabled();
		} else {
			return false;
		}
	}
	
	/**
	 * Enables or disables the key feedback popup. This is a popup that shows a magnified
	 * version of the depressed key. By default the preview is enabled. 
	 * @param previewEnabled whether or not to enable the key feedback popup
	 * @see #isPreviewEnabled()
	 */
	public void setPreviewEnabled(boolean previewEnabled) {
		mShowPreview = previewEnabled;
	}

	/**
	 * Returns the enabled state of the key feedback popup.
	 * @return whether or not the key feedback popup is enabled
	 * @see #setPreviewEnabled(boolean)
	 */
	public boolean isPreviewEnabled() {
		return mShowPreview;
	}

	public void setVerticalCorrection(int verticalOffset) {

	}
	
	public void setPopupParent(View v) {
		mPopupParent = v;
	}

	public void setPopupOffset(int x, int y) {
		mMiniKeyboardOffsetX = x;
		mMiniKeyboardOffsetY = y;
		if (mPreviewPopup.isShowing()) {
			mPreviewPopup.dismiss();
		}
	}

	/**
	 * When enabled, calls to {@link OnKeyboardActionListener#onKey} will include key
	 * codes for adjacent keys.  When disabled, only the primary key code will be
	 * reported.
	 * @param enabled whether or not the proximity correction is enabled
	 */
	public void setProximityCorrectionEnabled(boolean enabled) {
		mProximityCorrectOn = enabled;
	}

	/**
	 * Returns true if proximity correction is enabled.
	 */
	public boolean isProximityCorrectionEnabled() {
		return mProximityCorrectOn;
	}

	
	/** 
	 * Popup keyboard close button clicked.
	 * @hide 
	 */
	public void onClick(View v) {
		dismissPopupKeyboard();
	}

	private CharSequence adjustCase(CharSequence label) {
		if (label == null)
			return null;
		if ((mKeyboard.isShifted() || mAlwaysCaps) 
				&& label.length() > 0 && label.length() < 5) {
			if (!Character.isUpperCase(label.charAt(0))) {
				label = Workarounds.adjustCase(label);
			}
		} else if (label.length() > 0 && label.charAt(0) == '\u0130') {
			// Special for turkish
			return "i";
		} else if (label.length() == 2 && label.charAt(1) == '\u0130') {
			// Special for turkish
			return "li";
		}
		return label;
	}
	
	
	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// Round up a little
		if (mKeyboard == null) {
			setMeasuredDimension(getPaddingLeft() + getPaddingRight(), getPaddingTop() + getPaddingBottom());
		} else {
			int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
			if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
				width = MeasureSpec.getSize(widthMeasureSpec);
			}
			setMeasuredDimension(width, mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom());
		}
	}


	/**
	 * Compute the average distance between adjacent keys (horizontally and vertically)
	 * and square it to get the proximity threshold. We use a square here and in computing
	 * the touch distance from a key's center to avoid taking a square root.
	 * @param keyboard
	 */
	private void computeProximityThreshold(Keyboard keyboard) {
		if (keyboard == null) return;
		final Key[] keys = mKeys;
		if (keys == null) return;
		int length = keys.length;
		int dimensionSum = 0;
		for (int i = 0; i < length; i++) {
			Key key = keys[i];
			dimensionSum += Math.min(key.width, key.height) + key.gap;
		}
		if (dimensionSum < 0 || length == 0) return;
		mProximityThreshold = (int) (dimensionSum * 1.6f / length);
		mProximityThreshold *= mProximityThreshold; // Square it
	}

	@Override
	public void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		// Release the buffer, if any and it will be reallocated on the next draw
		mBuffer = null;
	}

	@Override
	public void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		if (mAttached == false) {
			if (mPopupParent != this) {
				if (DEBUG) Log.d(TAG, "popup attached");
				// Used by the mini keyboard
				getLocationOnScreen(mLocation);
				mPopupParent.getLocationOnScreen(mParentLocation);
				// Dequeue the pending events
				popEvents();
			}
			mAttached = true;
		}
		
		if (mDrawPending || mBuffer == null || mKeyboardChanged) {
			onBufferDraw();
		}
		canvas.drawBitmap(mBuffer, 0, 0, mTransparency ? mBgPaint : null);
	}

	private void onBufferDraw() {
		if (mBuffer == null || mKeyboardChanged) {
			if (mBuffer == null || mKeyboardChanged &&
					(mBuffer.getWidth() != getWidth() || mBuffer.getHeight() != getHeight())) {
				mBuffer = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
				mCanvas = new Canvas(mBuffer);
			}
			invalidateAllKeys();
			mKeyboardChanged = false;
			
			// Draw background
			mCanvas.clipRect(0, 0, getWidth(), getHeight(), Op.REPLACE);
			mBackground.setBounds(0, 0, getWidth(), getHeight());
			mBackground.draw(mCanvas);
		}
		final Canvas canvas = mCanvas;
		canvas.clipRect(mDirtyRect, Op.REPLACE);

		if (mKeyboard == null) return;

		final int kbdPaddingLeft = getPaddingLeft();
		final int kbdPaddingTop = getPaddingTop();

		boolean drawSingleKey = false;
		if (mInvalidatedKey != null && canvas.getClipBounds(mClipRegion)) {
			// Is clipRegion completely contained within the invalidated key?
			if (mInvalidatedKey.x + kbdPaddingLeft - 1 <= mClipRegion.left &&
					mInvalidatedKey.y + kbdPaddingTop - 1 <= mClipRegion.top &&
					mInvalidatedKey.x + mInvalidatedKey.width + kbdPaddingLeft + 1 >= mClipRegion.right &&
					mInvalidatedKey.y + mInvalidatedKey.height + kbdPaddingTop + 1 >= mClipRegion.bottom) {
				drawSingleKey = true;
			}
		}

		canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
		mBackground.draw(canvas);
		for (final Key key: mKeys) {
			drawKey(key, canvas, kbdPaddingLeft, kbdPaddingTop, drawSingleKey);
		}
		mInvalidatedKey = null;

		if (mShowTouchPoints) {
			drawTouchPoints(canvas);
		}

		mDrawPending = false;
		mDirtyRect.setEmpty();
	}

	private void drawTouchPoints(Canvas canvas) {
		mPaint.setAlpha(128);
		mPaint.setColor(0xFFFF0000);
		canvas.drawCircle(mStartX, mStartY, 3, mPaint);
		canvas.drawLine(mStartX, mStartY, mLastX, mLastY, mPaint);
		mPaint.setColor(0xFF0000FF);
		canvas.drawCircle(mLastX, mLastY, 3, mPaint);
		mPaint.setColor(0xFF00FF00);
		canvas.drawCircle((mStartX + mLastX) / 2, (mStartY + mLastY) / 2, 2, mPaint);
	}

	private void drawKey(Key key, Canvas canvas, int kbdPaddingLeft, int kbdPaddingTop, boolean drawSingleKey) {
		if (key.disabled) {
			return;
        }
		if (drawSingleKey && mInvalidatedKey != key) {
			return;
        }
		int[] drawableState = key.getCurrentDrawableState();
		// Check if alt key
		boolean altKey = false;
		Drawable keyBg = mKeyBackground;
		if (key.codes[0] == -108) {
            if (key.icon != null) {
                altKey = true;
            }
        } else if ((key.codes[0] < 0 && key.codes[0] > -120)
                || key.codes[0] == 10 || key.codes[0] == 27) {
            altKey = true;
        }
		if (altKey) {
            keyBg = mAltKeyBackground;
            mPaint.setColor(mTextAltColor);
        } else {
            mPaint.setColor(key.pressed ? mPressedTextColor : mKeyTextColor);
        }
		keyBg.setState(drawableState);
		//keyBackground.setAlpha(mGlobalAlpha);

		// Switch the character to uppercase if shift is pressed
		final String label = key.label == null? null :
            mCustomKeys.translate(adjustCase(key.label).toString());

		final Rect bounds = keyBg.getBounds();
		if (key.width != bounds.right ||
                key.height != bounds.bottom) {
            keyBg.setBounds(0, 0, key.width, key.height);
        }
		canvas.translate(key.x + kbdPaddingLeft, key.y + kbdPaddingTop);
		keyBg.draw(canvas);

		String altLabel = null;
		int textSize = 0;

		if (mDisplayAltLabels) {
			altLabel = key.altLabel;
		}
		textSize = key.textSize;
		Drawable icon = getKeyIcon(key);

		if (label != null) {
			drawKeyLabel(key, canvas, altKey, label, altLabel, textSize);
		} else if (icon != null) {
			drawKeyIcon(key, canvas, icon);
        }
		if (altLabel != null) {
			drawKeyAltLabel(key, canvas, altLabel);
        }
		Drawable altIcon = getIconByID(key.altIconID);
		if (altIcon != null) {
			drawKeyAltIcon(key, canvas, altIcon);
		}
		canvas.translate(-key.x - kbdPaddingLeft, -key.y - kbdPaddingTop);
	}

	private void drawKeyAltIcon(Key key, Canvas canvas, Drawable icon) {
		final int width = icon.getIntrinsicWidth() * 2 / 5;
		final int height = icon.getIntrinsicHeight() * 2 / 5;
		final int x = (key.width - mPadding.left - mPadding.right - width) / 2 + mPadding.left;
		final int y = (key.height - mPadding.top - mPadding.bottom - height) / 5 + mPadding.top;
		drawIcon(canvas, icon, x, y, width, height);
	}

	private void drawKeyIcon(Key key, Canvas canvas, Drawable icon) {
		final int width = (int)(icon.getIntrinsicWidth() * GlobalResources.mIconScale);
		final int height = (int)(icon.getIntrinsicHeight() * GlobalResources.mIconScale);
		final int x = (key.width - mPadding.left - mPadding.right - width) / 2 + mPadding.left;
		final int y = (key.height - mPadding.top - mPadding.bottom - height) / 2 + mPadding.top;
		drawIcon(canvas, icon, x, y, width, height);
	}

	private void drawIcon(Canvas canvas, Drawable icon, int x, int y, int width, int height) {
		canvas.translate(x, y);
		icon.setBounds(0, 0, width, height);
		icon.draw(canvas);
		canvas.translate(-x, -y);
	}

	private void drawKeyAltLabel(Key key, Canvas canvas, String altLabel) {
		mPaint.setColor(mAltLabelColor);
		mPaint.setTextSize(mAltLabelSize);
		mPaint.setTypeface(mAltLabelFont);
		if (mAltShadowColor != null) {
            mPaint.setShadowLayer(mAltShadowRadius, 0, 0, mAltShadowColor);
        }
		int yOffset = key.height;
		if (mSmallKeys) {
            yOffset = yOffset / 3;
        } else {
            yOffset = yOffset / 4;
        }
		canvas.drawText(altLabel, key.width / 2,
                yOffset + (mPaint.getTextSize() - mPaint.descent()) / 2,
                mPaint);
		mPaint.setShadowLayer(0, 0, 0, 0);
	}

	private void drawKeyLabel(Key key, Canvas canvas, boolean altKey, String label, String altLabel, int textSize) {
		// For characters, use large font. For labels like "Done", use small font.
		if (label.length() > 1 && key.codes.length < 2 && label.codePointAt(0) < 0xffff && !key.textSequence
                || key.codes[0] == Keyboard.KEYCODE_SETLANG) {
            mPaint.setTextSize(mLabelTextSize);
            mPaint.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            mPaint.setTextSize(mKeyTextSize[textSize]);
            // For emojis, force non bold font to ensure correct display
            final Typeface font = (textSize == 3) ? Typeface.DEFAULT : mLabelFont;
            mPaint.setTypeface(font);
        }
		// Draw a drop shadow for the text
		if (altKey) {
            if (mModShadowColor != null) {
                mPaint.setShadowLayer(mModShadowRadius, 0, 0, mModShadowColor);
            }
        } else {
            if (mShadowColor != null) {
                mPaint.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
            }
        }
		// Draw the text
		int yOffset = (key.height - mPadding.top - mPadding.bottom);
		if (altLabel == null) {
            yOffset = yOffset / 2;
        } else {
            yOffset = yOffset * mKeyPadding / 100;
        }
		canvas.drawText(label,
                (key.width - mPadding.left - mPadding.right) / 2
                + mPadding.left,
                yOffset + (mPaint.getTextSize() - mPaint.descent()) / 2 + mPadding.top,
                mPaint);
		// Turn off drop shadow
		mPaint.setShadowLayer(0, 0, 0, 0);
	}

	private Drawable getKeyIcon(Key key) {
		Drawable icon = key.icon;
		// Get the styled icon
		int iconID = key.iconID;
		if (iconID != 0) {
			icon = getIconByID(iconID);
		}
		return icon;
	}

	private Drawable getIconByID(int iconID) {
		Drawable icon = null;
		if (iconID == R.id.delete_key) {
            icon = mDeleteKey;
        } else if (iconID == R.id.shift_key) {
            icon = mShiftKey;
        } else if (iconID == R.id.return_key) {
            icon = mReturnKey;
        } else if (iconID == R.id.space_key) {
            icon = mSpaceKey;
        } else if (iconID == R.id.mic_key) {
            icon = mMicKey;
        } else if (iconID == R.id.search_key) {
            icon = mSearchKey;
        } else if (iconID == R.id.left_arrow) {
            icon = mLeftArrow;
        } else if (iconID == R.id.right_arrow) {
            icon = mRightArrow;
        } else if (iconID == R.id.up_arrow) {
            icon = mUpArrow;
        } else if (iconID == R.id.down_arrow) {
            icon = mDownArrow;
        }
		return icon;
	}

	private int getKeyIndices(int x, int y, int[] allKeys) {
		final Key[] keys = mKeys;
		final boolean proximityCorrectOn = mProximityCorrectOn;
		final int proximityThreshold = mProximityThreshold;
		int[] distances = mDistances;
		int primaryIndex = NOT_A_KEY;
		int closestKey = NOT_A_KEY;
		int closestKeyDist = mProximityThreshold + 1;
		java.util.Arrays.fill(mDistances, Integer.MAX_VALUE);
		
		// Detect keypress outside the keyboard
		if (x < 0) {
			x = 0;
		} else {
			final int width = mKeyboard.getMinWidth();
			if (x >= width) {
				x = width - 1;
			}
		}
		if (y<0) {
			y = 0;
		} else {
			final int height = mKeyboard.getHeight();
			if (y >= height) {
				y = height - 1;
			}
		}
		
		int [] nearestKeyIndices = mKeyboard.getNearestKeys(x, y);
		final int keyCount = nearestKeyIndices.length;
		for (int i = 0; i < keyCount; i++) {
			final Key key = keys[nearestKeyIndices[i]];
			// Ignore disabled keys
			if (key.disabled)
				continue;
			int dist = 0;
			boolean isInside = key.isInside(x,y);
			if (isInside) {
				primaryIndex = nearestKeyIndices[i];
			}
			if (((proximityCorrectOn 
					&& (dist = key.squaredDistanceFrom(x, y)) < proximityThreshold) 
					|| isInside)
					&& key.codes[0] > 32) {
				// Find insertion point
				final int nCodes = key.codes.length;
				if (dist < closestKeyDist) {
					closestKeyDist = dist;
					closestKey = nearestKeyIndices[i];
				}

				if (allKeys == null) continue;

				for (int j = 0; j < distances.length; j++) {
					if (distances[j] > dist) {
						// Make space for nCodes codes
						System.arraycopy(distances, j, distances, j + nCodes,
								distances.length - j - nCodes);
						System.arraycopy(allKeys, j, allKeys, j + nCodes,
								allKeys.length - j - nCodes);
						System.arraycopy(key.codes, 0, allKeys, j, nCodes);
                        Arrays.fill(distances, j, j + nCodes, dist);
						break;
					}
				}
			}

		}
		if (primaryIndex == NOT_A_KEY) {
			primaryIndex = closestKey;
		}
		return primaryIndex;
	}

	protected void detectAndSendKey(int index, int x, int y, long eventTime, int pointerCount) {
		if( mMoving )
			return;
		if (DEBUG) Log.d(TAG, "detectAndSendKey " + Integer.toString(index) + " x=" +
				Integer.toString(x) + " y=" + Integer.toString(y));
		if (index != NOT_A_KEY && index < mKeys.length) {
			final Key key = mKeys[index];
			if (key.textSequence) {
				// Special case for Hindi ligatures
				int len = key.text.length();
				for (int i = 0; i < len; i++) {
					int code = key.text.charAt(i);
					int[] codes = new int[1];
					codes[0] = code;
					mKeyboardActionListener.onKey(code, codes, false, false);
					mKeyboardActionListener.onRelease(code);
				}
			} else if (key.text != null) {
				mKeyboardActionListener.onText(mCustomKeys.translate(key.text.toString()));
				mKeyboardActionListener.onRelease(NOT_A_KEY);
			} else {
				int code = key.codes[0];
				if (DEBUG) Log.d(TAG, "detectAndSendKey " + Character.toString((char)code));
				//TextEntryState.keyPressedAt(key, x, y);
				int[] codes = new int[MAX_NEARBY_KEYS];
				Arrays.fill(codes, NOT_A_KEY);
				
				// Multi-tap
				boolean replace = false;
				if (mInMultiTap) {
					if (mTapCount != -1) {
						replace = true;
					} else {
						mTapCount = 0;
					}
					code = key.codes[mTapCount];
				}
				
				if (!mCompactOrT9Layout)  {
					// Normal mode
					getKeyIndices(x, y, codes);
				} else {
					// Exact mode for T9
					if (isT9PredictionOn()) {
						// Get all the possible letters if T9 prediction on
						System.arraycopy(key.codes, 0, codes, 0, key.codes.length);
					} else {
						// Otherwise just keep the selected letter
						codes[0] = code;
					}
				}
				
				// Hack for the language key
				if (code == Keyboard.KEYCODE_SETLANG) {
					codes = new int[] { key.codes[1] };
				}
				// workaround for multitouch bug
				if (pointerCount < 2 || 
						(code != Keyboard.KEYCODE_MODE_CHANGE && code != Keyboard.KEYCODE_DELETE && code != 10)) {
					mKeyboardActionListener.onKey(code, codes, false, replace);
					mKeyboardActionListener.onRelease(code);
				}
			}
			mLastSentIndex = index;
			mLastTapTime = eventTime;
		}
	}

	/**
	 * Handle multi-tap keys by producing the key label for the current multi-tap state.
	 */
	private CharSequence getPreviewText(Key key) {
		if (mInMultiTap) {
			// Multi-tap
			mPreviewLabel.setLength(0);
			final int index = mTapCount < 0 ? 0 : mTapCount;
			if (index < key.codes.length) {
				mPreviewLabel.append((char) key.codes[index]);
			} else {
				Log.e(TAG, "Index out of bounds: " + Integer.toString(index));
			}
			return adjustCase(mPreviewLabel);
		} else {
			return adjustCase(key.label);
		}
	}

	private void showPreview(int keyIndex) {
		int oldKeyIndex = mCurrentKeyIndex;
		final PopupWindow previewPopup = mPreviewPopup;
		final Key[] keys = mKeys;
		
		mCurrentKeyIndex = keyIndex;
		// Release the old key and press the new key
		if (oldKeyIndex != mCurrentKeyIndex) {
			if (oldKeyIndex != NOT_A_KEY && keys.length > oldKeyIndex) {
				keys[oldKeyIndex].onReleased(mCurrentKeyIndex == NOT_A_KEY);
				invalidateKey(oldKeyIndex);
			}
			if (mCurrentKeyIndex != NOT_A_KEY && keys.length > mCurrentKeyIndex) {
				keys[mCurrentKeyIndex].onPressed();
				invalidateKey(mCurrentKeyIndex);
			}
		}
		// If key changed and preview is on ...
		if (oldKeyIndex != mCurrentKeyIndex && mShowPreview) {
			mHandler.removeMessages(MSG_SHOW_PREVIEW);
			final boolean showPreview = keyIndex != NOT_A_KEY &&
				(keys[keyIndex].codes[0] != 32 || mSpacePreview) &&
					keys[keyIndex].codes[0] != KEYCODE_EMOJI_NUM;
			if (previewPopup.isShowing()) {
				if (!showPreview) {
					mHandler.sendMessageDelayed(mHandler
							.obtainMessage(MSG_REMOVE_PREVIEW), 
							DELAY_AFTER_PREVIEW);
				}
			}
			if (showPreview) {
				if (previewPopup.isShowing() && mPreviewText.getVisibility() == VISIBLE) {
					// Show right away, if it's already visible and finger is moving around
					showKey(keyIndex);
				} else {
					mHandler.sendMessageDelayed(
							mHandler.obtainMessage(MSG_SHOW_PREVIEW, keyIndex, 0), 
							DELAY_BEFORE_PREVIEW);
				}
			}
		}
	}

	private void showKey(final int keyIndex) {
		final PopupWindow previewPopup = mPreviewPopup;
		final Key[] keys = mKeys;
		if (keyIndex < 0 || keyIndex >= mKeys.length) return;
		Key key = keys[keyIndex];
		if (key.icon != null) {
			mPreviewText.setCompoundDrawables(null, null, null, 
					key.iconPreview != null ? key.iconPreview : key.icon);
			mPreviewText.setText(null);
		} else {
			mPreviewText.setCompoundDrawables(null, null, null, null);
			mPreviewText.setText(mCustomKeys.translate(getPreviewText(key).toString()));
			final String label = key.label.toString();
			if (key.label.length() > 1 && key.codes.length < 2 && label.codePointAt(0) < 0xffff) {
				mPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mKeyTextSize[0]);
				mPreviewText.setTypeface(Typeface.DEFAULT_BOLD);
			} else {
				mPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mPreviewTextSizeLarge);
				mPreviewText.setTypeface(Typeface.DEFAULT);
			}
		}
		mPreviewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		int popupWidth = Math.max(mPreviewText.getMeasuredWidth(), key.width 
				+ mPreviewText.getPaddingLeft() + mPreviewText.getPaddingRight());
		final int popupHeight = mPreviewHeight;
		LayoutParams lp = mPreviewText.getLayoutParams();
		if (lp != null) {
			lp.width = popupWidth;
			lp.height = popupHeight;
		}
				
		if (!mPreviewCentered) {
			mPopupPreviewX = key.x - mPreviewText.getPaddingLeft() + getPaddingLeft();
			mPopupPreviewY = key.y - popupHeight + mPreviewOffset;
		} else {
			// TODO: Fix this if centering is brought back
			mPopupPreviewX = 160 - mPreviewText.getMeasuredWidth() / 2;
			mPopupPreviewY = - mPreviewText.getMeasuredHeight();
		}
		mHandler.removeMessages(MSG_REMOVE_PREVIEW);
		
		if (mOffsetInWindow == null) {
			mOffsetInWindow = new int[2];
			getLocationInWindow(mOffsetInWindow);
			mOffsetInWindow[0] += mMiniKeyboardOffsetX; // Offset may be zero
			mOffsetInWindow[1] += mMiniKeyboardOffsetY; // Offset may be zero
		}
		
		// Set the preview background state
		boolean longPressable = key.popupResId != 0 || key.codes[0] == -2;
		mPreviewText.getBackground().setState(
				longPressable ? LONG_PRESSABLE_STATE_SET : EMPTY_STATE_SET);
		if (previewPopup.isShowing()) {
			previewPopup.dismiss();
			/*previewPopup.update(mPopupPreviewX + mOffsetInWindow[0],
					mPopupPreviewY + mOffsetInWindow[1], 
					popupWidth, popupHeight);*/
		}
		//else {
			previewPopup.setWidth(popupWidth);
			previewPopup.setHeight(popupHeight);
			previewPopup.showAtLocation(mPopupParent, Gravity.NO_GRAVITY, 
					mPopupPreviewX + mOffsetInWindow[0], 
					mPopupPreviewY + mOffsetInWindow[1]);
		//}
		mPreviewText.setVisibility(VISIBLE);
	}

	/**
	 * Requests a redraw of the entire keyboard. Calling {@link #invalidate} is not sufficient
	 * because the keyboard renders the keys to an off-screen buffer and an invalidate() only 
	 * draws the cached buffer.
	 * @see #invalidateKey(int)
	 */
	public void invalidateAllKeys() {
		mDirtyRect.union(0, 0, getWidth(), getHeight());
		mDrawPending = true;
		invalidate();
	}

	/**
	 * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only
	 * one key is changing it's content. Any changes that affect the position or size of the key
	 * may not be honored.
	 * @param keyIndex the index of the key in the attached {@link Keyboard}.
	 * @see #invalidateAllKeys
	 */
	private void invalidateKey(int keyIndex) {
		if (keyIndex < 0 || keyIndex >= mKeys.length) {
			return;
		}
		
		// ugly hack to avoid exception when creating the mini keyboard
		if (getWidth() <= 0 || getHeight() <= 0)
			return;
		
		final Key key = mKeys[keyIndex];
		mInvalidatedKey = key;
		mDirtyRect.union(key.x + getPaddingLeft(), key.y + getPaddingTop(), 
				key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
		onBufferDraw();
		invalidate(key.x + getPaddingLeft(), key.y + getPaddingTop(), 
				key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
	}

	private boolean openPopupIfRequired(MotionEvent me) {
		// Check if we have a popup layout specified first.
		if (mPopupLayout == 0 || popupKeyboardDisabled) {
			return false;
		}
		if (mCurrentKey < 0 || mCurrentKey >= mKeys.length) {
			return false;
		}

		Key popupKey = mKeys[mCurrentKey];        
		boolean result = onLongPress(popupKey, me);
		if (result) {
			mAbortKey = true;
			showPreview(NOT_A_KEY);
		}
		return result;
	}

	/**
	 * Called when a key is long pressed. By default this will open any popup keyboard associated
	 * with this key through the attributes popupLayout and popupCharacters.
	 * @param popupKey the key that was long pressed
	 * @return true if the long press is handled, false otherwise. Subclasses should call the
	 * method on the base class if the subclass doesn't wish to handle the call.
	 */
	protected boolean onLongPress(Key popupKey, MotionEvent me) {
		int popupKeyboardId = popupKey.popupResId;

		if (popupKey.altIconID == R.id.mic_key) {
			mKeyboardActionListener.onVoiceInput();
			return true;
		}

		// Don't show the preview if there is only one character
		if ((!mShowPreview || mNoAltPreview) && popupKey.popupCharacters != null && popupKey.popupCharacters.length() ==1) {
            // directly send the key
            int code = (int)popupKey.popupCharacters.charAt(0);
            int[] codes = new int[1];
            codes[0] = code;
            mKeyboardActionListener.onKey(code, codes, false, false);
            mKeyboardActionListener.onRelease(code);
            return true;
        } else if (popupKey.codes[0] == 10 && !customSmileys) {
			mKeyboardActionListener.onShowEmojis();
		} else if (popupKeyboardId != 0) {
			openPopupKeyboard(popupKey, me, popupKeyboardId);
			return true;
		} else if (popupKey.codes[0] == CODE_LANG) {
			// Long press on LANG key -> show preferences screen
			return mKeyboardActionListener.onDisplayPrefScreen();
		}
		else if (popupKey.codes[0] == -140)
		{

            int[] codes = new int[1];
            codes[0] = -141;
            mKeyboardActionListener.onKey(-141, codes, false, false);
            mKeyboardActionListener.onRelease(-141);
			return true;
		}
		else if(popupKey.codes[0]==32)
			mMoving = mMover != null;
		return false;
	}

	private void openPopupKeyboard(Key popupKey, MotionEvent me, int popupKeyboardId) {
		// Check if slide mode has to be used
		final boolean slide = (popupKeyboardId == R.xml.popup) &&
            (mSlidePopup || (popupKey.popupCharacters != null && popupKey.popupCharacters.length() == 1));

		mMiniKeyboardContainer = mMiniKeyboardCache.get(popupKey);
		if (mMiniKeyboardContainer == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            mMiniKeyboardContainer = inflater.inflate(mPopupLayout, null);
            mMiniKeyboard = (KeyboardView) mMiniKeyboardContainer.findViewById(
                    R.id.keyboardView);
            mMiniKeyboard.setCustomKeys(mCustomKeys);
            View closeButton = mMiniKeyboardContainer.findViewById(
                    R.id.closeButton);
            if (closeButton != null) {
                closeButton.setOnClickListener(this);
                if (slide) {
                    // Hide close button in slide mode
                    closeButton.setVisibility(View.GONE);
                }
            }
            mMiniKeyboard.setOnKeyboardActionListener(new OnKeyboardActionListener() {
                public void onKey(int primaryCode, int[] keyCodes, boolean hardKbd, boolean replace) {
                    mKeyboardActionListener.onKey(primaryCode, keyCodes, hardKbd, replace);
                    dismissPopupKeyboardAsync();
                }

                public void onText(CharSequence text) {
                    mKeyboardActionListener.onText(text);
                    dismissPopupKeyboardAsync();
                }

                public void swipeLeft() { }
                public void swipeRight() { }
                public void swipeUp() { }
                public void swipeDown() { }
                public void onPress(int primaryCode) {
                    mKeyboardActionListener.onPress(primaryCode);
                }
                public void onRelease(int primaryCode) {
                    mKeyboardActionListener.onRelease(primaryCode);
                }
                public boolean onDisplayPrefScreen() {return false;}

				@Override
				public void onVoiceInput() {
				}

				@Override
				public void onShowEmojis() {
				}
			});
            //mInputView.setSuggest(mSuggest);
            Keyboard keyboard;
            CharSequence popupChars = mAccentsPriority ? popupKey.popupAccents : popupKey.popupCharacters;
            if (popupChars != null) {
                int nbColumns = -1;
                // For arabic diacritics
                if (popupChars.length() == 14) {
                    nbColumns = 7;
                }
                keyboard = new Keyboard(getContext(), popupKeyboardId,
                        popupChars, nbColumns, getPaddingLeft() + getPaddingRight());
            } else if (popupKey.codes[0] == Keyboard.KEYCODE_LANG) {
                keyboard = mLangPopup;
            } else {
                keyboard = new Keyboard(getContext(), popupKeyboardId, R.id.mode_normal, true,
                        false, false, null);
            }
            mMiniKeyboard.setKeyboard(keyboard);
            mMiniKeyboard.setPopupParent(this);
            mMiniKeyboard.applySkin(mPopupSkin);
            mMiniKeyboardContainer.measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));

            mMiniKeyboardCache.put(popupKey, mMiniKeyboardContainer);
        } else {
            mMiniKeyboard = (KeyboardView) mMiniKeyboardContainer.findViewById(
                    R.id.keyboardView);
        }

		if (mWindowOffset == null) {
            mWindowOffset = new int[2];
            getLocationInWindow(mWindowOffset);
        }
		final int centerX = popupKey.x + getPaddingLeft() + popupKey.width / 2;
		mPopupX = centerX;
		mPopupY = popupKey.y + getPaddingTop();
		final int previewKeyWidth = getWidth() / 10;
		// Check if popup must be aligned on left or right
		if (popupKey.x + popupKey.width / 3 >= mKeyboard.getDisplayWidth() / 2) {
            mPopupX = mPopupX + previewKeyWidth / 2 - mMiniKeyboardContainer.getMeasuredWidth() + mMiniKeyboardContainer.getPaddingRight();
        } else {
            mPopupX = mPopupX - previewKeyWidth / 2 - mMiniKeyboardContainer.getPaddingLeft();
        }
		mPopupY = mPopupY - mMiniKeyboardContainer.getMeasuredHeight();
		final int x = mPopupX + mWindowOffset[0];
		final int y = mPopupY + mMiniKeyboardContainer.getPaddingBottom() + mWindowOffset[1];
		mMiniKeyboard.setPopupOffset(x < 0 ? 0 : x, y);
		mMiniKeyboard.setShifted(isShifted(), false);
		mPopupKeyboard.setContentView(mMiniKeyboardContainer);
		mPopupKeyboard.setWidth(mMiniKeyboardContainer.getMeasuredWidth());
		mPopupKeyboard.setHeight(mMiniKeyboardContainer.getMeasuredHeight());
		// If there is only one row...
		if (slide && me != null) {
            mMiniKeyboard.setPreviewEnabled(false);
            mMiniKeyboard.mSlideMode = true;
            mMiniKeyboard.mAttached = false;
            // Build a DOWN event
            MotionEvent newMe = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
MotionEvent.ACTION_DOWN, me.getX(), me.getY() , me.getMetaState());
            mMiniKeyboard.mFirstX = me.getX();
            mMiniKeyboard.mCenterX = centerX;
            mMiniKeyboard.pushEvent(newMe);
            if (DEBUG) Log.d(TAG, "xxxx popup first event " + newMe.toString());
        }
		mPopupKeyboard.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
		mMiniKeyboardOnScreen = true;
		invalidateAllKeys();
	}

	//private long mOldEventTime;
	//private boolean mUsedVelocity;

	
    /**
     * This function checks to see if we need to handle any sudden jumps in the pointer location
     * that could be due to a multi-touch being treated as a move by the firmware or hardware.
     * Once a sudden jump is detected, all subsequent move events are discarded
     * until an UP is received.<P>
     * When a sudden jump is detected, an UP event is simulated at the last position and when 
     * the sudden moves subside, a DOWN event is simulated for the second key.
     * @param me the motion event
     * @return true if the event was consumed, so that it doesn't continue to be handled by 
     * KeyboardView.
     */
	private boolean handleSuddenJump(MotionEvent me) {
        final int action = me.getAction();
        final int x = (int) me.getX();
        final int y = (int) me.getY();
        boolean result = false;

        // Real multi-touch event? Stop looking for sudden jumps
        if (mMultiTouchAvail) {
        	if (MotionEventWrapper.getPointerCount(me) > 1) {
        		mDisableDisambiguation = true;
        		if (DEBUG) Log.d(TAG, "mDisableDisambiguation = true");
        	}
        }
        if (mDisableDisambiguation) {
            // If UP, reset the multi-touch flag
            if (action == MotionEvent.ACTION_UP) {
            	mDisableDisambiguation = false;
        		if (DEBUG) Log.d(TAG, "UP -> mDisableDisambiguation = false");
            }
            return false;
        }

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            // Reset the "session"
            mDroppingEvents = false;
            mDisableDisambiguation = false;
    		if (DEBUG) Log.d(TAG, "DOWN -> mDisableDisambiguation = false");
            break;
        case MotionEvent.ACTION_MOVE:
            // Is this a big jump?
            final int distanceSquare = (mLastX - x) * (mLastX - x) + (mLastY - y) * (mLastY - y);
            // Check the distance and also if the move is not entirely within the bottom row
            // If it's only in the bottom row, it might be an intentional slide gesture
            // for language switching
            if (distanceSquare > mJumpThresholdSquare
                    && (mLastY < mLastRowY || y < mLastRowY)) {
                // If we're not yet dropping events, start dropping and send an UP event
                if (!mDroppingEvents) {
                    mDroppingEvents = true;
                }
                result = true;
            } else if (mDroppingEvents) {
                // If moves are small and we're already dropping events, continue dropping
                result = true;
            }
            break;
        case MotionEvent.ACTION_UP:
            if (mDroppingEvents) {
                // Send an up event
        		if (DEBUG) Log.d(TAG, "MOVE -> translate event " + Integer.toString(mLastX) + " " + Integer.toString(mLastY));
                MotionEvent translated = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                        MotionEvent.ACTION_UP,
                        mLastX, mLastY, me.getMetaState());
                onBaseTouchEvent(translated, false);
                translated.recycle();
            	
                // Send a down event first, as we dropped a bunch of sudden jumps and assume that
                // the user is releasing the touch on the second key.
        		if (DEBUG) Log.d(TAG, "UP -> translate event " + Integer.toString(x) + " " + Integer.toString(y));
               translated = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                        MotionEvent.ACTION_DOWN,
                        x, y, me.getMetaState());
                onBaseTouchEvent(translated, false);
                translated.recycle();
                mDroppingEvents = false;
                // Let the up event get processed as well, result = false
            }
            break;
        }
        // Track the previous coordinate
        mLastX = x;
        mLastY = y;
        return result;
    }

	@Override
	public boolean onTouchEvent(MotionEvent me) {
		if(handleWindowMove(me))
			return true;

		return onBaseTouchEvent(me, true);
	}

	private boolean onBaseTouchEvent(MotionEvent me, boolean checkJump) {
		int action = me.getAction();
		final long eventTime = me.getEventTime();

		if (mMiniKeyboardOnScreen && mMiniKeyboard != null && mMiniKeyboard.mSlideMode) {
			return handlePopupKeyboardSlide(me);
		}
		
		try {
			// Convert multi-pointer up/down events to single up/down events to 
			// deal with the typical multi-pointer behavior of two-thumb typing
			int pointerCount = 1;
			if (mMultiTouchAvail) {
				pointerCount = MotionEventWrapper.getPointerCount(me);
			}

			if (DEBUG) Log.d(TAG, me.toString() + " pointer count: " + Integer.toString(pointerCount));

			boolean result = false;
			if (pointerCount != mOldPointerCount) {
				if (pointerCount == 1) {
					// Send a down event for the latest pointer
					final MotionEvent down = MotionEvent.obtain(eventTime, eventTime, MotionEvent.ACTION_DOWN,
							me.getX(), me.getY(), me.getMetaState());
					result = onModifiedTouchEvent(down, false);
					down.recycle();
					// If it's an up action, then deliver the up as well.
					if (action == MotionEvent.ACTION_UP) {
						result = onModifiedTouchEvent(me, true);
					}
				} else {
					// Send an up event for the last pointer
					final MotionEvent up = MotionEvent.obtain(eventTime, eventTime, MotionEvent.ACTION_UP,
							mOldPointerX, mOldPointerY, me.getMetaState());
					result = onModifiedTouchEvent(up, true);
					up.recycle();
				}
			} else {
				if (pointerCount == 1) {
					result = onModifiedTouchEvent(me, false);
					mOldPointerX = me.getX();
					mOldPointerY = me.getY();
				}
			}
			mOldPointerCount = pointerCount;
			if (result == true) {
				return true;
			}

			// Needs to be called after the gesture detector gets a turn, as it may have
			// displayed the mini keyboard
			if (mMiniKeyboardOnScreen) {
				if (DEBUG) Log.d(TAG, "mMiniKeyboardOnScreen");
				return true;
			}

			// if there was a sudden jump, return without processing the
			// actual motion event.
			if (checkJump && mPopupParent == this && handleSuddenJump(me)) {
				if (DEBUG) Log.d(TAG, "Sudden jump handled");
				return true;
			}

			int pointerIndex = -1;
			if (DEBUG) Log.d(TAG, "mShownPointer " + Integer.toString(mShownPointer) + " action " + 
					Integer.toString(action));
			// Validate the current shown pointer if needed
			if (mShownPointer == 0 &&
					(action == MotionEvent.ACTION_UP || 
							action == mActionPointer1Up ||
							action == mActionPointer2Down))
			{
				pointerIndex = 0;
				if (mMultiTouchAvail) {
					pointerIndex = MotionEventWrapper.findPointerIndex(me, 0);
				}
				mShownPointer = -1;
			}
			else if (mShownPointer == 1 &&
					(action == MotionEvent.ACTION_UP || 
							action == mActionPointer2Up ||
							action == mActionPointer1Down))
			{
				pointerIndex = 0;
				if (mMultiTouchAvail) {
					pointerIndex = MotionEventWrapper.findPointerIndex(me, 1);
				}		
				mShownPointer = -1;
			}
			
			if (DEBUG) Log.d(TAG, "pointerIndex " + Integer.toString(pointerIndex));
			if (pointerIndex >= 0)
			{
				// Up event
				int touchX = 0;
				int touchY = 0;
				if (mMultiTouchAvail) {
					final float x = MotionEventWrapper.getX(me, pointerIndex);
					touchX = (int)x + mHorizontalCorrection - getPaddingLeft();
					final float y = MotionEventWrapper.getY(me, pointerIndex);
					touchY = (int)y + mVerticalCorrection - getPaddingTop();
				}
				else {
					touchX = (int)me.getX() + mHorizontalCorrection - getPaddingLeft();
					touchY = (int)me.getY() + mVerticalCorrection - getPaddingTop();
				}

				int keyIndex = getKeyIndices(touchX, touchY, null);

				// Validate key
				mHandler.removeMessages(MSG_SHOW_PREVIEW);
				mHandler.removeMessages(MSG_REPEAT);
				mHandler.removeMessages(MSG_LONGPRESS);
				if (keyIndex == mCurrentKey) {
					mCurrentKeyTime += eventTime - mLastMoveTime;
				} else {
					resetMultiTap();
					mLastKey = mCurrentKey;
					mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime;
					mCurrentKey = keyIndex;
					if (DEBUG) Log.d(TAG, "1 mCurrentKey=" + Integer.toString(mCurrentKey));
					mCurrentKeyTime = 0;
				}
				if (mCurrentKeyTime < mLastKeyTime && mLastKey != NOT_A_KEY
						// Always follow quick slides if we are in the preview popup
						&& mPopupParent == this) {
					mCurrentKey = mLastKey;
					if (DEBUG) Log.d(TAG, "2 mCurrentKey=" + Integer.toString(mCurrentKey));
					touchX = mLastCodeX;
					touchY = mLastCodeY;
					if (DEBUG) Log.d(TAG, "mLastCodeX=" + Integer.toString(mLastCodeX));
				}
				showPreview(NOT_A_KEY);
				Arrays.fill(mKeyIndices, NOT_A_KEY);
				// If we're not on a repeating key (which sends on a DOWN event)
				if (mRepeatKeyIndex == NOT_A_KEY && !mMiniKeyboardOnScreen && !mAbortKey) {
					detectAndSendKey(mCurrentKey, touchX, touchY, eventTime, pointerCount);
				}
				invalidateKey(keyIndex);
				mRepeatKeyIndex = NOT_A_KEY;

				mLastX = touchX;
				mLastY = touchY;
			}

			handleMotionEvent(me, action, eventTime, pointerCount);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return true;
	}
	private int mLastRealX, mLastRealY;
	boolean handleWindowMove(MotionEvent me)
	{
		if( mMover == null )
			return false;
		int action = me.getAction();
		int x = (int)me.getRawX(), y =(int)me.getRawY();
		int dx = x - mLastRealX, dy = y - mLastRealY;

		if( mMoving && action == MotionEvent.ACTION_MOVE )
			mMover.move(dx, dy);

		mLastRealX = (int)me.getRawX();
		mLastRealY = (int)me.getRawY();
		if( action == MotionEvent.ACTION_DOWN )
			mMoving = false;
		return mMoving && action == MotionEvent.ACTION_MOVE;
	}

	private void handleMotionEvent(MotionEvent me, int action, long eventTime, int pointerCount) {
		int pointerIndex;
		switch (action) {
        case MotionEvent.ACTION_MOVE:
            pointerIndex = 0;
            if (mMultiTouchAvail) {
                pointerIndex = MotionEventWrapper.findPointerIndex(me, mShownPointer);
            }
            if (pointerIndex >= 0)
            {
                int touchX = 0;
                int touchY = 0;
                if (mMultiTouchAvail) {
                    final float x = MotionEventWrapper.getX(me, pointerIndex);
                    touchX = (int)x + mHorizontalCorrection - getPaddingLeft();
                    final float y = MotionEventWrapper.getY(me, pointerIndex);
                    touchY = (int)y + mVerticalCorrection - getPaddingTop();
                }
                else {
                    touchX = (int)me.getX() + mHorizontalCorrection - getPaddingLeft();
                    touchY = (int)me.getY() + mVerticalCorrection - getPaddingTop();
                }

                final int keyIndex = getKeyIndices(touchX, touchY, null);

                boolean continueLongPress = false;
                if (keyIndex != NOT_A_KEY) {
                    if (mCurrentKey == NOT_A_KEY) {
                        mCurrentKey = keyIndex;
                        if (DEBUG) Log.d(TAG, "3 mCurrentKey=" + Integer.toString(mCurrentKey));
                        mCurrentKeyTime = eventTime - mDownTime;
                    } else {
                        if (keyIndex == mCurrentKey) {
                            mCurrentKeyTime += eventTime - mLastMoveTime;
                            continueLongPress = true;
                        } else if (mRepeatKeyIndex == NOT_A_KEY) {
                            resetMultiTap();
                            mLastKey = mCurrentKey;
                            mLastCodeX = mLastX;
                            mLastCodeY = mLastY;
                            if (DEBUG) Log.d(TAG, "change mLastCodeX=" + Integer.toString(mLastCodeX));
                            mLastKeyTime =
                                mCurrentKeyTime + eventTime - mLastMoveTime;
                            mCurrentKey = keyIndex;
                            if (DEBUG) Log.d(TAG, "4 mCurrentKey=" + Integer.toString(mCurrentKey));
                            mCurrentKeyTime = 0;
                        }
                    }
                }
                if (!continueLongPress) {
                    // Cancel old longpress
                    mHandler.removeMessages(MSG_LONGPRESS);
                    // Start new longpress if key has changed
                    if (keyIndex != NOT_A_KEY) {
                        Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
                        mHandler.sendMessageDelayed(msg, mLongpressDuration);
                    }

					mLastRealX = (int)me.getRawX();
					mLastRealY = (int)me.getRawY();
					mMoving = mKeys[keyIndex].codes[0] == 32;
                }
				else
				{

				}
                showPreview(mCurrentKey);
                mLastX = touchX;
                mLastY = touchY;
            }
            break;

        case MotionEvent.ACTION_DOWN:
        case mActionPointer1Down:
        case mActionPointer2Down:
            // Set the current shown pointer
            mShownPointer = (action == mActionPointer2Down ? 1 : 0);
            if (pointerCount == 1) {
                mShownPointer = me.getPointerId(0);
            }
            pointerIndex = 0;
            if (mMultiTouchAvail) {
                pointerIndex = MotionEventWrapper.findPointerIndex(me, mShownPointer);
            }
            if (pointerIndex >= 0)
            {
                int touchX = 0;
                int touchY = 0;
                if (mMultiTouchAvail) {
                    final float x = MotionEventWrapper.getX(me, pointerIndex);
                    touchX = (int)x + mHorizontalCorrection - getPaddingLeft();
                    final float y = MotionEventWrapper.getY(me, pointerIndex);
                    touchY = (int)y + mVerticalCorrection - getPaddingTop();
                }
                else {
                    touchX = (int)me.getX() + mHorizontalCorrection - getPaddingLeft();
                    touchY = (int)me.getY() + mVerticalCorrection - getPaddingTop();
                }

                final int keyIndex = getKeyIndices(touchX, touchY, null);
                mCurrentKey = keyIndex;
                if (DEBUG) Log.d(TAG, "5 mCurrentKey=" + Integer.toString(mCurrentKey));

                mAbortKey = false;
                mStartX = touchX;
                mStartY = touchY;
                mLastCodeX = touchX;
                mLastCodeY = touchY;

                mLastKeyTime = 0;
                mCurrentKeyTime = 0;
                mLastKey = NOT_A_KEY;
                mDownTime = me.getEventTime();
                mLastMoveTime = mDownTime;
                // Don't check multitap in T9 prediction mode
                boolean multitap = !isT9PredictionOn();
                if (!multitap && (keyIndex != NOT_A_KEY)) {
                    final Key key = mKeys[keyIndex];
                    if (key.forceMultitap) {
                        multitap = true;
                    }
                }
                if (multitap) {
                    checkMultiTap(eventTime, keyIndex);
                } else {
                    resetMultiTap();
                }
                mKeyboardActionListener.onPress(keyIndex != NOT_A_KEY ?
                            mKeys[keyIndex].codes[0] : 0);

                if (mCurrentKey >= 0 && mKeys[mCurrentKey].repeatable
                        && (action == MotionEvent.ACTION_DOWN)) {
                    mRepeatKeyIndex = mCurrentKey;
                    repeatKey();
                    final Message msg = mHandler.obtainMessage(MSG_REPEAT);
                    mHandler.sendMessageDelayed(msg, REPEAT_START_DELAY);
                }
                if (mCurrentKey != NOT_A_KEY) {
                    final Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
                    mHandler.sendMessageDelayed(msg, mLongpressDuration);
                }
                showPreview(keyIndex);

                mLastX = touchX;
                mLastY = touchY;
            }
            break;
        }
	}

	private boolean handlePopupKeyboardSlide(MotionEvent me) {
		try {
            if (mMiniKeyboard.mAttached) {
                // If the popup keyboard is ready to receive events, translate it now
                if (DEBUG) Log.d(TAG, "xxxx translating event " + me.toString());
                return mMiniKeyboard.translateEvent(me);
            } else {
                // Otherwise push the event in the queue and try to process it later
                if (DEBUG) Log.d(TAG, "xxxx delaying event " + me.toString());
                // Create a new event otherwise translate will not work...
                MotionEvent newMe = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                        me.getAction(), me.getX(), me.getY() , me.getMetaState());
                mMiniKeyboard.pushEvent(newMe);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // this may happen on android 1.5, just ignore and continue...
        }
		return true;
	}


	private boolean onModifiedTouchEvent(MotionEvent me, boolean possiblePoly) {
		
		// Don't do this if we are in the popup keyboard (will cause a crash)
		if (mPopupParent != this) {
			return false;
		}
		
		//		int touchX = (int) me.getX() - getPaddingLeft();
		//		int touchY = (int) me.getY() + mVerticalCorrection - getPaddingTop();
		final int action = me.getAction();
		//final long eventTime = me.getEventTime();
		//mOldEventTime = eventTime;
		//int keyIndex = getKeyIndices(touchX, touchY, null);
		mPossiblePoly = possiblePoly;

		// Track the last few movements to look for spurious swipes.
		if (action == MotionEvent.ACTION_DOWN) mSwipeTracker.clear();
		mSwipeTracker.addMovement(me);

		if (mGestureDetector.onTouchEvent(me)) {
			showPreview(NOT_A_KEY);
			mHandler.removeMessages(MSG_REPEAT);
			mHandler.removeMessages(MSG_LONGPRESS);
			return true;
		}
		return false;
	}
	
	
	// Translate an event received by the main keyboard to an event for the mini keyboard
	public boolean translateEvent(MotionEvent me) {
		// Take the real x position into account only if the finger has been moved enough
		// (to handle situations in which the initially selected letter is not above the long
		// press touch point)
		float offset = 0;
		if (mCenterX != -1) {
			// Threshold = 1/4 of popup key width
			if (Math.abs(me.getX() - mFirstX) > getWidth() / 40) {
				mCenterX = -1;
				if (DEBUG) Log.d(TAG, "Threshold exceeded: starting to process events (" +
						Float.toString(me.getX()) + " - " + Float.toString(mFirstX));
			} else {
				if (DEBUG) Log.d(TAG, "Moving x position to key center");
				offset = me.getX() - mCenterX;
			}
		}
		me.offsetLocation(mParentLocation[0] - mLocation[0] - offset, mParentLocation[1] - mLocation[1]);
		return dispatchTouchEvent(me);
	}


	private boolean repeatKey() {
		Key key = mKeys[mRepeatKeyIndex];
		detectAndSendKey(mCurrentKey, key.x, key.y, mLastTapTime, 1);
		return true;
	}

	protected void swipeRight() {
		mKeyboardActionListener.swipeRight();
	}

	protected void swipeLeft() {
		mKeyboardActionListener.swipeLeft();
	}

	protected void swipeUp() {
		mKeyboardActionListener.swipeUp();
	}

	protected void swipeDown() {
		mKeyboardActionListener.swipeDown();
	}

	public void closing() {

		if (mPreviewPopup.isShowing()) {
			mPreviewPopup.dismiss();
		}
		removeMessages();

		dismissPopupKeyboard();
		mBuffer = null;
		mCanvas = null;
		mOffsetInWindow = null;
		mMiniKeyboardCache.clear();
	}

	private void removeMessages() {
		mHandler.removeMessages(MSG_REPEAT);
		mHandler.removeMessages(MSG_LONGPRESS);
		mHandler.removeMessages(MSG_SHOW_PREVIEW);
	}

	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		closing();
	}

	private void dismissPopupKeyboardAsync() {
		mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_DISMISS_POPUP), 0);
	}
	
	private void dismissPopupKeyboard() {
		if (DEBUG) Log.d(TAG, "dismissPopupKeyboard");
		if (mPopupKeyboard.isShowing()) {
			if (DEBUG) Log.d(TAG, "dismiss");
			mPopupKeyboard.dismiss();
			mMiniKeyboardOnScreen = false;
			invalidateAllKeys();
		}
	}

	public boolean handleBack() {
		if (mPopupKeyboard.isShowing()) {
			dismissPopupKeyboard();
			return true;
		}
		return false;
	}

	private void resetMultiTap() {
		mLastSentIndex = NOT_A_KEY;
		mTapCount = 0;
		mLastTapTime = -1;
		mInMultiTap = false;
	}

	private void checkMultiTap(long eventTime, int keyIndex) {
		if (keyIndex == NOT_A_KEY) return;
		Key key = mKeys[keyIndex];
		if (key.codes.length > 1) {
			mInMultiTap = true;
			if (eventTime < mLastTapTime + mMultitapInterval
					&& keyIndex == mLastSentIndex) {
				mTapCount = (mTapCount + 1) % key.codes.length;
				return;
			} else {
				mTapCount = -1;
				return;
			}
		}
		if (eventTime > mLastTapTime + mMultitapInterval || keyIndex != mLastSentIndex) {
			resetMultiTap();
		}
	}

	public void setPopupKeyboardDisabled(boolean disablePopupKeyboard) {
		this.popupKeyboardDisabled = disablePopupKeyboard;
	}
	public interface WindowMover
	{
		public void move(int dx, int dy);
	}
	private WindowMover mMover;
	private boolean mMoving;
	
	public void setWindowMover(WindowMover mover)
	{
		mMover = mover;
	}

}
