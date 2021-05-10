LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := pinyin_smartkbd

LOCAL_SRC_FILES := \
	 share/dictbuilder.cpp \
	 share/dictlist.cpp \
	 share/dicttrie.cpp \
	 share/lpicache.cpp \
	 share/matrixsearch.cpp \
	 share/mystdlib.cpp \
	 share/ngram.cpp \
	 share/pinyinime.cpp \
	 share/searchutility.cpp \
	 share/spellingtable.cpp \
	 share/spellingtrie.cpp \
	 share/splparser.cpp \
	 share/utf16char.cpp \
	 share/utf16reader.cpp \
	 android/com_android_inputmethod_pinyin_PinyinDecoderService.cpp

include $(BUILD_SHARED_LIBRARY)
