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

package com.dexilog.smartkeyboard.keyboard;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Vector;

import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.util.Xml;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.StringTokenizer;

import com.dexilog.smartkeyboard.R;


/**
 * Loads an XML description of a keyboard and stores the attributes of the keys. A keyboard
 * consists of rows of keys.
 * <p>The layout file for a keyboard contains XML that looks like the following snippet:</p>
 * <pre>
 * &lt;Keyboard
 *         android:keyWidth="%10p"
 *         android:keyHeight="50px"
 *         android:horizontalGap="2px"
 *         android:verticalGap="2px" &gt;
 *     &lt;Row android:keyWidth="32px" &gt;
 *         &lt;Key android:keyLabel="A" /&gt;
 *         ...
 *     &lt;/Row&gt;
 *     ...
 * &lt;/Keyboard&gt;
 * </pre>
 * @attr ref android.R.styleable#Keyboard_keyWidth
 * @attr ref android.R.styleable#Keyboard_keyHeight
 * @attr ref android.R.styleable#Keyboard_horizontalGap
 * @attr ref android.R.styleable#Keyboard_verticalGap
 */
public class Keyboard {

	public enum MicDisplay {
		HIDDEN,
		REPLACE_COMMA,
		ABOVE_COMMA
	}

	// Keyboard XML Tags
	private static final String TAG_KEYBOARD = "Keyboard";
	private static final String TAG_ROW = "Row";
	private static final String TAG_KEY = "Key";

	public static final int EDGE_LEFT = 0x01;
	public static final int EDGE_RIGHT = 0x02;
	public static final int EDGE_TOP = 0x04;
	public static final int EDGE_BOTTOM = 0x08;

	public static final int KEYCODE_SHIFT = -1;
	public static final int KEYCODE_MODE_CHANGE = -2;
	public static final int KEYCODE_CANCEL = -3;
	public static final int KEYCODE_DONE = -4;
	public static final int KEYCODE_DELETE = -5;
	public static final int KEYCODE_ALT = -6;

	private final static int[] KEY_STATE_NORMAL_ON = { 
		android.R.attr.state_checkable, 
		android.R.attr.state_checked
	};

	private final static int[] KEY_STATE_PRESSED_ON = { 
		android.R.attr.state_pressed, 
		android.R.attr.state_checkable, 
		android.R.attr.state_checked 
	};

	private final static int[] KEY_STATE_NORMAL_OFF = { 
		android.R.attr.state_checkable 
	};

	private final static int[] KEY_STATE_PRESSED_OFF = { 
		android.R.attr.state_pressed, 
		android.R.attr.state_checkable 
	};

	private final static int[] KEY_STATE_NORMAL = {
	};

	private final static int[] KEY_STATE_PRESSED = {
		android.R.attr.state_pressed
	};

	private static final int KEYCODE_SPACE = ' ';

	/** Horizontal gap default for all rows */
	private int mDefaultExactHorizontalGap;

	/** Default key width */
	private int mDefaultExactWidth;

	/** Default key height */
	private int mDefaultHeight;

	/** Default gap between rows */
	private int mDefaultVerticalGap;

	/** Is the keyboard in the shifted state */
	private boolean mShifted;

	/** Key instance for the shift key, if present */
	private Key mShiftKey;

	/** Key index for the shift key, if present */
	private int mShiftKeyIndex = -1;

	/** Total height of the keyboard, including the padding and keys */
	private int mTotalHeight;

	/** 
	 * Total width of the keyboard, including left side gaps and keys, but not any gaps on the
	 * right side.
	 */
	private int mTotalWidth;

	/** List of keys in this keyboard */
	private List<Key> mKeys;
	private Key[] mKeyArray;

	/** List of modifier keys such as Shift & Alt, if any */
	private List<Key> mModifierKeys;

	/** Width of the screen available to fit the keyboard */
	private int mDisplayWidth;

	/** Height of the screen */
	private int mDisplayHeight;

	/** Keyboard mode, or zero, if none.  */
	protected int mKeyboardMode;

	// Variables for pre-computing nearest keys.

	private static final int GRID_WIDTH = 10;
	private static final int GRID_HEIGHT = 5;
	private static final int GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;
	private int mCellWidth;
	private int mCellHeight;
	private int[][] mGridNeighbors;
	private int mProximityThreshold;
	/** Number of key widths from current touch point to search for nearest keys. */
	private static float SEARCH_DISTANCE = 1.8f;

	static final int KEYCODE_DIC = -100; // Toggle dictionary
	public static final int KEYCODE_LANG = -101; // Change language
	public static final int KEYCODE_LEFT = -102;
	public static final int KEYCODE_RIGHT = -103;
	public static final int KEYCODE_UP = -104;
	public static final int KEYCODE_DOWN = -105;
	public static final int KEYCODE_TAB = -106;
	public static final int KEYCODE_ARROWS = -107;
	public static final int KEYCODE_MIC = -108;
	public static final int KEYCODE_T9 = -109;
	public static final int KEYCODE_NEXT = -110;
	public static final int KEYCODE_SETLANG = -111; // Key in the lang popup
	public static final int KEYCODE_NEXT_SPACE = -112;
	public static final int KEYCODE_EMOJI_PREV = -113;
	public static final int KEYCODE_EMOJI_NEXT = -114;
	public static final int KEYCODE_EMOJI_NUM = -115;
	public static final int KEYCODE_LATEST_LANG = - 116;
	public static final int KEYCODE_NEXT_SPACE2 = -120;
	public static final int KEYCODE_EMOJI_TAB1 = -131;
	public static final int KEYCODE_EMOJI_TAB2 = -132;
	public static final int KEYCODE_EMOJI_TAB3 = -133;
	public static final int KEYCODE_EMOJI_TAB4 = -134;
	public static final int KEYCODE_EMOJI_TAB5 = -135;
	public static final int KEYCODE_EMOJI_TAB6 = -136;
	public static final int KEYCODE_EMOJI_TAB7 = -137;
	public static final int KEYCODE_EMOJI_TAB8 = -138;

	public static final int KEYCODE_DAKUTEN = 12441;

	private static final String TAG = "SmartKeyboard";

	private static final String TAG_INCLUDE = "Include";

	public static final int SMILEY_KEY_AUTO = 0;
	public static final int SMILEY_KEY_OFF = 1;
	public static final int SMILEY_KEY_ON = 2;

	private boolean mShiftLockEnabled = false;
	private Key mSpaceKey;
	private Key mEnterKey;
	private Key mLangKey;
	private Key mT9Key;
	private Key mMicKey;
	private Key mNextKey;
	private String mLang;
	
	private boolean mNumbersTop;

	private int mMyTotalHeight = 0;

	private static final float OVERLAP_PERCENTAGE_LOW_PROB = 0.70f;
	private static final float OVERLAP_PERCENTAGE_HIGH_PROB = 0.85f;

	private static final int[] mMicCodes = new int[] {-108};

	private int mSpacebarVerticalCorrection = 0;

	private int[] mPrefLetterFrequencies;
	private int mPrefLetter;
	private int mPrefLetterX;
	private int mPrefLetterY;
	private int mPrefDistance;

	protected EmojiCategories mEmojiCategories;


	/**
	 * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate. 
	 * Some of the key size defaults can be overridden per row from what the {@link Keyboard}
	 * defines. 
	 * @attr ref android.R.styleable#Keyboard_keyWidth
	 * @attr ref android.R.styleable#Keyboard_keyHeight
	 * @attr ref android.R.styleable#Keyboard_horizontalGap
	 * @attr ref android.R.styleable#Keyboard_verticalGap
	 * @attr ref android.R.styleable#Keyboard_Row_rowEdgeFlags
	 * @attr ref android.R.styleable#Keyboard_Row_keyboardMode
	 */
	public static class Row {
		/** Default width of a key in this row. */
		public int defaultExactWidth;
		/** Default height of a key in this row. */
		public int defaultHeight;
		/** Default horizontal gap between keys in this row. */
		public int defaultExactHorizontalGap;
		/** Vertical gap following this row. */
		public int verticalGap;
		/**
		 * Edge flags for this row of keys. Possible values that can be assigned are
		 * {@link Keyboard#EDGE_TOP EDGE_TOP} and {@link Keyboard#EDGE_BOTTOM EDGE_BOTTOM}  
		 */
		public int rowEdgeFlags;

		/** The keyboard mode for this row */
		public int mode;
		public boolean numbers;

		private Keyboard parent;

		public Row(Keyboard parent) {
			this.parent = parent;
		}

		public Row(Resources res, Keyboard parent, XmlResourceParser parser) {
			this.parent = parent;
			TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser), 
					R.styleable.Keyboard);
			defaultExactWidth = getDimensionOrFraction(a, 
					R.styleable.Keyboard_android_keyWidth, 
					100 * parent.mDisplayWidth, parent.mDefaultExactWidth);
			defaultHeight = getDimensionOrFraction(a, 
					R.styleable.Keyboard_android_keyHeight, 
					parent.mDisplayHeight, parent.mDefaultHeight);
			defaultExactHorizontalGap = getDimensionOrFraction(a,
					R.styleable.Keyboard_android_horizontalGap, 
					100 * parent.mDisplayWidth, parent.mDefaultExactHorizontalGap);
			verticalGap = getDimensionOrFraction(a, 
					R.styleable.Keyboard_android_verticalGap, 
					parent.mDisplayHeight, parent.mDefaultVerticalGap);
			a.recycle();
			a = res.obtainAttributes(Xml.asAttributeSet(parser),
					R.styleable.Keyboard_Row);
			rowEdgeFlags = a.getInt(R.styleable.Keyboard_Row_android_rowEdgeFlags, 0);
			// XXX
			rowEdgeFlags = 0;
			mode = a.getResourceId(R.styleable.Keyboard_Row_android_keyboardMode,
					0);
			numbers = parser.getAttributeBooleanValue(null, "numbers", false);
			// Override mode in case of emoji
			int emoji = parser.getAttributeIntValue(null, "emoji", 0);
			if (emoji != 0) {
				mode = emoji;
			}
			a.recycle();
		}
	}

	/**
	 * Class for describing the position and characteristics of a single key in the keyboard.
	 * 
	 * @attr ref android.R.styleable#Keyboard_keyWidth
	 * @attr ref android.R.styleable#Keyboard_keyHeight
	 * @attr ref android.R.styleable#Keyboard_horizontalGap
	 * @attr ref android.R.styleable#Keyboard_Key_codes
	 * @attr ref android.R.styleable#Keyboard_Key_keyIcon
	 * @attr ref android.R.styleable#Keyboard_Key_keyLabel
	 * @attr ref android.R.styleable#Keyboard_Key_iconPreview
	 * @attr ref android.R.styleable#Keyboard_Key_isSticky
	 * @attr ref android.R.styleable#Keyboard_Key_isRepeatable
	 * @attr ref android.R.styleable#Keyboard_Key_isModifier
	 * @attr ref android.R.styleable#Keyboard_Key_popupKeyboard
	 * @attr ref android.R.styleable#Keyboard_Key_popupCharacters
	 * @attr ref android.R.styleable#Keyboard_Key_keyOutputText
	 * @attr ref android.R.styleable#Keyboard_Key_keyEdgeFlags
	 */
	public class Key {
		/** 
		 * All the key codes (unicode or custom code) that this key could generate, zero'th 
		 * being the most important.
		 */
		public int[] codes;

		/** Label to display */
		public CharSequence label;

		/** Icon to display instead of a label. Icon takes precedence over a label */
		public Drawable icon;
		/** Preview version of the icon, for the preview popup */
		public Drawable iconPreview;
		/** Width of the key, not including the gap */
		public int width;
		/** Height of the key, not including the gap */
		public int height;
		/** The horizontal gap before this key */
		public int gap;
		/** Whether this key is sticky, i.e., a toggle key */
		public boolean sticky;
		/** X coordinate of the key in the keyboard layout */
		public int x;
		/** Y coordinate of the key in the keyboard layout */
		public int y;
		/** The current pressed state of this key */
		public boolean pressed;
		/** If this is a sticky key, is it on? */
		public boolean on;
		/** Text to output when pressed. This can be multiple characters, like ".com" */
		public CharSequence text;
		/** Popup characters */
		public CharSequence popupCharacters;
		/** Popup characters when accents priority is on */
		public CharSequence popupAccents;
		/** Width * 100 */
		public int exactWidth;
		/** Gap * 100 */
		public int exactGap;
		// Special flag for Hindi ligatures, to treat a text as a sequence of chars
		public boolean textSequence = false;

		/** 
		 * Flags that specify the anchoring to edges of the keyboard for detecting touch events
		 * that are just out of the boundary of the key. This is a bit mask of 
		 * {@link Keyboard#EDGE_LEFT}, {@link Keyboard#EDGE_RIGHT}, {@link Keyboard#EDGE_TOP} and
		 * {@link Keyboard#EDGE_BOTTOM}.
		 */
		public int edgeFlags;
		/** Whether this is a modifier key, such as Shift or Alt */
		public boolean modifier;
		/** The keyboard that this key belongs to */
		private Keyboard keyboard;
		/**
		 * If this key pops up a mini keyboard, this is the resource id for the XML layout for that
		 * keyboard.
		 */
		public int popupResId;
		/** Whether this key repeats itself when held down */
		public boolean repeatable;
		public int altIconID;

		boolean mStickyEnabled = true;
		public String altLabel;
		public int iconID = 0;
		public int textSize = 0; // 0 = normal, 1 = small, 2 = very small
		public boolean disabled = false;
		public int origExactWidth;
		public String origLabel;
		public boolean forceMultitap = false;


		/** Create an empty key with no attributes. */
		public Key(Row parent) {
			keyboard = parent.parent;
			height = parent.defaultHeight;
			exactWidth = parent.defaultExactWidth;
			exactGap = parent.defaultExactHorizontalGap;
			width = Math.round(exactWidth / 100);
			gap = Math.round(exactGap / 100);
			edgeFlags = parent.rowEdgeFlags;
		}

		/** Create a key with the given top-left coordinate and extract its attributes from
		 * the XML parser.
		 * @param res resources associated with the caller's context
		 * @param parent the row that this key belongs to. The row must already be attached to
		 * a {@link Keyboard}.
		 * @param exactX the x coordinate of the top-left
		 * @param y the y coordinate of the top-left
		 * @param parser the XML parser containing the attributes for this key
		 */
		public Key(Resources res, Row parent, int exactX, int y, XmlResourceParser parser) {
			this(parent);

			TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser), 
					R.styleable.Keyboard);

			exactWidth = getDimensionOrFraction(a, 
					R.styleable.Keyboard_android_keyWidth,
					100 * keyboard.mDisplayWidth, parent.defaultExactWidth);
			width = Math.round(exactWidth / 100);
			height = getDimensionOrFraction(a, 
					R.styleable.Keyboard_android_keyHeight,
					keyboard.mDisplayHeight, parent.defaultHeight);
			exactGap = getDimensionOrFraction(a, 
					R.styleable.Keyboard_android_horizontalGap,
					100 * keyboard.mDisplayWidth, parent.defaultExactHorizontalGap);
			gap = Math.round(exactGap / 100);
			a.recycle();
			a = res.obtainAttributes(Xml.asAttributeSet(parser),
					R.styleable.Keyboard_Key);

			this.x = Math.round((exactX + exactGap) / 100);
			this.y = y;
			
			TypedValue codesValue = new TypedValue();
			a.getValue(R.styleable.Keyboard_Key_android_codes, 
					codesValue);
			if (codesValue.type == TypedValue.TYPE_INT_DEC 
					|| codesValue.type == TypedValue.TYPE_INT_HEX) {
				codes = new int[] { codesValue.data };
			} else if (codesValue.type == TypedValue.TYPE_STRING) {
				codes = parseCSV(codesValue.string.toString());
			}

			iconPreview = a.getDrawable(R.styleable.Keyboard_Key_android_iconPreview);
			if (iconPreview != null) {
				iconPreview.setBounds(0, 0, iconPreview.getIntrinsicWidth(), 
						iconPreview.getIntrinsicHeight());
			}
			popupCharacters = a.getText(
					R.styleable.Keyboard_Key_android_popupCharacters);
			popupAccents = parser.getAttributeValue(null, "popupAccents");
			if (popupAccents == null) {
				popupAccents = popupCharacters;
			}
			popupResId = a.getResourceId(
					R.styleable.Keyboard_Key_android_popupKeyboard, 0);
			repeatable = a.getBoolean(
					R.styleable.Keyboard_Key_android_isRepeatable, false);
			modifier = a.getBoolean(
					R.styleable.Keyboard_Key_android_isModifier, false);
			sticky = a.getBoolean(
					R.styleable.Keyboard_Key_android_isSticky, false);
			edgeFlags = a.getInt(R.styleable.Keyboard_Key_android_keyEdgeFlags, 0);
			edgeFlags |= parent.rowEdgeFlags;

			icon = a.getDrawable(
					R.styleable.Keyboard_Key_android_keyIcon);
			if (icon != null) {
				icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
			}
			text = a.getText(R.styleable.Keyboard_Key_android_keyOutputText);
			label = a.getText(R.styleable.Keyboard_Key_android_keyLabel);

			if (codes == null && !TextUtils.isEmpty(label)) {
				codes = new int[] { label.charAt(0) };
			}
			a.recycle();


			origExactWidth = exactWidth;
			// Check if there is an alternative label
			altLabel = parser.getAttributeValue(null, "altLabel");
			if (parser.getAttributeBooleanValue(null, "smallText", false)) {
				textSize = 1;
			} else if (parser.getAttributeBooleanValue(null, "verySmallText", false)) {
				textSize = 2;
			} else if (parser.getAttributeBooleanValue(null, "bigText", false)) {
				textSize = 3;
			} 
			iconID = parser.getAttributeResourceValue(null, "iconID", 0);
			if (popupCharacters != null) {
				popupResId = R.xml.popup;
			}
			if (parser.getAttributeBooleanValue(null, "forceMultitap", false)) {
				forceMultitap = true;
			}
			float commaWidth = parser.getAttributeFloatValue(null, "commaWidth", -1);
			if (commaWidth != -1 && GlobalResources.mHideComma) {
				// Handle hide comma
				if (commaWidth == 0) {
					disabled = true;
					width = 0;
					exactWidth = 0;
				} else {
					// Add comma width to space key
					exactWidth += Math.round(keyboard.mDisplayWidth * commaWidth);
					width = Math.round(exactWidth / 100);
					origExactWidth = exactWidth;
				}
			}
			float periodWidth = parser.getAttributeFloatValue(null, "periodWidth", -1);
			if (periodWidth != -1 && GlobalResources.mHidePeriod) {
				// Handle hide period
				if (periodWidth == 0) {
					disabled = true;
					width = 0;
					exactWidth = 0;
				} else {
					// Add period width to space key
					exactWidth += Math.round(keyboard.mDisplayWidth * periodWidth);
					width = Math.round(exactWidth / 100);
					origExactWidth = exactWidth;
				}
			}
		}

		/**
		 * Informs the key that it has been pressed, in case it needs to change its appearance or
		 * state.
		 * @see #onReleased(boolean)
		 */
		public void onPressed() {
			pressed = !pressed;
		}



		int[] parseCSV(String value) {
			int count = 0;
			int lastIndex = 0;
			if (value.length() > 0) {
				count++;
				while ((lastIndex = value.indexOf(",", lastIndex + 1)) > 0) {
					count++;
				}
			}
			int[] values = new int[count];
			count = 0;
			StringTokenizer st = new StringTokenizer(value, ",");
			while (st.hasMoreTokens()) {
				try {
					values[count++] = Integer.parseInt(st.nextToken());
				} catch (NumberFormatException nfe) {
					Log.e(TAG, "Error parsing keycodes " + value);
				}
			}
			return values;
		}



		/**
		 * Returns the drawable state for the key, based on the current state and type of the key.
		 * @return the drawable state of the key.
		 * @see android.graphics.drawable.StateListDrawable#setState(int[])
		 */
		public int[] getCurrentDrawableState() {
			int[] states = KEY_STATE_NORMAL;

			if (on) {
				if (pressed) {
					states = KEY_STATE_PRESSED_ON;
				} else {
					states = KEY_STATE_NORMAL_ON;
				}
			} else {
				if (sticky) {
					if (pressed) {
						states = KEY_STATE_PRESSED_OFF;
					} else {
						states = KEY_STATE_NORMAL_OFF;
					}
				} else {
					if (pressed) {
						states = KEY_STATE_PRESSED;
					}
				}
			}
			return states;
		}

		public void setSticky(boolean enabled) {
			mStickyEnabled = enabled;
		}

		public void onReleased(boolean inside) {
			if (mStickyEnabled) {
				pressed = !pressed;
				if (sticky) {
					on = !on;
				}
			} else
			{
				// Don't toggle the sticky key
				pressed = !pressed;
			}
		}

		/**
		 * Overriding this method so that we can reduce the target area for certain keys.
		 */
		public boolean isInside(int x, int y) {
			boolean result = Keyboard.this.isInside(this, x, y);
	        return result;
		}

		boolean isInsideSuper(int x, int y) {
			final boolean leftEdge = (edgeFlags & EDGE_LEFT) > 0;
			final boolean rightEdge = (edgeFlags & EDGE_RIGHT) > 0;
			final boolean topEdge = (edgeFlags & EDGE_TOP) > 0;
			final boolean bottomEdge = (edgeFlags & EDGE_BOTTOM) > 0;
			final int thisX = this.x;
			final int thisY = this.y;
			final int thisWidth = this.width;
			final int thisHeight = this.height;
			if ((x >= thisX || (leftEdge && x <= thisX + thisWidth)) 
					&& (x < thisX + thisWidth || (rightEdge && x >= thisX)) 
					&& (y >= thisY || (topEdge && y <= thisY + thisHeight))
					&& (y < thisY + thisHeight || (bottomEdge && y >= thisY))) {
				return true;
			} else {
				return false;
			}
		}

		public int squaredDistanceFrom(int x, int y) {
			if (codes[0] == 32 && y > this.y) {
				// Special handling for space key
				final int center = this.x + width / 2;
				x = center + (x - center) / 2;
			}
			int xDist = this.x + width / 2 - x;
			int yDist = this.y + height / 2 - y;
			return xDist * xDist + yDist * yDist;            
		}
	}

	/**
	 * Creates a keyboard from the given xml key layout file.
	 * @param context the application or service context
	 * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
	 */
	public Keyboard(Context context, int xmlLayoutResId) {
		this(context, xmlLayoutResId, 0, true, false, false, null);
	}

	/**
	 * Creates a keyboard from the given xml key layout file. Weeds out rows
	 * that have a keyboard mode defined but don't match the specified mode. 
	 * @param context the application or service context
	 * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
	 * @param modeId keyboard mode identifier
	 */
	public Keyboard(Context context, int xmlLayoutResId, int modeId, boolean isPortrait,
			boolean includeArrows, boolean numbersTop, EmojiCategories emojiCategories) {
		mDisplayWidth = GlobalResources.mKeyboardWidth;
		mDisplayHeight = GlobalResources.mKeyboardScreenHeight;
		//Log.d(TAG, "keyboard's display metrics:" + dm);
		mNumbersTop = numbersTop;
		mEmojiCategories = emojiCategories;
		
		// Take into account soft buttons on Archos tablets
		boolean hasButtonBar = false;
		try {
			Method m = Environment.class.getMethod("hasButtonBar", (Class[]) null);
			hasButtonBar = (Boolean)m.invoke(null, (Object[]) null);
		} catch (Exception e) {
		}
		if (hasButtonBar) {
			int buttonbarSize = context.getResources().getDimensionPixelSize(R.dimen.archos_buttonbar_size);
			Log.d(TAG, "Button bar size: " + Integer.toString(buttonbarSize));
			if (!isPortrait) {
				mDisplayWidth -= buttonbarSize;
			}
		}

		mDefaultExactHorizontalGap = 0;
		mDefaultExactWidth = 10 * mDisplayWidth;
		if (includeArrows && !isPortrait) {
			// Reduce keyboard width to add arrows on the side
			mDisplayWidth -= mDefaultExactWidth / 100;
		}
		mDefaultVerticalGap = 0;
		mDefaultHeight = mDisplayWidth / 10;
		mKeys = new ArrayList<Key>();
		mModifierKeys = new ArrayList<Key>();
		mKeyboardMode = modeId;
		mTotalHeight = loadKeyboard(context, xmlLayoutResId, 0, 0) - mDefaultVerticalGap;
		
		if (includeArrows) {
			addArrows(context, isPortrait);
		}
		buildKeyArray();
	}

	/**
	 * <p>Creates a blank keyboard from the given resource file and populates it with the specified
	 * characters in left-to-right, top-to-bottom fashion, using the specified number of columns.
	 * </p>
	 * <p>If the specified number of columns is -1, then the keyboard will fit as many keys as
	 * possible in each row.</p>
	 * @param context the application or service context
	 * @param layoutTemplateResId the layout template file, containing no keys.
	 * @param characters the list of characters to display on the keyboard. One key will be created
	 * for each character.
	 * @param columns the number of columns of keys to display. If this number is greater than the 
	 * number of keys that can fit in a row, it will be ignored. If this number is -1, the 
	 * keyboard will fit as many keys as possible in each row.
	 */
	public Keyboard(Context context, int layoutTemplateResId, 
			CharSequence characters, int columns, int horizontalPadding) {
		this(context, layoutTemplateResId);
		int x = 0;
		int y = 0;
		int column = 0;
		mTotalWidth = 0;

		Row row = new Row(this);
		row.defaultHeight = mDefaultHeight;
		row.defaultExactWidth = mDefaultExactWidth;
		row.defaultExactHorizontalGap = mDefaultExactHorizontalGap;
		row.verticalGap = mDefaultVerticalGap;
		row.rowEdgeFlags = EDGE_TOP | EDGE_BOTTOM;
		final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
		final int len = characters.length();
		// Special case for Hindi ligatures ज्ञ क्ष त्र श्र
		boolean hindi = (len == 4 && characters.charAt(2) == 2381);
		for (int i = 0; i < len; i++) {
			if (column >= maxColumns 
					|| x + mDefaultExactWidth / 100 + horizontalPadding > mDisplayWidth) {
				x = 0;
				y += mDefaultVerticalGap + mDefaultHeight;
				column = 0;
			}
			final Key key = new Key(row);
			key.x = x;
			key.y = y;
			char c = characters.charAt(i);
			if (hindi && i == 1) {
				// Special case for Hindi ligatures
				key.label = characters.subSequence(1, 4);
				key.text = key.label;
				key.textSequence = true;
				i = len;
			} else if (c == '\u0b84') {
				// Tamil "sri"
				key.label = "\u0bb6\u0bcd\u0bb0\u0bc0";
				key.text = key.label;
				key.textSequence = true;
			} else if (c == '\u0b98') {
				// Tamil "ks"
				key.label = "\u0b95\u0bcd\u0bb7";
				key.text = key.label;
				key.textSequence = true;
			} else {
				key.label = String.valueOf(c);
			}
			key.codes = new int[] { c };
			column++;
			x += key.width + key.gap;
			mKeys.add(key);
			if (x > mTotalWidth) {
				mTotalWidth = x;
			}
		}
		mTotalHeight = y + mDefaultHeight;
		buildKeyArray();
	}
	
	private void buildKeyArray() {
		mKeyArray = mKeys.toArray(new Key[mKeys.size()]);
	}

	public int getDisplayWidth() {
		return mDisplayWidth;
	}
	
	public Key[] getKeys() {
		return mKeyArray;
	}

	public List<Key> getModifierKeys() {
		return mModifierKeys;
	}

	public int getMinWidth() {
		return mTotalWidth;
	}

	public boolean isShifted() {
		return mShifted;
	}

	public int getShiftKeyIndex() {
		return mShiftKeyIndex;
	}
	

	private void computeNearestNeighbors() {
		// Round-up so we don't have any pixels outside the grid
		mCellWidth = (getMinWidth() + GRID_WIDTH - 1) / GRID_WIDTH;
		mCellHeight = (getHeight() + GRID_HEIGHT - 1) / GRID_HEIGHT;
		mGridNeighbors = new int[GRID_SIZE][];
		int[] indices = new int[mKeys.size()];
		final int gridWidth = GRID_WIDTH * mCellWidth;
		final int gridHeight = GRID_HEIGHT * mCellHeight;
		for (int x = 0; x < gridWidth; x += mCellWidth) {
			for (int y = 0; y < gridHeight; y += mCellHeight) {
				int count = 0;
				for (int i = 0; i < mKeys.size(); i++) {
					final Key key = mKeys.get(i);
					if (key.squaredDistanceFrom(x, y) < mProximityThreshold ||
							key.squaredDistanceFrom(x + mCellWidth - 1, y) < mProximityThreshold ||
							key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1) 
							< mProximityThreshold ||
							key.squaredDistanceFrom(x, y + mCellHeight - 1) < mProximityThreshold) {
						indices[count++] = i;
					}
				}
				int [] cell = new int[count];
				System.arraycopy(indices, 0, cell, 0, count);
				mGridNeighbors[(y / mCellHeight) * GRID_WIDTH + (x / mCellWidth)] = cell;
			}
		}
	}

	/**
	 * Returns the indices of the keys that are closest to the given point.
	 * @param x the x-coordinate of the point
	 * @param y the y-coordinate of the point
	 * @return the array of integer indices for the nearest keys to the given point. If the given
	 * point is out of range, then an array of size zero is returned.
	 */
	public int[] getNearestKeys(int x, int y) {
		if (mGridNeighbors == null) computeNearestNeighbors();
		if (x >= 0 && x < getMinWidth() && y >= 0 && y < getHeight()) {
			int index = (y / mCellHeight) * GRID_WIDTH + (x / mCellWidth);
			if (index < GRID_SIZE) {
				return mGridNeighbors[index];
			}
		}
		return new int[0];
	}

	protected Row createRowFromXml(Resources res, XmlResourceParser parser) {    	
		Row row = new Row(res, this, parser);

		// Scaling factor
		int height = (res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) ?
				GlobalResources.mKeyHeight : GlobalResources.mKeyHeightLandscape;
		row.defaultHeight = row.defaultHeight * (50 + height) / 100;
		return row;

	}


	protected int loadKeyboard(Context context, int xmlId, int initialX, int initialY) {
		boolean inKey = false;
		boolean inRow = false;
		int row = 0;
		int exactX = initialX * 100;
		int y = initialY;
		Key key = null;
		Row currentRow = null;
		Resources res = context.getResources();
		boolean skipRow = false;
		XmlResourceParser parser = res.getXml(xmlId); 

		try {
			int event;
			while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
				if (event == XmlResourceParser.START_TAG) {
					String tag = parser.getName();
					if (TAG_ROW.equals(tag)) {
						inRow = true;
						exactX = initialX * 100;
						currentRow = createRowFromXml(res, parser);
						skipRow = currentRow.mode != 0 && currentRow.mode != mKeyboardMode;
						// Check numbers row
						if (currentRow.numbers && !mNumbersTop) {
							skipRow = true;
						}
						if (skipRow) {
							skipToEndOfRow(parser);
							inRow = false;
						}
					} else if (TAG_KEY.equals(tag)) {
						inKey = true;
						key = createKeyFromXml(res, currentRow, exactX, y, parser);
						mKeys.add(key);
						if (key.codes[0] == KEYCODE_SHIFT) {
							mShiftKey = key;
							mShiftKeyIndex = mKeys.size()-1;
							mModifierKeys.add(key);
						} else if (key.codes[0] == KEYCODE_ALT) {
							mModifierKeys.add(key);
						}
					} else if (TAG_KEYBOARD.equals(tag)) {
						parseKeyboardAttributes(res, parser);
					} else if (TAG_INCLUDE.equals(tag)) {
						int include = parser.getAttributeResourceValue(null, "xml", 0);
						y += loadKeyboard(context, include, 0, y);
					}
				} else if (event == XmlResourceParser.END_TAG) {
					if (inKey) {
						inKey = false;
						exactX += key.exactGap + key.exactWidth;
						int x = Math.round(exactX / 100);
						if (x > mTotalWidth) {
							mTotalWidth = x;
						}
					} else if (inRow) {
						inRow = false;
						y += currentRow.verticalGap;
						y += currentRow.defaultHeight;
						row++;
					} else {
						// TODO: error or extend?
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Parse error:" + e);
			e.printStackTrace();
		}
		return y - initialY;
	}
	
	
	public void addArrows(Context context, boolean isPortrait) {
		if (isPortrait) {
			mTotalHeight += loadKeyboard(context, R.xml.arrows_horiz, 0, mTotalHeight);
		} else {
			loadKeyboard(context, R.xml.arrows_vert, mDisplayWidth, 0);
		}
	}
	
	
	private void parseKeyboardAttributes(Resources res, XmlResourceParser parser) {
		TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser), 
				R.styleable.Keyboard);

		mDefaultExactWidth = getDimensionOrFraction(a,
				R.styleable.Keyboard_android_keyWidth,
				100 * mDisplayWidth, 10 * mDisplayWidth);
		mDefaultHeight = getDimensionOrFraction(a,
				R.styleable.Keyboard_android_keyHeight,
				mDisplayHeight, 50);
		mDefaultExactHorizontalGap = getDimensionOrFraction(a,
				R.styleable.Keyboard_android_horizontalGap,
				100 * mDisplayWidth, 0);
		mDefaultVerticalGap = getDimensionOrFraction(a,
				R.styleable.Keyboard_android_verticalGap,
				mDisplayHeight, 0);
		mProximityThreshold = (int) (mDefaultExactWidth * SEARCH_DISTANCE / 100);
		mProximityThreshold = mProximityThreshold * mProximityThreshold; // Square it for comparison
		a.recycle();
	}

	static int getDimensionOrFraction(TypedArray a, int index, int base, int defValue) {
		TypedValue value = a.peekValue(index);
		if (value == null) return defValue;
		if (value.type == TypedValue.TYPE_DIMENSION) {
			return a.getDimensionPixelOffset(index, defValue);
		} else if (value.type == TypedValue.TYPE_FRACTION) {
			// Round it to avoid values like 47.9999 from getting truncated
			return Math.round(a.getFraction(index, base, base, defValue));
		}
		return defValue;
	}

	public Keyboard(Context context, int popupKeyboardId,
			List<String> popupStrings) {
		this(context, popupKeyboardId, buildString(popupStrings.size()), 9, 0);

		final int size = popupStrings.size();
		final Key[] keys = mKeyArray;
		for (int i=0; i<size; i++) {
			Key key = keys[i];
			key.label = popupStrings.get(i).substring(0, 2);
			// Hack to retrieve the lang index
			key.codes = new int[] { KEYCODE_SETLANG, i };
		}
	}

	public void setSpaceCorrection(int spaceCorrection) {
		mSpacebarVerticalCorrection = spaceCorrection;
	}

	// Build a string of given size
	static final CharSequence buildString(int size) {
		StringBuilder sb = new StringBuilder(size);
		for (int i=0; i<size; i++) {
			sb.append(" ");
		}
		return sb;
	}


	protected Key createKeyFromXml(Resources res,
			Row parent, int exactX, int y, XmlResourceParser parser) {

		Key key = new Key(res, parent, exactX, y, parser);
		handleSpecialCode(parser, key);
		return key;
	}

	protected void handleSpecialCode(XmlResourceParser parser, Key key) {
		switch (key.codes[0]) {
			case 10:
				if (!parser.getAttributeBooleanValue(null, "alwaysEnter", false)) {
					mEnterKey = key;
				}
				break;
			case KEYCODE_LANG:
				mLangKey = key;
				mLangKey.popupResId = R.xml.popup;
				break;
			case KEYCODE_MIC:
				mMicKey = key;
				break;
			case KEYCODE_T9:
				mT9Key = key;
				break;
			case 32:
				mSpaceKey = key;
				break;
			case KEYCODE_NEXT:
				mNextKey = key;
				break;
		}
	}

	public void setLanguage(String language) {
		if (mLangKey != null) {
			mLangKey.label = language;
		}
		mLang = language;
	}

	public void setMicButton(Resources res, MicDisplay micDisplay) {
		if (mMicKey != null) {
			mMicKey.altLabel = null;
			mMicKey.altIconID = 0;
			mMicKey.popupCharacters = null;
			mMicKey.popupAccents = null;
			mMicKey.popupResId = 0;
			if (micDisplay == MicDisplay.REPLACE_COMMA) {
				mMicKey.label = null;
				mMicKey.icon = res.getDrawable(R.drawable.mic_black);
				mMicKey.iconID = R.id.mic_key;
				mMicKey.codes = mMicCodes;
			} else {
				setCommaKey();
			}
			if (micDisplay == MicDisplay.ABOVE_COMMA) {
				mMicKey.altIconID = R.id.mic_key;
			}
			if (micDisplay == MicDisplay.HIDDEN && GlobalResources.mHidePeriod) {
				setPeriodKey();
			}
		}
	}

	private void setPeriodKey() {
		mMicKey.altLabel = ".";
		mMicKey.popupCharacters = ".";
		mMicKey.popupAccents = mMicKey.popupCharacters;
		mMicKey.popupResId = R.xml.popup;
	}

	private void setCommaKey() {
		if (mLang.equals("JP") || mLang.equals("ZH")) {
            mMicKey.label = "\u3001";
            mMicKey.codes = new int[]{0x3001};
        } else {
            mMicKey.label = ",";
            mMicKey.codes = new int[]{','};
        }
		mMicKey.icon = null;
		mMicKey.iconID = 0;
	}

	// Control whether shift lock is enabled
	public void setShiftLocked(boolean enabled) {
		int index = getShiftKeyIndex();
		if (index >= 0) {
			mShiftKey = mKeyArray[index];
			mShiftKey.setSticky(enabled);
			mShiftLockEnabled = enabled;
		}
	}


	public boolean setShifted(boolean shiftState) {
		if (mShiftKey != null) {
			mShiftKey.on = shiftState;
		}
		boolean shiftChanged = false;
		if (mShifted != shiftState) {
			mShifted = shiftState;
			shiftChanged = true;
		}
		if (mShiftKey != null && !mShiftLockEnabled) {
			mShiftKey.on = false;
		}
		return shiftChanged;
	}

	public int getHeight() {
		return mTotalHeight + mMyTotalHeight;
	}

	public boolean isT9Enabled() {
		if (mT9Key != null) {
			return mT9Key.on;
		} else {
			return false;
		}
	}

	public void enableT9(boolean enableT9) {
		if (mT9Key != null) {
			mT9Key.on = enableT9;
		}
	}

	public void setT9NextKey(boolean t9NextKey) {
		if (mNextKey != null) {
			mNextKey.disabled = !t9NextKey;
			mSpaceKey.exactWidth = mSpaceKey.origExactWidth + (t9NextKey ? 0 : mNextKey.exactWidth);
			mSpaceKey.width = Math.round(mSpaceKey.exactWidth / 100);
		}
	}

	public void setOptions(Resources res, int mode, int options, int smileyMode) {
		if (mEnterKey != null) {
			mEnterKey.popupCharacters = null;
			mEnterKey.popupResId = R.xml.popup_smileys;
			mEnterKey.iconID = 0;
			mEnterKey.text = null;
			if (smileyMode == SMILEY_KEY_ON) {
				setSmileyKey();
			} else {
				switch (options&(EditorInfo.IME_MASK_ACTION|EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
				case EditorInfo.IME_ACTION_GO:
					mEnterKey.iconPreview = null;
					mEnterKey.icon = null;
					mEnterKey.label = res.getText(R.string.label_go_key);
					break;
				case EditorInfo.IME_ACTION_NEXT:
					mEnterKey.iconPreview = null;
					mEnterKey.icon = null;
					mEnterKey.label = res.getText(R.string.label_next_key);
					break;
				case EditorInfo.IME_ACTION_DONE:
					mEnterKey.iconPreview = null;
					mEnterKey.icon = null;
					mEnterKey.label = res.getText(R.string.label_done_key);
					break;
				case EditorInfo.IME_ACTION_SEARCH:
					mEnterKey.iconPreview = res.getDrawable(
							R.drawable.sym_keyboard_feedback_search);
					mEnterKey.icon = res.getDrawable(
							R.drawable.sym_keyboard_search);
					mEnterKey.iconID = R.id.search_key;
					mEnterKey.label = null;
					break;
				case EditorInfo.IME_ACTION_SEND:
					mEnterKey.iconPreview = null;
					mEnterKey.icon = null;
					mEnterKey.label = res.getText(R.string.label_send_key);
					break;
				default:
					if (mode == KeyboardSwitcher.MODE_IM && smileyMode != SMILEY_KEY_OFF) {
						setSmileyKey();
					} else {
						mEnterKey.icon = res.getDrawable(
								R.drawable.sym_keyboard_feedback_return);
						mEnterKey.iconPreview = res.getDrawable(
								R.drawable.sym_keyboard_return);
						mEnterKey.iconID = R.id.return_key;
						mEnterKey.label = null;
					}
				}
			}
			// Set the initial size of the preview icon
			if (mEnterKey.iconPreview != null) {
				mEnterKey.iconPreview.setBounds(0, 0, 
						mEnterKey.iconPreview.getIntrinsicWidth(),
						mEnterKey.iconPreview.getIntrinsicHeight());
			}
		}
	}

	public void setSmileyKey() {
		mEnterKey.popupCharacters = null;
		mEnterKey.iconID = 0;
		mEnterKey.icon = null;
		mEnterKey.iconPreview = null;
		mEnterKey.label = "%smiley_00";
		mEnterKey.text = "%smiley_00 ";
	}

	public void setPreferredLetters(int[] frequencies) {
		mPrefLetterFrequencies = frequencies;
		mPrefLetter = 0;
	}	

	/**
	 * Does the magic of locking the touch gesture into the spacebar when
	 * switching input languages.
	 */
	boolean isInside(Key key, int x, int y) {
		final int code = key.codes[0];
		if (key.disabled) {
			return false;
		}
		if (code == KEYCODE_SHIFT ||
				code == KEYCODE_DELETE) {
			y -= key.height / 10;
			if (code == KEYCODE_SHIFT) x += key.width / 8;
			if (code == KEYCODE_DELETE) x -= key.width / 8;
		} else if (code == KEYCODE_SPACE) {
			y += mSpacebarVerticalCorrection;
		} else if (mPrefLetterFrequencies != null) {
			// New coordinate? Reset
			if (mPrefLetterX != x || mPrefLetterY != y) {
				mPrefLetter = 0;
				mPrefDistance = Integer.MAX_VALUE;
			}
			// Handle preferred next letter
			final int[] pref = mPrefLetterFrequencies;
			final int prefLength = pref.length;
			if (mPrefLetter > 0) {
				return mPrefLetter == code;
			} else {
				final boolean inside = key.isInsideSuper(x, y);
				final int[] nearby = getNearestKeys(x, y);
				final Key[] nearbyKeys = mKeyArray;
				if (inside) {
					// If it's a preferred letter
					//if (inPrefList(code, pref)) {
					if (code < prefLength && code >= 0 && pref[code] > 0) {
						// Check if its frequency is much lower than a nearby key
						mPrefLetter = code;
						mPrefLetterX = x;
						mPrefLetterY = y;
						for (int i = 0; i < nearby.length; i++) {
							final Key k = nearbyKeys[nearby[i]];
							if (k != key) {
								// && inPrefList(k.codes[0], pref)) {
								final int code0 = k.codes[0];
								if (code0 < prefLength && code0 >= 0 && pref[code0] > 0) {
									final int dist = distanceFrom(k, x, y);
									if (dist < (int) (k.width * OVERLAP_PERCENTAGE_LOW_PROB) &&
											(pref[code0] > pref[mPrefLetter] * 3))  {
										mPrefLetter = code0;
										mPrefDistance = dist;
										// Log.d(TAG, "CORRECTED ALTHOUGH PREFERRED !!!!!!");
										break;
									}
								}
							}
						}

						return mPrefLetter == code;
					}
				}

				// Get the surrounding keys and intersect with the preferred list
				// For all in the intersection
				//   if distance from touch point is within a reasonable distance
				//       make this the pref letter
				// If no pref letter
				//   return inside;
				// else return thiskey == prefletter;

				for (int i = 0; i < nearby.length; i++) {
					final Key k = nearbyKeys[nearby[i]];
					// if (inPrefList(k.codes[0], pref)) {
					final int code0 = k.codes[0];
					if (code0 < prefLength && code0 >= 0 && pref[code0] > 0) {
						final int dist = distanceFrom(k, x, y);
						if (dist < (int) (k.width * OVERLAP_PERCENTAGE_HIGH_PROB)
								&& dist < mPrefDistance)  {
							mPrefLetter = code0;
							mPrefLetterX = x;
							mPrefLetterY = y;
							mPrefDistance = dist;
						}
					}
				}
				// Didn't find any
				if (mPrefLetter == 0) {
					return inside;
				} else {
					return mPrefLetter == code;
				}
			}
		}

		return key.isInsideSuper(x, y);
	}

	/*
	static private boolean inPrefList(int code, int[] pref) {
		if (code < pref.length && code >= 0) return pref[code] > 0;
		return false;
	}*/
	// (code < pref.length && code >= 0 && pref[code] > 0)

	static private int distanceFrom(Key k, int x, int y) {
		if (y > k.y && y < k.y + k.height) {
			return Math.abs(k.x + k.width / 2 - x);
		} else {
			return Integer.MAX_VALUE;
		}
	}


	static private void skipToEndOfRow(XmlResourceParser parser) throws XmlPullParserException, IOException
	{
		int event;
		while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
			if (event == XmlResourceParser.END_TAG 
					&& parser.getName().equals(TAG_ROW)) {
				break;
			}
		}
	}


}
