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

import android.os.IBinder;

import com.dexilog.smartkeyboard.keyboard.Keyboard;

public interface KeyboardView {
    void setKeyboard(Keyboard mCurKeyboard);

    void setCompactLayout(boolean b);

    void setLangPopup(Keyboard mLangPopup);

    void setDisplayAlt(boolean displayAlt);

    void setPopupKeyboardDisabled(boolean disablePopupKeyboard);

    boolean isT9PredictionOn();

    void closing();

    Keyboard getKeyboard();

    boolean isShifted();

    boolean isShown();

    IBinder getWindowToken();

    void resetKeyState();

    void setPreviewEnabled(boolean mShowPreview);

    void applySkin(SkinLoader.SkinInfo skin);

    void setTransparency(int mOpacity, int bgOpacity, int keyOpacity, int hKeyOpacity);

    void setAlwaysCaps(boolean mAlwaysCaps);

    void setShowTouchpoints(boolean mShowTouchPoints);

    void setSlidePopup(boolean mSlidePopup);

    void setSpacePreview(boolean mSpacePreview);

    void setLongpressDuration(int mLongpressDuration);

    void setMultitapInterval(int mMultitapInterval);

    void setSwipeFactor(int mSwipeFactor);

    void setNoAltPreview(boolean mNoAltPreview);

    void disableMT(boolean mDisableMT);

    void setCalibration(CalibrationInfo mCalibration);

    void setAccentsPriority(boolean mAccentsPriority);

    void setPadding(int i, int i1, int i2, int padding);

    void setVisibility(int i);

    void setProximityCorrectionEnabled(boolean b);

    boolean handleBack();

    boolean setShifted(boolean shifted, boolean forceDraw);

    void setCustomSmileys(boolean customSmileys);
}
