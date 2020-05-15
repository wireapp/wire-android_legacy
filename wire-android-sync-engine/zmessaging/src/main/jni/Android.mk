TOP_DIR := $(call my-dir)
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE       := lzw-decoder
LOCAL_SRC_FILES    := LzwDecoder.cpp
LOCAL_LDLIBS       := -llog
LOCAL_CFLAGS       := -O2 -Wall -pedantic -Wno-variadic-macros -fstack-protector-all
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := randombytes
LOCAL_SRC_FILES := randombytes.c
LOCAL_CFLAGS    := -std=c99 -O2 -Wall -pedantic -Wno-variadic-macros -lsodium -fstack-protector-all
LOCAL_LDLIBS    += $(LOCAL_PATH)/$(TARGET_ARCH_ABI)/libsodium.so
include $(BUILD_SHARED_LIBRARY)
