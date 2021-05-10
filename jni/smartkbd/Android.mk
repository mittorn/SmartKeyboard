LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := smartkbddict

LOCAL_SRC_FILES := \
	com_dexilog_smartkeyboard_BinaryDictionary.cpp \
	dictionary.cpp \
	expandable_dic.cpp

include $(BUILD_SHARED_LIBRARY)
