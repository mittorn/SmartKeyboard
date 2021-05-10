LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := wnn_smartkbd


LOCAL_SRC_FILES := OpenWnnDictionaryImplJni.c \
	engine/ndapi.c \
	engine/ndbdic.c \
	engine/ndcommon.c \
	engine/ndfdic.c \
	engine/ndldic.c \
	engine/ndrdic.c \
	engine/neapi.c \
	engine/necode.c \
	engine/nj_str.c

include $(BUILD_SHARED_LIBRARY)

