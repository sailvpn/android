package main

#include <jni.h>
#include <stdlib.h>

// You also need these helper wrappers because Go cannot
// call C function pointers (like env->GetStringUTFChars) directly.
static const char* get_string(JNIEnv *env, jstring str) {
    return (*env)->GetStringUTFChars(env, str, 0);
}

static void release_string(JNIEnv *env, jstring str, const char *chars) {
    (*env)->ReleaseStringUTFChars(env, str, chars);
}

import "C"

fun mina()

//export Java_com_illiad_troad_service_NativeEngine_startTun2Socks
func Java_com_illiad_troad_service_NativeEngine_startTun2Socks(
    env *C.JNIEnv,
    clazz C.jclass,
    fd C.jint,
    proxyAddr C.jstring,
    proxyPort C.jint,
    mtu C.jint,
    caCert C.jstring,
    header C.jstring,
    sni C.jstring,
) C.jint {
    // Your Go implementation here
    return 0
}

//export Java_com_illiad_troad_service_NativeEngine_stopTun2Socks
func Java_com_illiad_troad_service_NativeEngine_stopTun2Socks(env *C.JNIEnv, clazz C.jclass) {
    // Your Go stop logic here
}
