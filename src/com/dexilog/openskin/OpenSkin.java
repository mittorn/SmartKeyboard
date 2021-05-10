/*
 * Copyright (C) 2010 Cyril Deguet
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

package com.dexilog.openskin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

public class OpenSkin {

	private static final String TAG = "OpenSkin";
	private static final String FONT_CACHE = "font.ttf";
	static final int BUFFER_SIZE = 2048;

	private static final int[] NORMAL_STATES = 
	{ -android.R.attr.state_checkable, -android.R.attr.state_pressed };
	private static final int[] PRESSED_STATES = 
	{ -android.R.attr.state_checkable, android.R.attr.state_pressed };
	private static final int[] NORMAL_OFF_STATES = 
	{ android.R.attr.state_checkable, -android.R.attr.state_checked, -android.R.attr.state_pressed };
	private static final int[] PRESSED_OFF_STATES = 
	{ android.R.attr.state_checkable, -android.R.attr.state_checked, android.R.attr.state_pressed };
	private static final int[] NORMAL_ON_STATES = 
	{ android.R.attr.state_checkable, android.R.attr.state_checked, -android.R.attr.state_pressed };
	private static final int[] PRESSED_ON_STATES = 
	{ android.R.attr.state_checkable, android.R.attr.state_checked, android.R.attr.state_pressed };

	Context mContext;
	Method mCreateFromResources = null;
	int mDensityDpi = 160;
	Object[] mArgs;
	TypedValue mValue = new TypedValue();
	boolean mIsValid = false;
	ZipFile mZipFile;
	Drawable mBackground;
	Drawable mKeyBackground;
	Drawable mModKeyBackground;
	Drawable mDeleteKey;
	Drawable mReturnKey;
	Drawable mSearchKey;
	Drawable mSpaceKey;
	Drawable mShiftKey;
	Drawable mShiftLockedKey;
	Drawable mMicKey;
	int mLabelColor;
	int mAltLabelColor;
	int mModLabelColor;
	boolean mBoldLabel;
	Integer mShadowColor;
	Integer mAltShadowColor;
	Integer mModShadowColor;
	Drawable mCandidatesBackground;
	Drawable mCandidatesDivider;
	Drawable mCandidateHighlightBackground;
	Integer mCandidatesNormalColor;
	Integer mCandidatesRecommendedColor;
	Integer mCandidatesOtherColor;
	Integer mCandidatesHighlightColor;
	Typeface mLabelFont;

	public OpenSkin(Context context, String path) {
		mContext = context;

		// Use reflection to scale images on Android > 1.5
		try {
			mCreateFromResources = Drawable.class.getDeclaredMethod("createFromResourceStream", 
					new Class[] { android.content.res.Resources.class, TypedValue.class, 
					InputStream.class, String.class });
			mArgs = new Object[] { context.getResources(), mValue, null, null };
			
			DisplayMetrics metrics = context.getResources().getDisplayMetrics();
			mDensityDpi = DisplayMetrics.class.getDeclaredField("densityDpi").getInt(metrics);
		} catch (Exception e) {
			Log.d(TAG, "Old API, using default resources");
		}

		load(path);
	}

	public boolean isValid() {
		return mIsValid;
	}
	
	public Drawable getBackground() {
		return mBackground;
	}

	public Drawable getKeyBackground() {
		return mKeyBackground;
	}
	
	public Drawable getSpecialKeyBackground() {
		return mModKeyBackground;
	}

	public Drawable getDeleteKey() {
		return mDeleteKey;
	}
	
	public Drawable getReturnKey() {
		return mReturnKey;
	}

	public Drawable getSearchKey() {
		return mSearchKey;
	}
	
	public Drawable getSpaceKey() {
		return mSpaceKey;
	}
	
	public Drawable getShiftKey() {
		return mShiftKey;
	}
	
	public Drawable getShiftLockedKey() {
		return mShiftLockedKey;
	}
	
	public Drawable getMicKey() {
		return mMicKey;
	}

	public int getLabelColor() {
		return mLabelColor;
	}
	
	public int getAltLabelColor() {
		return mAltLabelColor;
	}
	
	public int getModLabelColor() {
		return mModLabelColor;
	}
	
	public boolean getBoldLabel() {
		return mBoldLabel;
	}
	
	public Integer getShadowColor() {
		return mShadowColor;
	}
	
	public Integer getAltShadowColor() {
		return mAltShadowColor;
	}
	
	public Integer getModShadowColor() {
		return mModShadowColor;
	}

	public Drawable getCandidatesBackground() {
		return mCandidatesBackground;
	}
	
	public Drawable getCandidatesDivider() {
		return mCandidatesDivider;
	}
	
	public Drawable getCandidateHighlightBackground() {
		return mCandidateHighlightBackground;
	}
	
	public Integer getCandidatesNormalColor() {
		return mCandidatesNormalColor;
	}
	
	public Integer getCandidatesRecommendedColor() {
		return mCandidatesRecommendedColor;
	}

	public Integer getCandidatesOtherColor() {
		return mCandidatesOtherColor;
	}

	public Integer getCandidatesHighlightColor() {
		return mCandidatesHighlightColor;
	}
	
	public Typeface getLabelFont() {
		return mLabelFont;
	}
	
	static public String getSkinName(File file) {
		ZipFile zipFile;
		try {
			zipFile = new ZipFile(file);
			// Parse the skin description file
			ZipEntry entry = zipFile.getEntry("skin.xml");
			InputStream is = zipFile.getInputStream(entry);
			DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
			Document dom = domBuilder.parse(is);
			Element root = dom.getDocumentElement();
			if (root.getNodeName().equals("skin")) {
				return root.getAttribute("name");
			} else {
				return null;
			}
		} catch (Exception e) {
			return null;
		}
	}

	private void load(String path) {
		File file = new File(path);
		try {
			mZipFile = new ZipFile(file);

			// Parse the skin description file
			ZipEntry entry = mZipFile.getEntry("skin.xml");
			InputStream is = mZipFile.getInputStream(entry);
			DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
			Document dom = domBuilder.parse(is);
			Element root = dom.getDocumentElement();
			mBackground = loadBackground(root, "background");
			mKeyBackground = loadKeyBackground(root, "key-background");
			mModKeyBackground = loadKeyBackground(root, "mod-key-background");
			Element symbols = getChild(root, "symbols");
			mDeleteKey = loadImage(symbols, "delete");
			mReturnKey = loadImage(symbols, "return");
			mSearchKey = loadImage(symbols, "search");
			mSpaceKey = loadImage(symbols, "space");
			mShiftKey = loadImage(symbols, "shift");
			mShiftLockedKey = loadImage(symbols, "shift-locked");
			mMicKey = loadImage(symbols, "mic");
			Element colors = getChild(root, "colors");
			mLabelColor = loadColor(colors, "label");
			mBoldLabel = getBoolAttr(colors, "label", "bold");
			mAltLabelColor = loadColor(colors, "alt-label");
			mModLabelColor = loadColor(colors, "mod-label");
			mShadowColor = loadColor(colors, "shadow");
			mAltShadowColor = loadColor(colors, "alt-shadow");
			mModShadowColor = loadColor(colors, "mod-shadow");
			Element candidates = getChild(root, "candidates");
			if (candidates != null) {
				mCandidatesBackground = loadBackground(candidates, "background");
				mCandidatesDivider = loadImage(candidates, "divider");
				mCandidateHighlightBackground = loadBackground(candidates, "highlight-background");
				colors = getChild(candidates, "colors");
				mCandidatesNormalColor = loadColor(colors, "normal");
				mCandidatesRecommendedColor = loadColor(colors, "recommended");
				mCandidatesOtherColor = loadColor(colors, "other");
				mCandidatesHighlightColor = loadColor(colors, "highlight");
			}
			Element fonts = getChild(root, "fonts");
			if (fonts != null) {
				mLabelFont = loadFont(fonts, "label");
			}
			mIsValid = true;

		} catch (Throwable e) {
			// Something bad happened, isValid() will return false
			e.printStackTrace();
		}

		try {
			mZipFile.close(); // should be closed
		} catch( IOException e ) {
			e.printStackTrace();
		}
	}

	private Drawable loadBackground(Element root, String nodeName) throws IOException {
		Element element = getChild(root, nodeName);
		if (element == null) {
			return null;
		}
		// Check if an image is present
		NodeList imageList = element.getElementsByTagName("image");
		if (imageList.getLength() > 0) {
			String file = ((Element)imageList.item(0)).getFirstChild().getNodeValue();
			return loadDrawable(file);
		} else {
			// Otherwise, create a color gradient
			int[] backgroundColor = {0, 0};
			Element topElem = getChild(element,"color-top");
			backgroundColor[0] = Color.parseColor(topElem.getFirstChild().getNodeValue());
			Element bottomElem = getChild(element, "color-bottom");
			backgroundColor[1] = Color.parseColor(bottomElem.getFirstChild().getNodeValue());
			return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, backgroundColor);
		}
	}
	
	private Drawable loadKeyBackground(Element root, String nodeName) throws IOException {
		Element element = getChild(root, nodeName);
		StateListDrawable drawable = new StateListDrawable();
		addState(drawable, element, "normal", NORMAL_STATES);
		addState(drawable, element, "pressed", PRESSED_STATES);
		addState(drawable, element, "normal-off", NORMAL_OFF_STATES);
		addState(drawable, element, "pressed-off", PRESSED_OFF_STATES);
		addState(drawable, element, "normal-on", NORMAL_ON_STATES);
		addState(drawable, element, "pressed-on", PRESSED_ON_STATES);
		return drawable;
	}
	
	private Drawable loadImage(Element root, String nodeName) throws IOException {
		Element element = getChild(root, nodeName);
		String file = element.getFirstChild().getNodeValue();
		return loadDrawable(file);
	}
	
	private Typeface loadFont(Element root, String nodeName) throws IOException {
		Element element = getChild(root, nodeName);
		if (element != null) {
			String file = element.getFirstChild().getNodeValue();
			// First, extract the font to internal storage
			ZipEntry entry = mZipFile.getEntry("font/" + file);
			if (entry == null) { 
				Log.e(TAG, "Cannot find font " + file);
				return null;
			}
			InputStream is = mZipFile.getInputStream(entry);
			try {
				FileOutputStream fos = mContext.openFileOutput(FONT_CACHE, Context.MODE_PRIVATE);
				byte buffer[] = new byte[BUFFER_SIZE];
				// Copy
				int bytesIn = 0;
				while ((bytesIn = is.read(buffer)) != -1) { 
					fos.write(buffer, 0, bytesIn); 
				}
				fos.close();
				Log.d(TAG, "Font " + file + " copied to internal storage");
				File fontFile = mContext.getFileStreamPath(FONT_CACHE);
				return Typeface.createFromFile(fontFile);
			} catch (Exception e) {
				Log.e(TAG, "Failed to copy " + file + " to internal storage");
				e.printStackTrace();
				return null;
			}
		} else {
			return null;
		}
	}
	
	private Integer loadColor(Element root, String nodeName) {
		Element elem = getChild(root, nodeName);
		if (elem != null) {
			return Color.parseColor(elem.getFirstChild().getNodeValue());
		} else {
			return null;
		}
	}
	
	private boolean getBoolAttr(Element root, String nodeName, String attribute) {
		Element elem = getChild(root, nodeName);
		if (elem != null && elem.hasAttribute(attribute)) {
			return elem.getAttribute(attribute).equals("true");
		} else {
			return false;
		}
	}
	
	private Element getChild(Element elem, String name) {
		return (Element)elem.getElementsByTagName(name).item(0);
	}
	
	private void addState(StateListDrawable drawable, Element key, 
			String name, int[] states) throws IOException {
		NodeList list = key.getElementsByTagName(name);
		if (list.getLength() > 0) {
			Element element = (Element)list.item(0);
			String file = element.getFirstChild().getNodeValue();
			drawable.addState(states, loadDrawable(file));
		}
	}
	
	private Drawable loadDrawable(String file) throws IOException {
		int imageDensity = 160;
		ZipEntry entry = null;
		if (mDensityDpi >= 240) {
			// Try to load hdpi image
			entry = mZipFile.getEntry("drawable-hdpi/" + file);
			if (entry != null) { 
				imageDensity = 240;
			}
		}
		if (entry == null) {
			// Load mdpi (default) image
			entry = mZipFile.getEntry("drawable/" + file);	
		}
		InputStream is = mZipFile.getInputStream(entry);
		if (file.endsWith(".9.png")) {
			// Decode NinePatch image
	        return NinePatchUtilities.decodeNinePatchDrawable(is);
		} else {
			// Otherwise normal image
			return createDrawable(is, imageDensity);	
		}
	}

	private Drawable createDrawable(InputStream is, int density) {
		if (mCreateFromResources != null) {
			mValue.density = density;
			mArgs[2] = is;
			try {
				return (Drawable)mCreateFromResources.invoke(null, mArgs);
			} catch (InvocationTargetException e) {
				Log.d(TAG, "Exception caught, try again without resources");
				mCreateFromResources = null;
				return Drawable.createFromStream(is, null);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} else {
			return Drawable.createFromStream(is, null);
		}
	}

}
